package ke.chapacuts.booking;

public enum BookingStatus {
    /** Created, no payment attempted yet. The slot is not held. */
    NEW,
    /** A payment was initiated. The slot is held for three minutes. */
    AWAITING_PAYMENT,
    /** The provider said the money moved. The slot is booked. */
    CONFIRMED,
    /** The provider said the payment didn't happen. The slot is released. */
    FAILED,
    /** Three minutes passed with no answer. The slot is released. */
    EXPIRED;

    public boolean holdsSlot() {
        return this == AWAITING_PAYMENT || this == CONFIRMED;
    }
}
