package com.acadence.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class JwtConfigTest {

    @TempDir
    static Path directory;
    static KeyPair keys;
    static KeyPair otherKeys;

    @BeforeAll
    static void generateTestKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
        writePem("private.pem", "PRIVATE KEY", keys.getPrivate().getEncoded());
        writePem("public.pem", "PUBLIC KEY", keys.getPublic().getEncoded());
        writePem("other-public.pem", "PUBLIC KEY", otherKeys.getPublic().getEncoded());
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();
        writePem("weak-private.pem", "PRIVATE KEY", weak.getPrivate().getEncoded());
        writePem("weak-public.pem", "PUBLIC KEY", weak.getPublic().getEncoded());
        Files.writeString(directory.resolve("invalid.pem"), "not a PEM key");
    }

    private static void writePem(String name, String label, byte[] bytes) throws Exception {
        Files.writeString(directory.resolve(name), "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + label + "-----\n");
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(JwtConfig.class, JwtService.class)
                .withPropertyValues("acadence.jwt.issuer=acadence", "acadence.jwt.audience=acadence-api",
                        "acadence.jwt.access-token-expiration=15m",
                        "acadence.jwt.private-key-path=" + directory.resolve("private.pem"),
                        "acadence.jwt.public-key-path=" + directory.resolve("public.pem"));
    }

    @Configuration
    @EnableWebSecurity
    static class WebTestConfig {
        @Bean
        CustomUserDetailsService userDetailsService() {
            return new CustomUserDetailsService(org.mockito.Mockito.mock(com.acadence.repository.UserRepository.class));
        }
    }

    @Test
    void securityFilterChainStartsWithConfiguredDecoderAndAuthenticationBeans() {
        new WebApplicationContextRunner()
                .withUserConfiguration(JwtConfig.class, JwtService.class, SecurityConfig.class, WebTestConfig.class)
                .withPropertyValues("acadence.jwt.issuer=acadence", "acadence.jwt.audience=acadence-api",
                        "acadence.jwt.access-token-expiration=15m",
                        "acadence.jwt.private-key-path=" + directory.resolve("private.pem"),
                        "acadence.jwt.public-key-path=" + directory.resolve("public.pem"))
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(org.springframework.security.web.SecurityFilterChain.class)
                            .hasSingleBean(JwtDecoder.class)
                            .hasSingleBean(org.springframework.security.crypto.password.PasswordEncoder.class)
                            .hasSingleBean(org.springframework.security.authentication.AuthenticationManager.class);
                });
    }

    @Test
    void startsWithExternalPemKeysAndRoundTripsMinimalClaims() {
        context().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
            JwtService service = context.getBean(JwtService.class);
            Jwt jwt = context.getBean(JwtDecoder.class).decode(service.generateAccessToken(42L));
            assertThat(jwt.getSubject()).isEqualTo("42");
            assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo("acadence");
            assertThat(jwt.getAudience()).containsExactly("acadence-api");
            assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
            assertThat(jwt.getClaims().keySet()).isEqualTo(Set.of("sub", "iss", "aud", "iat", "nbf", "exp"));
            assertThat(jwt.getNotBefore()).isEqualTo(jwt.getIssuedAt());
            assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
            assertThat(service.getAccessTokenExpiration()).isEqualTo(Duration.ofMinutes(15));
            assertThatThrownBy(() -> service.generateAccessToken(null)).isInstanceOf(IllegalArgumentException.class);
        });
    }

    private JwtClaimsSet claims(String issuer, String audience, Instant notBefore, Instant expiration) {
        return JwtClaimsSet.builder().subject("42").issuer(issuer).audience(List.of(audience))
                .issuedAt(notBefore).notBefore(notBefore).expiresAt(expiration).build();
    }

    private String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    @Test
    void rejectsExpiredToken() {
        context().run(context -> {
            Instant now = Instant.now();
            String token = encode(context.getBean(JwtEncoder.class),
                    claims("acadence", "acadence-api", now.minusSeconds(600), now.minusSeconds(120)));
            assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(token)).isInstanceOf(JwtException.class);
        });
    }

    @Test
    void rejectsFutureNotBefore() {
        context().run(context -> {
            Instant future = Instant.now().plusSeconds(300);
            String token = encode(context.getBean(JwtEncoder.class),
                    claims("acadence", "acadence-api", future, future.plusSeconds(900)));
            assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(token)).isInstanceOf(JwtException.class);
        });
    }

    @Test
    void rejectsIncorrectIssuer() {
        assertRejectedClaims("another-issuer", "acadence-api");
    }

    @Test
    void rejectsIncorrectAudience() {
        assertRejectedClaims("acadence", "another-api");
    }

    private void assertRejectedClaims(String issuer, String audience) {
        context().run(context -> {
            Instant now = Instant.now();
            String token = encode(context.getBean(JwtEncoder.class), claims(issuer, audience, now, now.plusSeconds(900)));
            assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(token)).isInstanceOf(JwtException.class);
        });
    }

    @Test
    void rejectsAnotherSigningKey() {
        context().run(context -> {
            JwtEncoder otherEncoder = NimbusJwtEncoder.withKeyPair((RSAPublicKey) otherKeys.getPublic(),
                    (RSAPrivateKey) otherKeys.getPrivate()).build();
            Instant now = Instant.now();
            String token = encode(otherEncoder, claims("acadence", "acadence-api", now, now.plusSeconds(900)));
            assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(token)).isInstanceOf(JwtException.class);
        });
    }

    @Test
    void rejectsRs512EvenWithTrustedKey() {
        context().run(context -> {
            Instant now = Instant.now();
            var signed = new com.nimbusds.jwt.SignedJWT(
                    new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS512),
                    new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("42").issuer("acadence")
                            .audience("acadence-api").notBeforeTime(java.util.Date.from(now))
                            .expirationTime(java.util.Date.from(now.plusSeconds(900))).build());
            signed.sign(new com.nimbusds.jose.crypto.RSASSASigner(keys.getPrivate()));
            assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(signed.serialize()))
                    .isInstanceOf(JwtException.class);
        });
    }

    @Test
    void rejectsMissingExpirationAndNotBefore() {
        context().run(context -> {
            JwtClaimsSet claims = JwtClaimsSet.builder().subject("42").issuer("acadence")
                    .audience(List.of("acadence-api")).build();
            String token = encode(context.getBean(JwtEncoder.class), claims);
            assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(token)).isInstanceOf(JwtException.class);
        });
    }

    @Test
    void failsClearlyForMissingMalformedMismatchedAndWeakKeys() {
        for (String[] paths : List.of(new String[]{"missing.pem", "public.pem"},
                new String[]{"invalid.pem", "public.pem"}, new String[]{"private.pem", "other-public.pem"},
                new String[]{"weak-private.pem", "weak-public.pem"})) {
            context().withPropertyValues("acadence.jwt.private-key-path=" + directory.resolve(paths[0]),
                    "acadence.jwt.public-key-path=" + directory.resolve(paths[1])).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("Invalid acadence.jwt key configuration");
            });
        }
    }

    @Test
    void rejectsInvalidLifetimeAndBlankIssuer() {
        context().withPropertyValues("acadence.jwt.access-token-expiration=0s").run(context ->
                assertThat(context).hasFailed());
        context().withPropertyValues("acadence.jwt.issuer=").run(context ->
                assertThat(context).hasFailed());
    }
}
