package com.acadence.exception;

public class DuplicateCourseMembershipException extends RuntimeException {

    public DuplicateCourseMembershipException() {
        super("User already belongs to this course offering");
    }
}
