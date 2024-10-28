package nl.cofx.top10;

import java.io.Serial;

public class NotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -1380304081817885283L;

    public NotFoundException(String message) {
        super(message);
    }
}
