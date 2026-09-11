package ke.chapacuts.api;

/** Something went wrong that the customer needs to hear about, in words they can read. Rendered as 400 {"message"}. */
public class CustomerFacingException extends RuntimeException {

    public CustomerFacingException(String message) {
        super(message);
    }
}
