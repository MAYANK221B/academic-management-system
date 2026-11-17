package com.acadence.controller;

import com.acadence.dto.request.CreateCourseOfferingRequest;
import com.acadence.dto.request.CreateOfferingRequest;
import com.acadence.dto.response.CourseOfferingResponse;
import com.acadence.security.JwtUserIdResolver;
import com.acadence.service.CourseOfferingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseOfferingController {

    private final CourseOfferingService courseOfferingService;

    @PostMapping("/courses/{courseId}/offerings")
    public ResponseEntity<CourseOfferingResponse> createOffering(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId,
            @Valid @RequestBody CreateOfferingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseOfferingService.createOffering(
                JwtUserIdResolver.resolve(jwt), new CreateCourseOfferingRequest(
                        courseId, request.semester(), request.year(), request.section())));
    }

    @GetMapping("/courses/{courseId}/offerings")
    public ResponseEntity<List<CourseOfferingResponse>> getOfferingsByCourseId(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseOfferingService.getOfferingsByCourseId(courseId));
    }

    @GetMapping("/offerings/{offeringId}")
    public ResponseEntity<CourseOfferingResponse> getOfferingById(@PathVariable Long offeringId) {
        return ResponseEntity.ok(courseOfferingService.getOfferingById(offeringId));
    }
}
