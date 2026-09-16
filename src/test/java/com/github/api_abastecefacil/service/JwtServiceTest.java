package com.github.api_abastecefacil.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Claims;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    @Test
    void generateToken_WithExplicitExpiration_ShouldHonourIt() {
        // Sobrecarga do S3: a expiracao passa a variar por perfil, e quem resolve
        // perfil -> prazo e o AuthService. O JwtService so recebe o numero.
        long trintaDias = 2_592_000_000L;

        String token = jwtService.generateToken(new HashMap<>(), userDetails, trintaDias);

        Date emitidoEm = jwtService.extractClaim(token, Claims::getIssuedAt);
        Date expiraEm = jwtService.extractClaim(token, Claims::getExpiration);

        // Tolerancia de 2s: emissao e leitura nao acontecem no mesmo milissegundo.
        assertThat(expiraEm.getTime() - emitidoEm.getTime())
                .isCloseTo(trintaDias, within(2000L));
    }

    @Test
    void generateToken_WithExplicitExpiration_ShouldDifferFromTheGlobalDefault() {
        String curto = jwtService.generateToken(new HashMap<>(), userDetails);
        String longo = jwtService.generateToken(new HashMap<>(), userDetails, 2_592_000_000L);

        Date expiraCurto = jwtService.extractClaim(curto, Claims::getExpiration);
        Date expiraLongo = jwtService.extractClaim(longo, Claims::getExpiration);

        assertThat(expiraLongo).isAfter(expiraCurto);
    }

    @Test
    void generateToken_WithExplicitExpiration_ShouldKeepExtraClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("perfil", "COLABORADOR");

        String token = jwtService.generateToken(claims, userDetails, 60_000L);

        assertThat((String) jwtService.extractClaim(token, c -> c.get("perfil"))).isEqualTo("COLABORADOR");
    }
}
