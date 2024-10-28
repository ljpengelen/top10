package nl.cofx.top10;

import java.io.Serial;

public class ForbiddenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 6849821041979454973L;

    public ForbiddenException(String message) {
        super(message);
    }
}
