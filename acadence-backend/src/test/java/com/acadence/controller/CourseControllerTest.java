package com.acadence.controller;

import com.acadence.enums.PlatformRole;
import com.acadence.exception.GlobalExceptionHandler;
import com.acadence.mapper.CourseMapper;
import com.acadence.model.Course;
import com.acadence.repository.CourseRepository;
import com.acadence.repository.UserPlatformRoleRepository;
import com.acadence.repository.UserRepository;
import com.acadence.security.CustomUserDetailsService;
import com.acadence.security.SecurityConfig;
import com.acadence.service.CourseService;
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

class CourseControllerTest {
    private static final String URL = "/api/v1/courses";
    private static final String BODY = "{\"courseCode\":\"CS101\",\"courseName\":\"Computing\",\"description\":\"Introduction\"}";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtDecoder decoder;
    private CourseRepository courses;
    private UserPlatformRoleRepository roles;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean CourseRepository courseRepository() { return mock(CourseRepository.class); }
        @Bean UserPlatformRoleRepository roleRepository() { return mock(UserPlatformRoleRepository.class); }
        @Bean CourseMapper courseMapper() { return Mappers.getMapper(CourseMapper.class); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, SecurityConfig.class, CustomUserDetailsService.class,
                CourseController.class, CourseService.class, PlatformAuthorizationService.class,
                GlobalExceptionHandler.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        decoder = context.getBean(JwtDecoder.class);
        courses = context.getBean(CourseRepository.class);
        roles = context.getBean(UserPlatformRoleRepository.class);
    }

    @AfterEach
    void close() { context.close(); }

    private void authenticate(String subject) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(subject).claim("roles", List.of("ACADENCE_ADMIN")).build());
    }

    private Course course() {
        Course course = Course.builder().courseCode("CS101").courseName("Computing").description("Introduction").build();
        ReflectionTestUtils.setField(course, "courseId", 42L);
        return course;
    }

    @Test
    void adminCreatesCourseUsingVerifiedIdentity() throws Exception {
        authenticate("7");
        when(roles.existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN)).thenReturn(true);
        when(courses.save(any())).thenReturn(course());
        mvc.perform(post(URL).param("actingUserId", "999").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.courseId").value(42))
                .andExpect(jsonPath("$.courseCode").value("CS101"))
                .andExpect(jsonPath("$.courseName").value("Computing"))
                .andExpect(jsonPath("$.description").value("Introduction"));
        var order = inOrder(roles, courses);
        order.verify(roles).existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN);
        order.verify(courses).existsByCourseCode("CS101");
        order.verify(courses).save(any());
        verifyNoMoreInteractions(roles);
    }

    @Test
    void regularUserCannotSpoofRoleOrActingUser() throws Exception {
        authenticate("8");
        mvc.perform(post(URL).param("actingUserId", "7").param("role", "ACADENCE_ADMIN")
                        .header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"CS101\",\"courseName\":\"Computing\",\"actingUserId\":7,\"role\":\"ACADENCE_ADMIN\"}"))
                .andExpect(status().isForbidden());
        verify(roles).existsByUser_UserIdAndRole(8L, PlatformRole.ACADENCE_ADMIN);
        verifyNoInteractions(courses);
    }

    @Test
    void regularUserCanListAndRetrieveCourses() throws Exception {
        authenticate("8");
        when(courses.findAll()).thenReturn(List.of(course()));
        when(courses.findById(42L)).thenReturn(Optional.of(course()));
        mvc.perform(get(URL).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].courseId").value(42));
        mvc.perform(get(URL + "/42").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.courseCode").value("CS101"));
        verifyNoInteractions(roles);
    }

    @Test
    void emptyCatalogReturnsEmptyArray() throws Exception {
        authenticate("8");
        when(courses.findAll()).thenReturn(List.of());
        mvc.perform(get(URL).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void missingCourseReturns404() throws Exception {
        authenticate("8");
        mvc.perform(get(URL + "/42").header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Course not found"));
    }

    @Test
    void duplicateCourseReturns409() throws Exception {
        authenticate("7");
        when(roles.existsByUser_UserIdAndRole(7L, PlatformRole.ACADENCE_ADMIN)).thenReturn(true);
        when(courses.existsByCourseCode("CS101")).thenReturn(true);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        verify(courses, never()).save(any());
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/42")).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, courses);
    }

    @Test
    void invalidTokenCannotAccessCourses() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(get(URL).header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, courses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "0", "-1", "9223372036854775808"})
    void invalidSubjectCannotCreateCourse(String subject) throws Exception {
        authenticate(subject);
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(roles, courses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"courseCode\":\"\",\"courseName\":\"Computing\"}",
            "{\"courseCode\":\"CS101\",\"courseName\":\" \"}", "broken"})
    void invalidBodyIsRejectedBeforeService(String body) throws Exception {
        authenticate("7");
        mvc.perform(post(URL).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(roles, courses);
    }
}
