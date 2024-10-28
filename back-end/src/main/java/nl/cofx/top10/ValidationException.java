package nl.cofx.top10;

import java.io.Serial;

public class ValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 6875596847137823277L;

    public ValidationException(String message) {
        super(message);
    }
}
