package ke.chapacuts.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import ke.chapacuts.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Daraja posts here. Always answers 200, even to junk, or Safaricom keeps retrying. */
@RestController
public class MpesaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);
    private static final Map<String, Object> ACCEPTED = Map.of("ResultCode", 0, "ResultDesc", "Accepted");

    private final PaymentService payments;
    private final ObjectMapper mapper;

    public MpesaCallbackController(PaymentService payments, ObjectMapper mapper) {
        this.payments = payments;
        this.mapper = mapper;
    }

    @PostMapping("/api/mpesa/callback")
    public Map<String, Object> callback(@RequestBody(required = false) String raw) {
        try {
            JsonNode body = raw == null || raw.isBlank() ? mapper.createObjectNode() : mapper.readTree(raw);
            payments.handleMpesaCallback(body);
        } catch (Exception e) {
            log.warn("Could not process M-Pesa callback: {}", e.toString());
        }
        return ACCEPTED;
    }
}
