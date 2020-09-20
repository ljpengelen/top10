package nl.friendlymirror.top10;

public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 6875596847137823277L;

    public ValidationException(String message) {
        super(message);
    }
}
