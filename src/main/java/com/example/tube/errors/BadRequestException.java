package com.example.tube.errors;



public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
