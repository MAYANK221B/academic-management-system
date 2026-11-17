package com.acadence.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public KeyPair jwtKeyPair(JwtProperties properties) {
        try (var privateInput = Files.newInputStream(Path.of(properties.privateKeyPath()));
             var publicInput = Files.newInputStream(Path.of(properties.publicKeyPath()))) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicInput);
            if (privateKey == null || publicKey == null
                    || publicKey.getModulus().bitLength() < 2048
                    || !privateKey.getModulus().equals(publicKey.getModulus())) {
                throw new IllegalArgumentException("Expected matching RSA keys of at least 2048 bits");
            }
            // Verify that the two externally supplied keys form a usable pair.
            Signature signature = Signature.getInstance("SHA256withRSA");
            byte[] probe = "Acadence RSA configuration check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            signature.initSign(privateKey);
            signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(probe);
            if (!signature.verify(signed)) {
                throw new IllegalArgumentException("RSA key pair does not match");
            }
            return new KeyPair(publicKey, privateKey);
        } catch (Exception exception) {
            // Do not include PEM contents or nested parser diagnostics in the configuration error.
            throw new IllegalStateException("Invalid acadence.jwt key configuration: provide readable, matching "
                    + "RSA PEM files (PKCS#8 private key and X.509 public key), at least 2048 bits");
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair jwtKeyPair) {
        return NimbusJwtEncoder.withKeyPair((RSAPublicKey) jwtKeyPair.getPublic(),
                (RSAPrivateKey) jwtKeyPair.getPrivate()).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair jwtKeyPair, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtKeyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.audience())),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, expiration -> expiration != null),
                new JwtClaimValidator<Object>(JwtClaimNames.NBF, notBefore -> notBefore != null)));
        return decoder;
    }
}
