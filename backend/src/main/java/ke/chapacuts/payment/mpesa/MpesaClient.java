package ke.chapacuts.payment.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import ke.chapacuts.api.CustomerFacingException;
import ke.chapacuts.booking.SlotSchedule;
import ke.chapacuts.config.MpesaProperties;
import ke.chapacuts.payment.PaymentOutcome;
import ke.chapacuts.payment.ProviderHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Daraja sandbox: OAuth token, STK push, and the STK status query. */
@Component
public class MpesaClient {

    private static final Logger log = LoggerFactory.getLogger(MpesaClient.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final String NOT_CONFIGURED =
            "M-Pesa isn't set up on this server yet. Ask the shop to add its Daraja keys, or pay by card.";

    private final MpesaProperties props;
    private final ProviderHttp http;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public MpesaClient(MpesaProperties props, ProviderHttp http) {
        this.props = props;
        this.http = http;
    }

    public void requireConfigured() {
        if (!props.isConfigured()) {
            throw new CustomerFacingException(NOT_CONFIGURED);
        }
    }

    // ---- Token --------------------------------------------------------------------------------------------

    /** Cached; refreshed a minute before Daraja says it expires. */
    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiresAt)) {
            return token;
        }
        String basic = Base64.getEncoder().encodeToString(
                (props.consumerKey() + ":" + props.consumerSecret()).getBytes(StandardCharsets.UTF_8));
        ProviderHttp.Result res = http.get(
                props.baseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
                h -> h.set("Authorization", "Basic " + basic));
        String accessToken = res.text("access_token");
        if (!res.ok() || accessToken == null) {
            log.warn("Daraja token request failed: HTTP {} {}", res.status(), res.body());
            throw new CustomerFacingException(
                    "M-Pesa didn't accept the shop's Daraja keys. Nothing was charged. Please try card, or try again later.");
        }
        long expiresIn = 3599;
        try {
            expiresIn = Long.parseLong(res.text("expires_in"));
        } catch (RuntimeException ignored) {
            // Daraja sends this as a string; fall back to the usual hour if it's odd.
        }
        token = accessToken;
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - 60));
        return token;
    }

    private String timestamp() {
        return ZonedDateTime.now(SlotSchedule.ZONE).format(TIMESTAMP);
    }

    private String password(String timestamp) {
        return Base64.getEncoder().encodeToString(
                (props.shortcode() + props.passkey() + timestamp).getBytes(StandardCharsets.UTF_8));
    }

    // ---- STK push -----------------------------------------------------------------------------------------

    /** Sends the prompt. Returns the CheckoutRequestID, the only reliable key for matching the callback. */
    public String stkPush(String phone, int amount, String accountReference) {
        String ts = timestamp();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", props.shortcode());
        body.put("Password", password(ts));
        body.put("Timestamp", ts);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", amount);
        body.put("PartyA", phone);
        body.put("PartyB", props.shortcode());
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", props.callbackUrl()); // capital B, or Daraja silently never calls back
        body.put("AccountReference", accountReference.length() > 12 ? accountReference.substring(0, 12) : accountReference);
        body.put("TransactionDesc", "Chapa Cuts deposit");

        String bearer = token();
        ProviderHttp.Result res = http.postJson(
                props.baseUrl() + "/mpesa/stkpush/v1/processrequest",
                h -> h.setBearerAuth(bearer), body);

        String responseCode = res.text("ResponseCode");
        String checkoutId = res.text("CheckoutRequestID");
        if (res.ok() && "0".equals(responseCode) && checkoutId != null) {
            log.info("STK push accepted for {} → {}", phone, checkoutId);
            return checkoutId;
        }
        log.warn("STK push rejected: HTTP {} {}", res.status(), res.body());
        String reason = firstNonNull(res.text("errorMessage"), res.text("ResponseDescription"));
        throw new CustomerFacingException(reason == null
                ? "M-Pesa didn't accept the request. Nothing was charged. Please try again."
                : "M-Pesa didn't accept the request: " + reason + ". Nothing was charged.");
    }

    // ---- Status query -------------------------------------------------------------------------------------

    /** Asks Daraja what happened to a push. Anything we can't read is treated as still pending. */
    public PaymentOutcome query(String checkoutRequestId) {
        String ts = timestamp();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", props.shortcode());
        body.put("Password", password(ts));
        body.put("Timestamp", ts);
        body.put("CheckoutRequestID", checkoutRequestId);

        String bearer;
        try {
            bearer = token();
        } catch (CustomerFacingException e) {
            return PaymentOutcome.pending();
        }
        ProviderHttp.Result res = http.postJson(
                props.baseUrl() + "/mpesa/stkpushquery/v1/query",
                h -> h.setBearerAuth(bearer), body);

        if (!res.ok()) {
            String errorCode = res.text("errorCode");
            if ("500.001.1001".equals(errorCode)) {
                return PaymentOutcome.pending(); // still being processed
            }
            log.warn("STK query for {} returned HTTP {} {}", checkoutRequestId, res.status(), res.body());
            return PaymentOutcome.pending();
        }
        String resultCode = res.text("ResultCode");
        String resultDesc = res.text("ResultDesc");
        log.info("STK query for {}: ResultCode={} {}", checkoutRequestId, resultCode, resultDesc);
        if (resultCode == null) {
            return PaymentOutcome.pending();
        }
        // Sandbox answers the query with a 200 and a "still under processing" result while the customer
        // hasn't acted yet. That's not a failure; keep waiting.
        if (resultDesc != null && resultDesc.toLowerCase().contains("processing")) {
            return PaymentOutcome.pending();
        }
        return outcomeFor(resultCode, resultDesc, null);
    }

    // ---- Shared result mapping ----------------------------------------------------------------------------

    /** Daraja result codes → what to tell the customer. Used by both the query and the callback. */
    public static PaymentOutcome outcomeFor(String resultCode, String resultDesc, String receipt) {
        int code;
        try {
            code = Integer.parseInt(resultCode.trim());
        } catch (RuntimeException e) {
            return PaymentOutcome.pending();
        }
        return switch (code) {
            case 0 -> PaymentOutcome.paid(receipt == null ? "M-PESA" : receipt);
            case 1032 -> PaymentOutcome.failed("You cancelled the payment on your phone.");
            case 1037 -> PaymentOutcome.failed("The M-Pesa prompt timed out before it was answered.");
            case 1 -> PaymentOutcome.failed("Your M-Pesa balance was too low for the KSh 200 deposit.");
            case 2001 -> PaymentOutcome.failed("The M-Pesa PIN entered was wrong.");
            default -> PaymentOutcome.failed(resultDesc == null || resultDesc.isBlank()
                    ? "M-Pesa couldn't complete the payment."
                    : "M-Pesa couldn't complete the payment: " + resultDesc);
        };
    }

    /** Pulls MpesaReceiptNumber out of CallbackMetadata.Item, if present. */
    public static String receiptFrom(JsonNode callbackMetadata) {
        for (JsonNode item : callbackMetadata.path("Item")) {
            if ("MpesaReceiptNumber".equals(item.path("Name").asText())) {
                return item.path("Value").asText();
            }
        }
        return null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
