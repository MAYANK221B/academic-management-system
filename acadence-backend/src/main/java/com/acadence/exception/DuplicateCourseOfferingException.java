package com.acadence.exception;

public class DuplicateCourseOfferingException extends RuntimeException {

    public DuplicateCourseOfferingException() {
        super("Course offering already exists for this course, semester, year, and section");
    }
}
