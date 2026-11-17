package com.acadence.controller;

import com.acadence.enums.AccountStatus;
import com.acadence.enums.CourseRole;
import com.acadence.enums.PlatformRole;
import com.acadence.exception.GlobalExceptionHandler;
import com.acadence.mapper.CourseMembershipMapper;
import com.acadence.model.CourseMembership;
import com.acadence.model.CourseOffering;
import com.acadence.model.User;
import com.acadence.repository.*;
import com.acadence.security.CustomUserDetailsService;
import com.acadence.security.SecurityConfig;
import com.acadence.service.CourseMembershipService;
import com.acadence.service.PlatformAuthorizationService;
import com.acadence.service.ProfessorAssignmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminOfferingControllerTest {
    private static final String URL = "/api/v1/admin/offerings/101/professors";
    private static final String BODY = "{\"email\":\"professor@umn.edu\"}";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtDecoder decoder;
    private UserRepository users;
    private CourseOfferingRepository offerings;
    private CourseMembershipRepository memberships;
    private UserPlatformRoleRepository platformRoles;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean CourseOfferingRepository offeringRepository() { return mock(CourseOfferingRepository.class); }
        @Bean CourseMembershipRepository membershipRepository() { return mock(CourseMembershipRepository.class); }
        @Bean UserPlatformRoleRepository platformRoleRepository() { return mock(UserPlatformRoleRepository.class); }
        @Bean CourseMembershipMapper membershipMapper() { return Mappers.getMapper(CourseMembershipMapper.class); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, SecurityConfig.class, CustomUserDetailsService.class,
                AdminOfferingController.class, ProfessorAssignmentService.class, PlatformAuthorizationService.class,
                CourseMembershipService.class, GlobalExceptionHandler.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        decoder = context.getBean(JwtDecoder.class);
        users = context.getBean(UserRepository.class);
        offerings = context.getBean(CourseOfferingRepository.class);
        memberships = context.getBean(CourseMembershipRepository.class);
        platformRoles = context.getBean(UserPlatformRoleRepository.class);
    }

    @AfterEach
    void close() { context.close(); }

    private void authenticatedAdmin() {
        when(decoder.decode("verified-token")).thenReturn(Jwt.withTokenValue("verified-token")
                .header("alg", "RS256").subject("7").build());
        when(platformRoles.existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN)).thenReturn(true);
    }

    private void target(AccountStatus status) {
        User user = User.builder().email("professor@umn.edu").accountStatus(status).build();
        ReflectionTestUtils.setField(user, "userId", 42L);
        when(users.findByEmail("professor@umn.edu")).thenReturn(Optional.of(user));
        when(users.findById(42L)).thenReturn(Optional.of(user));
    }

    private void offering() {
        CourseOffering offering = CourseOffering.builder().build();
        ReflectionTestUtils.setField(offering, "offeringId", 101L);
        when(offerings.findById(101L)).thenReturn(Optional.of(offering));
    }

    @Test
    void adminAssignsProfessorUsingJwtIdentityAndBackendRole() throws Exception {
        authenticatedAdmin();
        target(AccountStatus.ACTIVE);
        offering();
        when(memberships.save(any())).thenAnswer(invocation -> {
            CourseMembership membership = invocation.getArgument(0);
            assertThat(membership.getRole()).isEqualTo(CourseRole.PROFESSOR);
            ReflectionTestUtils.setField(membership, "membershipId", 99L);
            ReflectionTestUtils.setField(membership, "joinedAt", Instant.parse("2026-01-01T00:00:00Z"));
            return membership;
        });
        mvc.perform(post(URL).param("actingUserId", "999").header("Authorization", "Bearer verified-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.membershipId").value(99))
                .andExpect(jsonPath("$.userId").value(42)).andExpect(jsonPath("$.offeringId").value(101))
                .andExpect(jsonPath("$.role").value("PROFESSOR"))
                .andExpect(jsonPath("$.joinedAt").value("2026-01-01T00:00:00Z"));
        var order = inOrder(platformRoles, users, memberships);
        order.verify(platformRoles).existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN);
        order.verify(users).findByEmail("professor@umn.edu");
        order.verify(memberships).save(any());
        verify(platformRoles, never()).existsByUser_UserIdAndRole(999L, PlatformRole.ACADENCE_ADMIN);
    }

    @Test
    void nonAdminCannotSpoofActingUserId() throws Exception {
        when(decoder.decode("regular-token")).thenReturn(Jwt.withTokenValue("regular-token")
                .header("alg", "RS256").subject("8").build());
        mvc.perform(post(URL).param("actingUserId", "7").header("Authorization", "Bearer regular-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(platformRoles).existsByUser_UserIdAndRole(8L, PlatformRole.ACADENCE_ADMIN);
        verifyNoInteractions(users, offerings, memberships);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(platformRoles, users, offerings, memberships);
    }

    @Test
    void invalidJwtIsDenied() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(post(URL).header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(platformRoles, users, offerings, memberships);
    }

    @Test
    void unknownProfessorEmailReturns404() throws Exception {
        authenticatedAdmin();
        mvc.perform(post(URL).header("Authorization", "Bearer verified-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        verifyNoInteractions(offerings, memberships);
    }

    @Test
    void inactiveAccountIsRejected() throws Exception {
        authenticatedAdmin();
        target(AccountStatus.DISABLED);
        mvc.perform(post(URL).header("Authorization", "Bearer verified-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(offerings, memberships);
    }

    @Test
    void nonexistentOfferingReturns404() throws Exception {
        authenticatedAdmin();
        target(AccountStatus.ACTIVE);
        mvc.perform(post(URL).header("Authorization", "Bearer verified-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        verifyNoInteractions(memberships);
    }

    @Test
    void duplicateMembershipReturns409WithoutChangingRole() throws Exception {
        authenticatedAdmin();
        target(AccountStatus.ACTIVE);
        offering();
        when(memberships.existsByUser_UserIdAndOffering_OfferingId(42L, 101L)).thenReturn(true);
        mvc.perform(post(URL).header("Authorization", "Bearer verified-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        verify(memberships, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"invalid\"}",
            "{\"email\":\"professor@umn.edu\",\"role\":\"STUDENT\"}",
            "{\"email\":\"professor@umn.edu\",\"actingUserId\":7}"})
    void invalidOrUnauthorizedRequestFieldsAreRejected(String body) throws Exception {
        authenticatedAdmin();
        mvc.perform(post(URL).header("Authorization", "Bearer verified-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(platformRoles, users, offerings, memberships);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808", "0", "-1"})
    void invalidSubjectNeverReachesAuthorization(String subject) throws Exception {
        when(decoder.decode("bad-subject")).thenReturn(Jwt.withTokenValue("bad-subject")
                .header("alg", "RS256").subject(subject).build());
        mvc.perform(post(URL).header("Authorization", "Bearer bad-subject")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(platformRoles, users, offerings, memberships);
    }
}
