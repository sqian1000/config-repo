package com.xiaohongshu.client.exception;

/**
 * Exception thrown when data fetching fails
 */
public class DataFetchError extends Exception {
    
    public DataFetchError(String message) {
        super(message);
    }
    
    public DataFetchError(String message, Throwable cause) {
        super(message, cause);
    }
}