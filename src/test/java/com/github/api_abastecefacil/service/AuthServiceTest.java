package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.exception.InvalidLoginException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.UserAlreadyExistsException;
import com.github.api_abastecefacil.mapper.UserMapper;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User()
                .setId(1L)
                .setName("Test User")
                .setEmail("user@test.com")
                .setPassword("encodedPassword")
                .setActive(true);
    }

    @Test
    void register_ShouldRegisterUserSuccessfully() {
        RegisterRequest request = new RegisterRequest("Test User", "user@test.com", "password123");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.type()).isEqualTo("Bearer");
        verify(userRepository).save(user);
    }

    @Test
    void register_ShouldThrowUserAlreadyExistsException_WhenEmailExists() {
        RegisterRequest request = new RegisterRequest("Test User", "user@test.com", "password123");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_ShouldAuthenticateAndReturnToken() {
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_ShouldThrowNotFoundException_WhenUserDoesNotExist() {
        LoginRequest request = new LoginRequest("unknown@test.com", "password123");
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authService.login(request));
    }

    @Test
    void login_ShouldThrowInvalidLoginException_WhenUserIsInactive() {
        user.setActive(false);
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(InvalidLoginException.class, () -> authService.login(request));
    }

    @Test
    void login_ShouldThrowInvalidLoginException_WhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest("user@test.com", "wrongpassword");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidLoginException.class, () -> authService.login(request));
    }
}
