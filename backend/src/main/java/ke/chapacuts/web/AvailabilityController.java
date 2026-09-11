package ke.chapacuts.web;

import ke.chapacuts.booking.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvailabilityController {

    private final BookingService bookings;

    public AvailabilityController(BookingService bookings) {
        this.bookings = bookings;
    }

    /** Slots for one day, with the three bookable days alongside. No date means today. */
    @GetMapping("/api/availability")
    public BookingService.DayAvailability availability(@RequestParam(required = false) String date) {
        return bookings.availability(date);
    }
}
