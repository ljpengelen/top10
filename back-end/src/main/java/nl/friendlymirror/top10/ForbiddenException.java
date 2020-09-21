package nl.friendlymirror.top10;

public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 6849821041979454973L;

    public ForbiddenException(String message) {
        super(message);
    }
}
