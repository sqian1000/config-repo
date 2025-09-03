/**
 * Exception thrown when there's an error fetching data from the API
 */
public class DataFetchError extends RuntimeException {
    
    public DataFetchError(String message) {
        super(message);
    }
    
    public DataFetchError(String message, Throwable cause) {
        super(message, cause);
    }
}