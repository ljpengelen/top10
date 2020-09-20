package nl.friendlymirror.top10;

public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 4737061735215949294L;

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
