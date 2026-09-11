package ke.chapacuts.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The shop's calendar: one chair, 30-minute slots from 09:00 to 18:30, over today, tomorrow and the day after.
 * Days are fixed when the app starts. A few slots are seeded as already booked so the grid doesn't look empty.
 */
@Component
public class SlotSchedule {

    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");
    private static final LocalTime FIRST = LocalTime.of(9, 0);
    private static final LocalTime LAST = LocalTime.of(18, 30);

    private final List<LocalDate> days;
    private final List<LocalTime> times;
    private final Set<String> seeded;

    public SlotSchedule() {
        LocalDate today = LocalDate.now(ZONE);
        this.days = List.of(today, today.plusDays(1), today.plusDays(2));

        List<LocalTime> t = new ArrayList<>();
        for (LocalTime time = FIRST; !time.isAfter(LAST); time = time.plusMinutes(30)) {
            t.add(time);
        }
        this.times = List.copyOf(t);

        this.seeded = Set.of(
                key(days.get(0), LocalTime.of(11, 0)),
                key(days.get(0), LocalTime.of(15, 30)),
                key(days.get(1), LocalTime.of(10, 0)),
                key(days.get(1), LocalTime.of(14, 0)),
                key(days.get(2), LocalTime.of(12, 30)));
    }

    public List<LocalDate> days() {
        return days;
    }

    public List<LocalTime> times() {
        return times;
    }

    public boolean isDay(LocalDate date) {
        return days.contains(date);
    }

    public boolean isTime(LocalTime time) {
        return times.contains(time);
    }

    public boolean isSeeded(LocalDate date, LocalTime time) {
        return seeded.contains(key(date, time));
    }

    /** A slot is past once its start time has gone by in Nairobi. */
    public boolean isPast(LocalDate date, LocalTime time) {
        return !ZonedDateTime.of(date, time, ZONE).isAfter(ZonedDateTime.now(ZONE));
    }

    public static String key(LocalDate date, LocalTime time) {
        return date + "T" + time;
    }
}
