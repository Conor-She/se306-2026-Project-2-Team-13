package com.lavs.data;

// Thrown by MainframeGateway on any failure. Distinguishes a business
// error (the mainframe rejected the request) from the mainframe being
// unreachable, per this ticket's acceptance criteria.
public class MainframeException extends RuntimeException {

    public enum Type {
        BUSINESS_ERROR,
        UNAVAILABLE
    }

    private final Type type;
    private final int errorCode;

    public MainframeException(Type type, int errorCode, String message) {
        super(message);
        this.type = type;
        this.errorCode = errorCode;
    }

    public Type getType() {
        return type;
    }

    public int getErrorCode() {
        return errorCode;
    }
}