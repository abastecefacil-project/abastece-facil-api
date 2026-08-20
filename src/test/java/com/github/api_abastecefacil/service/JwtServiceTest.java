package com.github.api_abastecefacil.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    private static final String SECRET_KEY = "NDAwM2I2OGI4N2I4NDM3NWFlMzg3YjQ2YjUyNjg5OWMzNDg2ZjRkYjA3NWM4YTA2OGM3MmExMTI1ZTI5N2YxOQ==";
    private static final long EXPIRATION = 3600000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION);

        userDetails = new User("user@test.com", "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void generateToken_ShouldReturnValidJwtToken() {
        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotNull().isNotEmpty();
        assertThat(jwtService.extractUsername(token)).isEqualTo("user@test.com");
    }

    @Test
    void generateToken_WithExtraClaims_ShouldReturnTokenWithClaims() {
        var claims = new HashMap<String, Object>();
        claims.put("role", "ADMIN");

        String token = jwtService.generateToken(claims, userDetails);

        assertThat(token).isNotNull();
        assertThat(jwtService.extractUsername(token)).isEqualTo("user@test.com");
        assertThat((String) jwtService.extractClaim(token, c -> c.get("role"))).isEqualTo("ADMIN");
    }

    @Test
    void isTokenValid_ShouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(userDetails);

        boolean isValid = jwtService.isTokenValid(token, userDetails);

        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_ShouldReturnFalseForDifferentUser() {
        String token = jwtService.generateToken(userDetails);
        UserDetails otherUser = new User("other@test.com", "password", Collections.emptyList());

        boolean isValid = jwtService.isTokenValid(token, otherUser);

        assertThat(isValid).isFalse();
    }
}
