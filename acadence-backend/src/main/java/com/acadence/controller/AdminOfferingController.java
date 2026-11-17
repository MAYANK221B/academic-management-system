package com.acadence.controller;

import com.acadence.dto.request.AssignProfessorRequest;
import com.acadence.dto.response.CourseMembershipResponse;
import com.acadence.security.JwtUserIdResolver;
import com.acadence.service.ProfessorAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/offerings")
@RequiredArgsConstructor
public class AdminOfferingController {

    private final ProfessorAssignmentService professorAssignmentService;

    @PostMapping("/{offeringId}/professors")
    public ResponseEntity<CourseMembershipResponse> assignProfessor(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long offeringId,
            @Valid @RequestBody AssignProfessorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(professorAssignmentService.assignProfessor(
                JwtUserIdResolver.resolve(jwt), offeringId, request.email()));
    }
}
