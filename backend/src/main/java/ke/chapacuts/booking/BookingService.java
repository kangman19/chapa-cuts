package ke.chapacuts.booking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ke.chapacuts.api.CustomerFacingException;
import ke.chapacuts.api.NotFoundException;
import ke.chapacuts.catalog.CutService;
import ke.chapacuts.catalog.ServiceCatalog;
import ke.chapacuts.upload.UploadStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Owns every booking and every slot. All state changes go through {@link #lock} so two requests can't grab
 * the same slot, and so confirm/fail/expire never race each other.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    public static final Duration HOLD = Duration.ofMinutes(3);
    public static final String EXPIRED_MESSAGE =
            "We waited three minutes and didn't hear back, so the slot was released. Nothing was charged.";

    private final Object lock = new Object();
    private final Map<String, Booking> byRef = new ConcurrentHashMap<>();
    private final Map<String, String> refByCheckoutId = new ConcurrentHashMap<>();

    private final SlotSchedule schedule;
    private final ServiceCatalog catalog;
    private final UploadStore uploads;

    public BookingService(SlotSchedule schedule, ServiceCatalog catalog, UploadStore uploads) {
        this.schedule = schedule;
        this.catalog = catalog;
        this.uploads = uploads;
    }

    // ---- Availability -------------------------------------------------------------------------------------

    public record SlotAvailability(String time, boolean available) {
    }

    public record DayAvailability(String date, List<String> days, List<SlotAvailability> slots) {
    }

    public DayAvailability availability(String rawDate) {
        LocalDate date = rawDate == null || rawDate.isBlank() ? schedule.days().get(0) : parseDay(rawDate);
        List<SlotAvailability> slots = new ArrayList<>();
        synchronized (lock) {
            for (LocalTime time : schedule.times()) {
                boolean available = !schedule.isPast(date, time) && !slotTaken(date, time);
                slots.add(new SlotAvailability(time.toString(), available));
            }
        }
        return new DayAvailability(date.toString(), schedule.days().stream().map(LocalDate::toString).toList(), slots);
    }

    /** Must be called with {@link #lock} held. */
    private boolean slotTaken(LocalDate date, LocalTime time) {
        if (schedule.isSeeded(date, time)) {
            return true;
        }
        for (Booking b : byRef.values()) {
            if (b.status().holdsSlot() && b.date().equals(date) && b.slotTime().equals(time)) {
                return true;
            }
        }
        return false;
    }

    // ---- Creation -----------------------------------------------------------------------------------------

    public Booking create(CreateBookingRequest req) {
        CutService service = catalog.find(req.serviceId())
                .orElseThrow(() -> new CustomerFacingException("Pick a cut from the list."));
        LocalDate date = parseDay(req.date());
        LocalTime time = parseTime(req.slotTime());
        String phone = PhoneNumbers.normalise(req.phone());
        String note = req.note() == null ? null : req.note().trim();
        String imageId = req.imageId() == null || req.imageId().isBlank() ? null : req.imageId().trim();
        if (imageId != null && !uploads.exists(imageId)) {
            throw new CustomerFacingException("That photo didn't upload properly. Please add it again.");
        }
        if (!service.id().equals(ServiceCatalog.OTHER_ID)) {
            note = null;
            imageId = null;
        }

        synchronized (lock) {
            if (schedule.isPast(date, time) || slotTaken(date, time)) {
                throw new CustomerFacingException("That time was just taken. Please pick another slot.");
            }
            String ref;
            do {
                ref = References.next();
            } while (byRef.containsKey(ref));
            Booking booking = new Booking(ref, service.id(), date, time, req.customerName().trim(), phone,
                    note == null || note.isEmpty() ? null : note, imageId);
            byRef.put(ref, booking);
            return booking;
        }
    }

    // ---- Holding and settling -----------------------------------------------------------------------------

    /** Moves a NEW booking to AWAITING_PAYMENT and holds the slot. Fails if the slot went in the meantime. */
    public Booking startHold(String ref, PaymentMethod method, String phone) {
        Booking b = get(ref);
        synchronized (lock) {
            if (b.status() != BookingStatus.NEW) {
                throw new CustomerFacingException(switch (b.status()) {
                    case AWAITING_PAYMENT -> "A payment is already in progress for this booking.";
                    case CONFIRMED -> "This booking is already confirmed.";
                    default -> "This booking has ended. Please start a new one.";
                });
            }
            if (schedule.isPast(b.date(), b.slotTime()) || slotTaken(b.date(), b.slotTime())) {
                throw new CustomerFacingException("That time was just taken. Please pick another slot.");
            }
            b.status(BookingStatus.AWAITING_PAYMENT);
            b.paymentMethod(method);
            b.holdStartedAt(Instant.now());
            if (phone != null) {
                b.phone(phone);
            }
            return b;
        }
    }

    public void attachCheckout(Booking b, String checkoutRequestId, String checkoutUrl, String accessCode) {
        synchronized (lock) {
            b.checkoutRequestId(checkoutRequestId);
            b.checkoutUrl(checkoutUrl);
            b.checkoutAccessCode(accessCode);
            if (checkoutRequestId != null) {
                refByCheckoutId.put(checkoutRequestId, b.ref());
            }
        }
    }

    public void markProviderChecked(Booking b) {
        synchronized (lock) {
            b.lastProviderCheckAt(Instant.now());
        }
    }

    /**
     * The provider said the money moved. Idempotent: the callback and the status poll will both call this
     * for the same booking, and the second call is a no-op.
     */
    public void confirm(String ref, String receipt) {
        Booking b = get(ref);
        synchronized (lock) {
            switch (b.status()) {
                case CONFIRMED -> {
                    return;
                }
                case AWAITING_PAYMENT -> settleConfirmed(b, receipt);
                case EXPIRED -> {
                    // Money moved after we gave up. Honour it if nobody else took the slot.
                    if (!slotTaken(b.date(), b.slotTime())) {
                        settleConfirmed(b, receipt);
                    } else {
                        log.warn("Booking {} paid ({}) after expiry but slot is gone; needs manual follow-up",
                                ref, receipt);
                    }
                }
                default -> log.warn("Ignoring confirm for booking {} in state {}", ref, b.status());
            }
        }
    }

    private void settleConfirmed(Booking b, String receipt) {
        b.status(BookingStatus.CONFIRMED);
        b.receipt(receipt);
        b.failureMessage(null);
        log.info("Booking {} confirmed, receipt {}", b.ref(), receipt);
    }

    /** The provider said it didn't happen. Releases the slot. Only applies while awaiting payment. */
    public void fail(String ref, String message) {
        Booking b = get(ref);
        synchronized (lock) {
            if (b.status() != BookingStatus.AWAITING_PAYMENT) {
                return;
            }
            b.status(BookingStatus.FAILED);
            b.failureMessage(message);
            log.info("Booking {} failed: {}", ref, message);
        }
    }

    /** Expires the hold if it has been waiting longer than {@link #HOLD}. Returns true if it expired now. */
    public boolean expireIfStale(Booking b) {
        synchronized (lock) {
            if (b.status() != BookingStatus.AWAITING_PAYMENT || b.holdStartedAt() == null) {
                return false;
            }
            if (Instant.now().isBefore(b.holdStartedAt().plus(HOLD))) {
                return false;
            }
            b.status(BookingStatus.EXPIRED);
            b.failureMessage(EXPIRED_MESSAGE);
            log.info("Booking {} expired", b.ref());
            return true;
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void sweep() {
        for (Booking b : byRef.values()) {
            expireIfStale(b);
        }
    }

    // ---- Lookup -------------------------------------------------------------------------------------------

    public Booking get(String ref) {
        Booking b = ref == null ? null : byRef.get(ref.trim().toUpperCase());
        if (b == null) {
            throw new NotFoundException("We couldn't find a booking with that reference.");
        }
        return b;
    }

    public Optional<Booking> findByCheckoutId(String checkoutRequestId) {
        if (checkoutRequestId == null) {
            return Optional.empty();
        }
        String ref = refByCheckoutId.get(checkoutRequestId);
        return ref == null ? Optional.empty() : Optional.ofNullable(byRef.get(ref));
    }

    /** Bookings that hold a slot on the given day, earliest first. Seeded slots are reported too. */
    public record DayEntry(String time, boolean seeded, Booking booking) {
    }

    public List<DayEntry> forDay(String rawDate) {
        LocalDate date = rawDate == null || rawDate.isBlank() ? schedule.days().get(0) : parseDay(rawDate);
        List<DayEntry> entries = new ArrayList<>();
        synchronized (lock) {
            for (LocalTime time : schedule.times()) {
                if (schedule.isSeeded(date, time)) {
                    entries.add(new DayEntry(time.toString(), true, null));
                }
            }
            for (Booking b : byRef.values()) {
                if (b.status().holdsSlot() && b.date().equals(date)) {
                    entries.add(new DayEntry(b.slotTime().toString(), false, b));
                }
            }
        }
        entries.sort(Comparator.comparing(DayEntry::time));
        return entries;
    }

    // ---- Parsing ------------------------------------------------------------------------------------------

    private LocalDate parseDay(String raw) {
        LocalDate date;
        try {
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new CustomerFacingException("Pick a day from the list.");
        }
        if (!schedule.isDay(date)) {
            throw new CustomerFacingException("We only take bookings for the next three days.");
        }
        return date;
    }

    private LocalTime parseTime(String raw) {
        LocalTime time;
        try {
            time = LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new CustomerFacingException("Pick a time from the grid.");
        }
        if (!schedule.isTime(time)) {
            throw new CustomerFacingException("Pick a time from the grid.");
        }
        return time;
    }
}
