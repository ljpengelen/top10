package nl.friendlymirror.top10;

public class InternalServerErrorException extends RuntimeException {

    private static final long serialVersionUID = -5610776568861958831L;

    public InternalServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public InternalServerErrorException(String message) {
        super(message);
    }
}
