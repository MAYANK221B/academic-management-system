package com.acadence.controller;

import com.acadence.dto.request.AddCourseMemberRequest;
import com.acadence.dto.response.CourseMembershipResponse;
import com.acadence.security.JwtUserIdResolver;
import com.acadence.service.CourseMemberManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/offerings")
@RequiredArgsConstructor
public class CourseMembershipController {

    private final CourseMemberManagementService courseMemberManagementService;

    @PostMapping("/{offeringId}/members")
    public ResponseEntity<CourseMembershipResponse> addMember(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long offeringId,
            @Valid @RequestBody AddCourseMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseMemberManagementService.addMember(
                JwtUserIdResolver.resolve(jwt), offeringId, request.email(), request.role()));
    }
}
