package com.acadence.controller;

import com.acadence.enums.PlatformRole;
import com.acadence.enums.Semester;
import com.acadence.exception.GlobalExceptionHandler;
import com.acadence.mapper.CourseOfferingMapper;
import com.acadence.model.Course;
import com.acadence.model.CourseOffering;
import com.acadence.repository.*;
import com.acadence.security.CustomUserDetailsService;
import com.acadence.security.SecurityConfig;
import com.acadence.service.CourseOfferingService;
import com.acadence.service.PlatformAuthorizationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseOfferingControllerTest {
    private static final String URL = "/api/v1/courses/42/offerings";
    private static final String DETAIL_URL = "/api/v1/offerings/101";
    private static final String BODY = "{\"semester\":\"FALL\",\"year\":2026,\"section\":\"A\"}";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtDecoder decoder;
    private CourseRepository courses;
    private CourseOfferingRepository offerings;
    private UserPlatformRoleRepository roles;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean CourseRepository courseRepository() { return mock(CourseRepository.class); }
        @Bean CourseOfferingRepository offeringRepository() { return mock(CourseOfferingRepository.class); }
        @Bean UserPlatformRoleRepository roleRepository() { return mock(UserPlatformRoleRepository.class); }
        @Bean CourseOfferingMapper offeringMapper() { return Mappers.getMapper(CourseOfferingMapper.class); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, SecurityConfig.class, CustomUserDetailsService.class,
                CourseOfferingController.class, CourseOfferingService.class, PlatformAuthorizationService.class,
                GlobalExceptionHandler.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        decoder = context.getBean(JwtDecoder.class);
        courses = context.getBean(CourseRepository.class);
        offerings = context.getBean(CourseOfferingRepository.class);
        roles = context.getBean(UserPlatformRoleRepository.class);
    }

    @AfterEach
    void close() { context.close(); }

    private void authenticate(String subject) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(subject).claim("roles", List.of("ACADENCE_ADMIN")).build());
    }

    private void admin() {
        authenticate("7");
        when(roles.existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN)).thenReturn(true);
    }

    private Course course() {
        Course course = Course.builder().courseCode("CS101").courseName("Computing").build();
        ReflectionTestUtils.setField(course, "courseId", 42L);
        return course;
    }

    private CourseOffering offering() {
        CourseOffering offering = CourseOffering.builder().course(course()).semester(Semester.FALL)
                .year(2026).section("A").build();
        ReflectionTestUtils.setField(offering, "offeringId", 101L);
        return offering;
    }

    @Test
    void adminCreatesOfferingUsingUrlCourseAndVerifiedIdentity() throws Exception {
        admin();
        when(courses.findById(42L)).thenReturn(Optional.of(course()));
        when(offerings.save(any())).thenReturn(offering());
        mvc.perform(post(URL).param("courseId", "999").param("actingUserId", "999")
                        .header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.offeringId").value(101))
                .andExpect(jsonPath("$.courseId").value(42)).andExpect(jsonPath("$.courseCode").value("CS101"))
                .andExpect(jsonPath("$.courseName").value("Computing"))
                .andExpect(jsonPath("$.semester").value("FALL")).andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.section").value("A"));
        var order = inOrder(roles, courses, offerings);
        order.verify(roles).existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN);
        order.verify(courses).findById(42L);
        order.verify(offerings).existsByCourse_CourseIdAndSemesterAndYearAndSection(42L, Semester.FALL, 2026, "A");
        order.verify(offerings).save(argThat(value -> value.getCourse().getCourseId().equals(42L)
                && value.getSemester() == Semester.FALL && value.getYear() == 2026 && value.getSection().equals("A")));
        verifyNoMoreInteractions(roles, courses, offerings);
    }

    @Test
    void nonAdminCannotCreateDespiteRoleClaimAndSpoofedQuery() throws Exception {
        authenticate("8");
        mvc.perform(post(URL).param("actingUserId", "7").param("role", "ACADENCE_ADMIN")
                        .header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(roles).existsByUser_UserIdAndRole(8L, PlatformRole.ACADENCE_ADMIN);
        verifyNoInteractions(courses, offerings);
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(DETAIL_URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, courses, offerings);
    }

    @Test
    void regularUserCanListAndRetrieveOfferings() throws Exception {
        authenticate("8");
        when(courses.existsById(42L)).thenReturn(true);
        when(offerings.findByCourse_CourseId(42L)).thenReturn(List.of(offering()));
        when(offerings.findById(101L)).thenReturn(Optional.of(offering()));
        mvc.perform(get(URL).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].offeringId").value(101));
        mvc.perform(get(DETAIL_URL).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.courseId").value(42));
        verifyNoInteractions(roles);
    }

    @Test
    void existingCourseWithNoOfferingsReturnsEmptyArray() throws Exception {
        authenticate("8");
        when(courses.existsById(42L)).thenReturn(true);
        when(offerings.findByCourse_CourseId(42L)).thenReturn(List.of());
        mvc.perform(get(URL).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void missingCourseReturns404ForCreationAndListing() throws Exception {
        admin();
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Course not found"));
        mvc.perform(get(URL).header("Authorization", "Bearer token")).andExpect(status().isNotFound());
        verifyNoInteractions(offerings);
    }

    @Test
    void missingOfferingReturns404() throws Exception {
        authenticate("8");
        mvc.perform(get(DETAIL_URL).header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Course offering not found"));
    }

    @Test
    void duplicateOfferingReturns409() throws Exception {
        admin();
        when(courses.findById(42L)).thenReturn(Optional.of(course()));
        when(offerings.existsByCourse_CourseIdAndSemesterAndYearAndSection(42L, Semester.FALL, 2026, "A"))
                .thenReturn(true);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        verify(offerings, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "broken", "{\"year\":2026,\"section\":\"A\"}",
            "{\"semester\":\"WINTER\",\"year\":2026,\"section\":\"A\"}",
            "{\"semester\":\"FALL\",\"section\":\"A\"}",
            "{\"semester\":\"FALL\",\"year\":0,\"section\":\"A\"}",
            "{\"semester\":\"FALL\",\"year\":-1,\"section\":\"A\"}",
            "{\"semester\":\"FALL\",\"year\":2026}",
            "{\"semester\":\"FALL\",\"year\":2026,\"section\":\" \"}",
            "{\"semester\":\"FALL\",\"year\":2026,\"section\":\"123456789012345678901\"}",
            "{\"semester\":\"FALL\",\"year\":2026,\"section\":\"A\",\"courseId\":999}",
            "{\"semester\":\"FALL\",\"year\":2026,\"section\":\"A\",\"courseId\":42}",
            "{\"semester\":\"FALL\",\"year\":2026,\"section\":\"A\",\"actingUserId\":7}"})
    void invalidOrUnexpectedBodyFieldsReturn400(String body) throws Exception {
        authenticate("7");
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(roles, courses, offerings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "0", "-1", "9223372036854775808"})
    void invalidSubjectCannotCreateOffering(String subject) throws Exception {
        authenticate(subject);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, courses, offerings);
    }

    @Test
    void invalidJwtIsRejected() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(get(URL).header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, courses, offerings);
    }
}
