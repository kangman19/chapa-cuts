package ke.chapacuts.booking;

import ke.chapacuts.api.CustomerFacingException;

/** Accepts 07XXXXXXXX, +2547XXXXXXXX, 2547XXXXXXXX or 7XXXXXXXX and returns 2547XXXXXXXX. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalise(String raw) {
        if (raw == null) {
            throw invalid();
        }
        String s = raw.replaceAll("[\\s\\-()]", "");
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (s.startsWith("254")) {
            s = s.substring(3);
        } else if (s.startsWith("0")) {
            s = s.substring(1);
        }
        if (!s.matches("7\\d{8}")) {
            throw invalid();
        }
        return "254" + s;
    }

    private static CustomerFacingException invalid() {
        return new CustomerFacingException("Enter a Kenyan mobile number, like 0712 345 678.");
    }
}
