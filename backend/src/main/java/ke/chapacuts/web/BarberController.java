package ke.chapacuts.web;

import java.util.List;
import ke.chapacuts.booking.Booking;
import ke.chapacuts.booking.BookingService;
import ke.chapacuts.booking.BookingStatus;
import ke.chapacuts.catalog.CutService;
import ke.chapacuts.catalog.ServiceCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The shop's day view. No auth here; in production this sits behind a login. */
@RestController
public class BarberController {

    public record Entry(
            String time,
            boolean seeded,
            String ref,
            String customerName,
            String phone,
            String serviceId,
            String serviceName,
            Integer price,
            BookingStatus status,
            String note,
            String imageId) {
    }

    public record DayView(String date, List<String> days, List<Entry> bookings) {
    }

    private final BookingService bookings;
    private final ServiceCatalog catalog;

    public BarberController(BookingService bookings, ServiceCatalog catalog) {
        this.bookings = bookings;
        this.catalog = catalog;
    }

    @GetMapping("/api/barber/bookings")
    public DayView day(@RequestParam(required = false) String date) {
        BookingService.DayAvailability day = bookings.availability(date);
        List<Entry> entries = bookings.forDay(day.date()).stream().map(this::entry).toList();
        return new DayView(day.date(), day.days(), entries);
    }

    private Entry entry(BookingService.DayEntry e) {
        if (e.seeded()) {
            return new Entry(e.time(), true, null, null, null, null, null, null, null, null, null);
        }
        Booking b = e.booking();
        CutService s = catalog.find(b.serviceId()).orElseThrow();
        return new Entry(e.time(), false, b.ref(), b.customerName(), b.phone(), s.id(), s.name(), s.price(),
                b.status(), b.note(), b.imageId());
    }
}
