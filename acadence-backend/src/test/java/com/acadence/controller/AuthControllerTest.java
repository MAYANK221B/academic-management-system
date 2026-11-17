package com.acadence.controller;

import com.acadence.dto.request.LoginRequest;
import com.acadence.dto.response.LoginResponse;
import com.acadence.dto.response.UserResponse;
import com.acadence.enums.AccountStatus;
import com.acadence.exception.UserNotFoundException;
import com.acadence.exception.GlobalExceptionHandler;
import com.acadence.repository.UserRepository;
import com.acadence.security.CustomUserDetailsService;
import com.acadence.security.SecurityConfig;
import com.acadence.service.AuthService;
import com.acadence.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AuthService authService;
    private JwtDecoder jwtDecoder;
    private static final String REQUEST = """
            {"email":"alex@example.com","password":"SecurePass123!"}
            """;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean CustomUserDetailsService userDetailsService() {
            return new CustomUserDetailsService(mock(UserRepository.class));
        }
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/test/protected")
        String protectedResource() { return "protected"; }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, SecurityConfig.class, AuthController.class,
                UserController.class, HealthController.class, GlobalExceptionHandler.class, ProtectedController.class);
        context.refresh();
        authService = context.getBean(AuthService.class);
        jwtDecoder = context.getBean(JwtDecoder.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @AfterEach
    void tearDown() { context.close(); }

    @Test
    void validLoginIsPublicAndReturnsOnlyResponseFields() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse(101L, "test-access-token", "Bearer", 900));

        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(101))
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password", "passwordHash", "SecurePass123!", "alex@example.com");
        verify(authService).login(new LoginRequest("alex@example.com", "SecurePass123!"));
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void incorrectCredentialsAndDisabledAccountsHaveIdenticalGenericResponses() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("internal passwordHash detail"));
        String invalid = unauthorizedLoginBody();
        doThrow(new DisabledException("internal account disabled detail")).when(authService).login(any());
        String disabled = unauthorizedLoginBody();
        assertThat(disabled).isEqualTo(invalid);
        assertThat(disabled).doesNotContain("internal", "disabled", "passwordHash", "SecurePass123!", "accessToken", "trace");
    }

    private String unauthorizedLoginBody() throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"password\":\"SecurePass123!\"}", "{\"email\":\"alex@example.com\"}",
            "{\"email\":\"not-an-email\",\"password\":\"SecurePass123!\"}",
            "{\"email\":\" \",\"password\":\"SecurePass123!\"}",
            "{\"email\":\"alex@example.com\",\"password\":\" \"}", "{malformed"
    })
    void invalidRequestsReturnGeneric400WithoutInvokingService(String request) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request body"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SecurePass123!", "passwordHash", "accessToken", "trace", "Exception");
        verifyNoInteractions(authService);
    }

    @Test
    void registrationRemainsPublic() throws Exception {
        mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"SecurePass123!\"}"))
                .andExpect(status().isCreated());
        verify(context.getBean(UserService.class)).registerStudent(any());
    }

    @Test
    void otherRequestsRequireAuthentication() throws Exception {
        mvc.perform(get("/test/protected")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/register")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublicOnlyForExactGetEndpoint() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}"));
        mvc.perform(post("/api/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health/details")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(authService, context.getBean(UserService.class), jwtDecoder);
    }

    @Test
    void invalidBearerTokensRemainHandledBySpringSecurity() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(get("/test/protected").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")))
                .andExpect(content().string(""));
    }

    @Test
    void validatedBearerTokenCanAccessProtectedEndpoint() throws Exception {
        when(jwtDecoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("101").build());
        mvc.perform(get("/test/protected").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(content().string("protected"));
    }

    private void currentUserToken(String subject) {
        when(jwtDecoder.decode("profile-token")).thenReturn(Jwt.withTokenValue("profile-token")
                .header("alg", "RS256").subject(subject).build());
    }

    @Test
    void currentUserUsesVerifiedSubjectAndIgnoresRequestUserId() throws Exception {
        currentUserToken("101");
        UserService userService = context.getBean(UserService.class);
        when(userService.getCurrentUser(101L)).thenReturn(new UserResponse(101L, "Alex", "alex@example.com",
                AccountStatus.ACTIVE, java.time.Instant.parse("2026-01-01T00:00:00Z")));
        mvc.perform(get("/api/v1/users/me").param("userId", "999")
                        .header("Authorization", "Bearer profile-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(101))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(userService).getCurrentUser(101L);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void currentUserRequiresJwt() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(UserService.class));
    }

    @Test
    void currentUserRejectsInvalidJwt() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(UserService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808", "0", "-1"})
    void currentUserRejectsInvalidSubject(String subject) throws Exception {
        currentUserToken(subject);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer profile-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid access token"));
        verifyNoInteractions(context.getBean(UserService.class));
    }

    @Test
    void currentUserReturns404ForDeletedUser() throws Exception {
        currentUserToken("101");
        when(context.getBean(UserService.class).getCurrentUser(101L)).thenThrow(new UserNotFoundException());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer profile-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    void currentUserReturns403ForDisabledAccount() throws Exception {
        currentUserToken("101");
        when(context.getBean(UserService.class).getCurrentUser(101L))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer profile-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("Access denied"));
    }
}
