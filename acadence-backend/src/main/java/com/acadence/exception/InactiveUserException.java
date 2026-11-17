package com.acadence.exception;

public class InactiveUserException extends RuntimeException {

    public InactiveUserException() {
        super("Target user account is not active");
    }
}
