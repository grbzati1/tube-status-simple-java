package com.example.tube.resilience;

public class HttpStatusException extends RuntimeException {
    private final int statusCode;
    public HttpStatusException(int statusCode, String message) { super(message); this.statusCode = statusCode; }
    public int statusCode() { return statusCode; }
}
