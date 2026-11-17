package com.acadence.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedCourseMemberRoleException.class)
    public ProblemDetail handleUnsupportedMemberRole(UnsupportedCourseMemberRoleException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(CourseNotFoundException.class)
    public ProblemDetail handleCourseNotFound(CourseNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Course not found");
    }

    @ExceptionHandler(DuplicateCourseCodeException.class)
    public ProblemDetail handleDuplicateCourseCode(DuplicateCourseCodeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Course code already exists");
    }

    @ExceptionHandler(CourseOfferingNotFoundException.class)
    public ProblemDetail handleOfferingNotFound(CourseOfferingNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Course offering not found");
    }

    @ExceptionHandler(DuplicateCourseOfferingException.class)
    public ProblemDetail handleDuplicateOffering(DuplicateCourseOfferingException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Course offering already exists");
    }

    @ExceptionHandler(DuplicateCourseMembershipException.class)
    public ProblemDetail handleDuplicateMembership(DuplicateCourseMembershipException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "User already belongs to this course offering");
    }

    @ExceptionHandler(InactiveUserException.class)
    public ProblemDetail handleInactiveUser(InactiveUserException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Target user account is not active");
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "User not found");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(OAuth2AuthenticationException.class)
    public ProblemDetail handleInvalidAccessToken(OAuth2AuthenticationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid access token");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail handleInvalidRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
    }
}
