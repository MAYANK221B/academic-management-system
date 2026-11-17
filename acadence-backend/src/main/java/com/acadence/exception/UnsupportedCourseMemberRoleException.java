package com.acadence.exception;

public class UnsupportedCourseMemberRoleException extends RuntimeException {
    public UnsupportedCourseMemberRoleException() {
        super("Only TA or STUDENT roles can be assigned through this endpoint");
    }
}
