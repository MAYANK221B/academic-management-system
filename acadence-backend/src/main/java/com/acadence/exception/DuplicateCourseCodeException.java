package com.acadence.exception;

public class DuplicateCourseCodeException extends RuntimeException {

    public DuplicateCourseCodeException() {
        super("Course code already exists");
    }
}
