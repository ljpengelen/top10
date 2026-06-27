package nl.cofx.top10;

import java.io.Serial;

public class InvalidCredentialsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4737061735215949294L;

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
