package ke.chapacuts.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One customer, one slot. Mutable state is only touched inside {@link BookingService}'s lock;
 * read it through the service or through a {@link BookingResponse} snapshot.
 */
public class Booking {

    private final String ref;
    private final String serviceId;
    private final LocalDate date;
    private final LocalTime slotTime;
    private final String customerName;
    private final String note;
    private final String imageId;
    private final Instant createdAt;

    private String phone;
    private volatile BookingStatus status = BookingStatus.NEW; // read outside the lock by the poll
    private PaymentMethod paymentMethod;
    private Instant holdStartedAt;
    private Instant lastProviderCheckAt;
    private String checkoutRequestId;
    private String checkoutUrl;
    private String receipt;
    private String failureMessage;

    Booking(String ref, String serviceId, LocalDate date, LocalTime slotTime, String customerName, String phone,
            String note, String imageId) {
        this.ref = ref;
        this.serviceId = serviceId;
        this.date = date;
        this.slotTime = slotTime;
        this.customerName = customerName;
        this.phone = phone;
        this.note = note;
        this.imageId = imageId;
        this.createdAt = Instant.now();
    }

    public String ref() { return ref; }
    public String serviceId() { return serviceId; }
    public LocalDate date() { return date; }
    public LocalTime slotTime() { return slotTime; }
    public String customerName() { return customerName; }
    public String note() { return note; }
    public String imageId() { return imageId; }
    public Instant createdAt() { return createdAt; }

    public String phone() { return phone; }
    public BookingStatus status() { return status; }
    public PaymentMethod paymentMethod() { return paymentMethod; }
    public Instant holdStartedAt() { return holdStartedAt; }
    public Instant lastProviderCheckAt() { return lastProviderCheckAt; }
    public String checkoutRequestId() { return checkoutRequestId; }
    public String checkoutUrl() { return checkoutUrl; }
    public String receipt() { return receipt; }
    public String failureMessage() { return failureMessage; }

    void phone(String phone) { this.phone = phone; }
    void status(BookingStatus status) { this.status = status; }
    void paymentMethod(PaymentMethod method) { this.paymentMethod = method; }
    void holdStartedAt(Instant at) { this.holdStartedAt = at; }
    void lastProviderCheckAt(Instant at) { this.lastProviderCheckAt = at; }
    void checkoutRequestId(String id) { this.checkoutRequestId = id; }
    void checkoutUrl(String url) { this.checkoutUrl = url; }
    void receipt(String receipt) { this.receipt = receipt; }
    void failureMessage(String message) { this.failureMessage = message; }
}
