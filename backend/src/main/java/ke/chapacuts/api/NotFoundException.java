package ke.chapacuts.api;

/** Rendered as 404 {"message"}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
