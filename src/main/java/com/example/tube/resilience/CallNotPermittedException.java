package com.example.tube.resilience;

public class CallNotPermittedException extends RuntimeException {
    public CallNotPermittedException(String message) { super(message); }
}

