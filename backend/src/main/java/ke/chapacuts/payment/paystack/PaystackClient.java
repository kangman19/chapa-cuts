package ke.chapacuts.payment.paystack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ke.chapacuts.api.CustomerFacingException;
import ke.chapacuts.config.PaystackProperties;
import ke.chapacuts.payment.PaymentOutcome;
import ke.chapacuts.payment.ProviderHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Paystack test mode: initialize a hosted checkout, then verify by reference. The secret key never leaves here. */
@Component
public class PaystackClient {

    private static final Logger log = LoggerFactory.getLogger(PaystackClient.class);
    public static final String NOT_CONFIGURED =
            "Card payments aren't set up on this server yet. Ask the shop to add its Paystack key, or pay with M-Pesa.";

    private final PaystackProperties props;
    private final ProviderHttp http;

    public PaystackClient(PaystackProperties props, ProviderHttp http) {
        this.props = props;
        this.http = http;
    }

    public void requireConfigured() {
        if (!props.isConfigured()) {
            throw new CustomerFacingException(NOT_CONFIGURED);
        }
    }

    /** The hosted page URL and the access code the in-page card form (Paystack Popup) resumes with. */
    public record Checkout(String url, String accessCode) {
    }

    public Checkout initialize(String email, int amountKsh, String reference) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("amount", amountKsh * 100); // Paystack works in the smallest unit
        body.put("currency", props.currency());
        body.put("reference", reference);
        body.put("callback_url", props.returnUrl() + "?ref=" + reference);
        body.put("channels", List.of("card")); // this button is the card option; M-Pesa has its own

        ProviderHttp.Result res = http.postJson(
                props.baseUrl() + "/transaction/initialize",
                h -> h.setBearerAuth(props.secretKey()), body);

        String url = res.text("data", "authorization_url");
        String accessCode = res.text("data", "access_code");
        if (res.ok() && res.body().path("status").asBoolean(false) && url != null && accessCode != null) {
            return new Checkout(url, accessCode);
        }
        log.warn("Paystack initialize rejected: HTTP {} {}", res.status(), res.body());
        String message = res.text("message");
        throw new CustomerFacingException(message == null
                ? "The card provider didn't accept the request. Nothing was charged."
                : "The card provider said: " + message + ". Nothing was charged.");
    }

    /** Paid only when data.status is "success". Failed when it's "failed". Anything else is still pending. */
    public PaymentOutcome verify(String reference) {
        ProviderHttp.Result res = http.get(
                props.baseUrl() + "/transaction/verify/" + reference,
                h -> h.setBearerAuth(props.secretKey()));

        if (!res.ok() || !res.body().path("status").asBoolean(false)) {
            log.info("Paystack verify for {} not ready: HTTP {} {}", reference, res.status(), res.text("message"));
            return PaymentOutcome.pending();
        }
        String status = res.text("data", "status");
        if ("success".equals(status)) {
            String id = res.text("data", "id");
            return PaymentOutcome.paid(id == null ? "CARD" : "CARD-" + id);
        }
        if ("failed".equals(status)) {
            String reason = res.text("data", "gateway_response");
            return PaymentOutcome.failed(reason == null || reason.isBlank()
                    ? "Your card payment didn't go through."
                    : "Your card payment didn't go through: " + reason + ".");
        }
        return PaymentOutcome.pending();
    }
}
