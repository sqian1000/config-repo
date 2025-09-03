/**
 * Exception thrown when the IP address is blocked by the service
 */
public class IPBlockError extends RuntimeException {
    
    public IPBlockError(String message) {
        super(message);
    }
    
    public IPBlockError(String message, Throwable cause) {
        super(message, cause);
    }
}