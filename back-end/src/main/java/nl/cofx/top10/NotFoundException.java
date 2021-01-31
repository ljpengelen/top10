package nl.cofx.top10;

public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = -1380304081817885283L;

    public NotFoundException(String message) {
        super(message);
    }
}
