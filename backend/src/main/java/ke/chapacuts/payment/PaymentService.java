package ke.chapacuts.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import ke.chapacuts.api.CustomerFacingException;
import ke.chapacuts.booking.Booking;
import ke.chapacuts.booking.BookingService;
import ke.chapacuts.booking.BookingStatus;
import ke.chapacuts.booking.PaymentMethod;
import ke.chapacuts.booking.PhoneNumbers;
import ke.chapacuts.catalog.ServiceCatalog;
import ke.chapacuts.payment.mpesa.MpesaClient;
import ke.chapacuts.payment.paystack.PaystackClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Starts payments and settles them. A booking is confirmed here only when a provider reports the money moved,
 * whether that arrives by callback, by our status query, or by the card-return verify.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Don't bother the provider until the customer has had a moment to see the prompt. */
    private static final Duration ASK_PROVIDER_AFTER = Duration.ofSeconds(4);
    /** The browser polls every 2.5 s; the provider gets asked at most this often per booking. */
    private static final Duration MIN_GAP_BETWEEN_PROVIDER_CHECKS = Duration.ofSeconds(5);

    private final BookingService bookings;
    private final MpesaClient mpesa;
    private final PaystackClient paystack;
    private final boolean allowSimulate;

    public PaymentService(BookingService bookings, MpesaClient mpesa, PaystackClient paystack,
            @Value("${demo.allow-simulate:true}") boolean allowSimulate) {
        this.bookings = bookings;
        this.mpesa = mpesa;
        this.paystack = paystack;
        this.allowSimulate = allowSimulate;
    }

    // ---- Start --------------------------------------------------------------------------------------------

    public Booking payWithMpesa(String ref, String rawPhone) {
        String phone = PhoneNumbers.normalise(rawPhone);
        mpesa.requireConfigured();
        Booking b = bookings.startHold(ref, PaymentMethod.MPESA, phone);
        try {
            String checkoutId = mpesa.stkPush(phone, ServiceCatalog.DEPOSIT, ref);
            bookings.attachCheckout(b, checkoutId, null, null);
        } catch (CustomerFacingException e) {
            bookings.fail(ref, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("STK push for {} threw", ref, e);
            String message = "We couldn't reach M-Pesa just now. Nothing was charged. Please try again.";
            bookings.fail(ref, message);
            throw new CustomerFacingException(message);
        }
        return b;
    }

    public Booking payWithCard(String ref, String email) {
        paystack.requireConfigured();
        Booking b = bookings.startHold(ref, PaymentMethod.CARD, null);
        // Paystack needs an email on every transaction; the card form doesn't ask for one, so make one from the phone.
        String customerEmail = email == null || email.isBlank() ? b.phone() + "@chapacuts.co.ke" : email.trim();
        try {
            PaystackClient.Checkout checkout = paystack.initialize(customerEmail, ServiceCatalog.DEPOSIT, ref);
            bookings.attachCheckout(b, null, checkout.url(), checkout.accessCode());
        } catch (CustomerFacingException e) {
            bookings.fail(ref, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("Paystack initialize for {} threw", ref, e);
            String message = "We couldn't reach the card provider just now. Nothing was charged. Please try again.";
            bookings.fail(ref, message);
            throw new CustomerFacingException(message);
        }
        return b;
    }

    // ---- Poll ---------------------------------------------------------------------------------------------

    /** The browser's poll. If the hold is stale it expires; if it's old enough, ask the provider what happened. */
    public Booking poll(String ref) {
        Booking b = bookings.get(ref);
        if (b.status() != BookingStatus.AWAITING_PAYMENT) {
            return b;
        }
        if (bookings.expireIfStale(b)) {
            return b;
        }
        Instant now = Instant.now();
        boolean oldEnough = b.holdStartedAt() != null && now.isAfter(b.holdStartedAt().plus(ASK_PROVIDER_AFTER));
        boolean notTooSoon = b.lastProviderCheckAt() == null
                || now.isAfter(b.lastProviderCheckAt().plus(MIN_GAP_BETWEEN_PROVIDER_CHECKS));
        if (oldEnough && notTooSoon) {
            bookings.markProviderChecked(b);
            try {
                apply(b, askProvider(b));
            } catch (RuntimeException e) {
                log.warn("Provider check for {} threw; leaving pending", ref, e);
            }
        }
        return b;
    }

    private PaymentOutcome askProvider(Booking b) {
        if (b.paymentMethod() == PaymentMethod.MPESA) {
            if (b.checkoutRequestId() == null) {
                return PaymentOutcome.pending();
            }
            return mpesa.query(b.checkoutRequestId());
        }
        if (b.paymentMethod() == PaymentMethod.CARD) {
            return paystack.verify(b.ref());
        }
        return PaymentOutcome.pending();
    }

    private void apply(Booking b, PaymentOutcome outcome) {
        switch (outcome.kind()) {
            case PAID -> bookings.confirm(b.ref(), outcome.receipt());
            case FAILED -> bookings.fail(b.ref(), outcome.message());
            case PENDING -> { }
        }
    }

    // ---- Callback -----------------------------------------------------------------------------------------

    /** Daraja's callback. Matches on CheckoutRequestID only. Unknown or malformed bodies are logged and dropped. */
    public void handleMpesaCallback(JsonNode body) {
        JsonNode cb = body.path("Body").path("stkCallback");
        String checkoutId = cb.path("CheckoutRequestID").asText(null);
        if (checkoutId == null) {
            log.warn("M-Pesa callback without CheckoutRequestID: {}", body);
            return;
        }
        Booking b = bookings.findByCheckoutId(checkoutId).orElse(null);
        if (b == null) {
            log.warn("M-Pesa callback for unknown CheckoutRequestID {}", checkoutId);
            return;
        }
        String resultCode = cb.path("ResultCode").asText(null);
        String resultDesc = cb.path("ResultDesc").asText(null);
        String receipt = MpesaClient.receiptFrom(cb.path("CallbackMetadata"));
        log.info("M-Pesa callback for {}: ResultCode={} {}", b.ref(), resultCode, resultDesc);
        if (resultCode == null) {
            return;
        }
        apply(b, MpesaClient.outcomeFor(resultCode, resultDesc, receipt));
    }

    // ---- Demo ---------------------------------------------------------------------------------------------

    // DELETE BEFORE PRODUCTION — forces a pending booking to CONFIRMED with no provider involved.
    public Booking simulate(String ref) {
        if (!allowSimulate) {
            throw new CustomerFacingException("Simulated payments are switched off.");
        }
        Booking b = bookings.get(ref);
        if (b.status() != BookingStatus.AWAITING_PAYMENT) {
            throw new CustomerFacingException("Only a booking that's waiting for payment can be simulated.");
        }
        bookings.confirm(ref, "SIMULATED");
        return b;
    }
}
