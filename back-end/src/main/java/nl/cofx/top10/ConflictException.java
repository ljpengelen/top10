package nl.cofx.top10;

public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 7150499842543166545L;

    public ConflictException(String message) {
        super(message);
    }
}
