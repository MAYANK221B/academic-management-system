package com.acadence.exception;

public class CourseOfferingNotFoundException extends RuntimeException {

    public CourseOfferingNotFoundException() {
        super("Course offering not found");
    }
}
