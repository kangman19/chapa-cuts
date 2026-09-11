package ke.chapacuts.booking;

import ke.chapacuts.catalog.CutService;
import ke.chapacuts.catalog.ServiceCatalog;

/** What the browser sees. A snapshot, so the mutable Booking never leaks out of the service. */
public record BookingResponse(
        String ref,
        BookingStatus status,
        String serviceId,
        String serviceName,
        Integer price,
        int deposit,
        Integer balance,
        String date,
        String slotTime,
        String customerName,
        String phone,
        String note,
        String imageId,
        PaymentMethod paymentMethod,
        String checkoutUrl,
        String receipt,
        String message) {

    public static BookingResponse of(Booking b, CutService service) {
        Integer price = service.price();
        Integer balance = price == null ? null : Math.max(0, price - ServiceCatalog.DEPOSIT);
        return new BookingResponse(
                b.ref(),
                b.status(),
                service.id(),
                service.name(),
                price,
                ServiceCatalog.DEPOSIT,
                balance,
                b.date().toString(),
                b.slotTime().toString(),
                b.customerName(),
                b.phone(),
                b.note(),
                b.imageId(),
                b.paymentMethod(),
                b.status() == BookingStatus.AWAITING_PAYMENT ? b.checkoutUrl() : null,
                b.receipt(),
                b.failureMessage());
    }
}
