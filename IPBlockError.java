package com.xiaohongshu.client.exception;

/**
 * Exception thrown when IP is blocked
 */
public class IPBlockError extends Exception {
    
    public IPBlockError(String message) {
        super(message);
    }
    
    public IPBlockError(String message, Throwable cause) {
        super(message, cause);
    }
}