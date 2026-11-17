package com.acadence.controller;

import com.acadence.enums.*;
import com.acadence.exception.GlobalExceptionHandler;
import com.acadence.mapper.CourseMembershipMapper;
import com.acadence.model.*;
import com.acadence.repository.*;
import com.acadence.security.CustomUserDetailsService;
import com.acadence.security.SecurityConfig;
import com.acadence.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseMembershipControllerTest {
    private static final String URL = "/api/v1/offerings/101/members";
    private static final String EMAIL = "target@umn.edu";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtDecoder decoder;
    private UserRepository users;
    private CourseOfferingRepository offerings;
    private CourseMembershipRepository memberships;
    private UserPlatformRoleRepository roles;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean CourseOfferingRepository offeringRepository() { return mock(CourseOfferingRepository.class); }
        @Bean CourseMembershipRepository membershipRepository() { return mock(CourseMembershipRepository.class); }
        @Bean UserPlatformRoleRepository roleRepository() { return mock(UserPlatformRoleRepository.class); }
        @Bean CourseMembershipMapper membershipMapper() { return Mappers.getMapper(CourseMembershipMapper.class); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, SecurityConfig.class, CustomUserDetailsService.class,
                CourseMembershipController.class, CourseMemberManagementService.class, CourseAuthorizationService.class,
                PlatformAuthorizationService.class, CourseMembershipService.class, GlobalExceptionHandler.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        decoder = context.getBean(JwtDecoder.class);
        users = context.getBean(UserRepository.class);
        offerings = context.getBean(CourseOfferingRepository.class);
        memberships = context.getBean(CourseMembershipRepository.class);
        roles = context.getBean(UserPlatformRoleRepository.class);
    }

    @AfterEach
    void close() { context.close(); }

    private String body(CourseRole role) { return "{\"email\":\"" + EMAIL + "\",\"role\":\"" + role + "\"}"; }

    private void authenticate(String subject) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(subject).claim("roles", List.of("ACADENCE_ADMIN", "PROFESSOR")).build());
    }

    private void actor(String actor) {
        authenticate("7");
        if (actor.equals("ADMIN")) {
            when(roles.existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN)).thenReturn(true);
        } else if (!actor.equals("NONE")) {
            long offeringId = actor.endsWith("_OTHER") ? 102L : 101L;
            CourseRole role = CourseRole.valueOf(actor.replace("_OTHER", ""));
            when(memberships.findByUser_UserIdAndOffering_OfferingId(7L, offeringId))
                    .thenReturn(Optional.of(CourseMembership.builder().role(role).build()));
        }
    }

    private void target(AccountStatus status) {
        User user = User.builder().email(EMAIL).accountStatus(status).build();
        ReflectionTestUtils.setField(user, "userId", 42L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(users.findById(42L)).thenReturn(Optional.of(user));
    }

    private void offering() {
        CourseOffering offering = CourseOffering.builder().build();
        ReflectionTestUtils.setField(offering, "offeringId", 101L);
        when(offerings.findById(101L)).thenReturn(Optional.of(offering));
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,TA", "PROFESSOR,TA", "ADMIN,STUDENT", "PROFESSOR,STUDENT", "TA,STUDENT"})
    void authorizedActorCanEnroll(String actor, CourseRole requestedRole) throws Exception {
        actor(actor);
        target(AccountStatus.ACTIVE);
        offering();
        when(memberships.save(any())).thenAnswer(invocation -> {
            CourseMembership membership = invocation.getArgument(0);
            ReflectionTestUtils.setField(membership, "membershipId", 99L);
            return membership;
        });
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body(requestedRole)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.membershipId").value(99))
                .andExpect(jsonPath("$.userId").value(42)).andExpect(jsonPath("$.offeringId").value(101))
                .andExpect(jsonPath("$.role").value(requestedRole.name()));
        var order = inOrder(roles, users, memberships);
        order.verify(roles).existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN);
        if (!actor.equals("ADMIN")) order.verify(memberships).findByUser_UserIdAndOffering_OfferingId(7L, 101L);
        order.verify(users).findByEmail(EMAIL);
        order.verify(memberships).save(argThat(value -> value.getRole() == requestedRole));
    }

    @ParameterizedTest
    @CsvSource({"PROFESSOR_OTHER,TA", "TA,TA", "STUDENT,TA", "NONE,TA",
            "TA_OTHER,STUDENT", "PROFESSOR_OTHER,STUDENT", "STUDENT,STUDENT", "NONE,STUDENT"})
    void unauthorizedActorCannotEnrollOrInspectTarget(String actor, CourseRole requestedRole) throws Exception {
        actor(actor);
        mvc.perform(post(URL).param("actingUserId", "999").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(body(requestedRole)))
                .andExpect(status().isForbidden());
        verify(memberships).findByUser_UserIdAndOffering_OfferingId(7L, 101L);
        verify(memberships, never()).save(any());
        verifyNoInteractions(users, offerings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PROFESSOR", "TA", "NONE"})
    void nobodyCanAssignProfessorThroughThisEndpoint(String actor) throws Exception {
        actor(actor);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.PROFESSOR)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, offerings);
        verify(memberships, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "broken", "{\"email\":\"target@umn.edu\"}",
            "{\"email\":\"target@umn.edu\",\"role\":null}",
            "{\"email\":\"target@umn.edu\",\"role\":\"ADMIN\"}",
            "{\"email\":\"invalid\",\"role\":\"TA\"}",
            "{\"email\":\" \",\"role\":\"STUDENT\"}",
            "{\"email\":\"target@umn.edu\",\"role\":\"TA\",\"actingUserId\":999}",
            "{\"email\":\"target@umn.edu\",\"role\":\"TA\",\"offeringId\":102}"})
    void invalidOrSpoofedBodyReturns400(String body) throws Exception {
        authenticate("7");
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(roles, users, offerings, memberships);
    }

    @Test
    void unaffiliatedUserCannotSelfEnroll() throws Exception {
        authenticate("42");
        mvc.perform(post(URL).header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.STUDENT)))
                .andExpect(status().isForbidden());
        verify(roles).existsByUser_UserIdAndRole(42L, PlatformRole.ACADENCE_ADMIN);
        verify(memberships).findByUser_UserIdAndOffering_OfferingId(42L, 101L);
        verify(memberships, never()).save(any());
        verifyNoInteractions(users, offerings);
    }

    @Test
    void unknownTargetReturns404() throws Exception {
        actor("ADMIN");
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.TA)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(offerings, memberships);
        verify(users, never()).save(any());
    }

    @Test
    void inactiveTargetReturns400() throws Exception {
        actor("ADMIN");
        target(AccountStatus.DISABLED);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.STUDENT)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(offerings, memberships);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TA", "STUDENT"})
    void duplicateMembershipReturns409WithoutChangingExistingRole(String role) throws Exception {
        actor("ADMIN");
        target(AccountStatus.ACTIVE);
        offering();
        when(memberships.existsByUser_UserIdAndOffering_OfferingId(42L, 101L)).thenReturn(true);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.valueOf(role))))
                .andExpect(status().isConflict());
        verify(memberships, never()).save(any());
    }

    @Test
    void unknownOfferingReturns404ForAdmin() throws Exception {
        actor("ADMIN");
        target(AccountStatus.ACTIVE);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.STUDENT)))
                .andExpect(status().isNotFound());
        verify(memberships, never()).save(any());
    }

    @Test
    void missingJwtReturns401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.TA)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, users, offerings, memberships);
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(post(URL).header("Authorization", "Bearer invalid").contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.TA)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, users, offerings, memberships);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "0", "-1", "9223372036854775808"})
    void invalidSubjectReturns401BeforeAuthorization(String subject) throws Exception {
        authenticate(subject);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body(CourseRole.TA)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, users, offerings, memberships);
    }
}
