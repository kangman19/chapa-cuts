package ke.chapacuts.payment;

/** What a provider told us about a payment. Only {@link Kind#PAID} may confirm a booking. */
public record PaymentOutcome(Kind kind, String receipt, String message) {

    public enum Kind {
        PAID,
        FAILED,
        PENDING
    }

    public static PaymentOutcome paid(String receipt) {
        return new PaymentOutcome(Kind.PAID, receipt, null);
    }

    public static PaymentOutcome failed(String message) {
        return new PaymentOutcome(Kind.FAILED, null, message);
    }

    public static PaymentOutcome pending() {
        return new PaymentOutcome(Kind.PENDING, null, null);
    }
}
