package ke.chapacuts.booking;

import java.security.SecureRandom;

/** 8 uppercase characters, no I, O, 0 or 1 so nobody misreads one over the counter. */
final class References {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private References() {
    }

    static String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
