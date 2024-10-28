package nl.cofx.top10;

import java.io.Serial;

public class ConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 7150499842543166545L;

    public ConflictException(String message) {
        super(message);
    }
}
