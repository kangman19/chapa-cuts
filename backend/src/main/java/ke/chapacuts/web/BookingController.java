package ke.chapacuts.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ke.chapacuts.booking.Booking;
import ke.chapacuts.booking.BookingResponse;
import ke.chapacuts.booking.BookingService;
import ke.chapacuts.booking.CreateBookingRequest;
import ke.chapacuts.catalog.ServiceCatalog;
import ke.chapacuts.payment.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    public record MpesaPayRequest(
            @NotBlank(message = "Enter the M-Pesa number to send the prompt to.") String phone) {
    }

    /** Email is optional; the in-page card form doesn't collect one. */
    public record CardPayRequest(@Email(message = "That email address doesn't look right.") String email) {
    }

    private final BookingService bookings;
    private final PaymentService payments;
    private final ServiceCatalog catalog;

    public BookingController(BookingService bookings, PaymentService payments, ServiceCatalog catalog) {
        this.bookings = bookings;
        this.payments = payments;
        this.catalog = catalog;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest req) {
        return view(bookings.create(req));
    }

    @PostMapping("/{ref}/pay/mpesa")
    public BookingResponse payMpesa(@PathVariable String ref, @Valid @RequestBody MpesaPayRequest req) {
        return view(payments.payWithMpesa(ref, req.phone()));
    }

    @PostMapping("/{ref}/pay/card")
    public BookingResponse payCard(@PathVariable String ref, @Valid @RequestBody(required = false) CardPayRequest req) {
        return view(payments.payWithCard(ref, req == null ? null : req.email()));
    }

    @GetMapping("/{ref}")
    public BookingResponse poll(@PathVariable String ref) {
        return view(payments.poll(ref));
    }

    // DELETE BEFORE PRODUCTION — demo escape hatch for when venue wifi fails.
    @PostMapping("/{ref}/simulate")
    public BookingResponse simulate(@PathVariable String ref) {
        return view(payments.simulate(ref));
    }

    private BookingResponse view(Booking b) {
        return BookingResponse.of(b, catalog.find(b.serviceId()).orElseThrow());
    }
}
