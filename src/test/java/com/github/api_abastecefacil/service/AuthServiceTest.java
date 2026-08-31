package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.AuthResponse;
import com.github.api_abastecefacil.dto.auth.LoginRequest;
import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.exception.InvalidLoginException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.PasswordNotSetException;
import com.github.api_abastecefacil.exception.UserAlreadyExistsException;
import com.github.api_abastecefacil.mapper.UserMapper;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> claimsCaptor;

    private User user;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        user = new User()
                .setId(1L)
                .setName("Test User")
                .setEmail("user@test.com")
                .setPassword("encodedPassword")
                .setActive(true)
                .setPerfil(Perfil.COLABORADOR)
                .setSenhaDefinida(true);

        userDetails = new org.springframework.security.core.userdetails.User(
                "user@test.com",
                "encodedPassword",
                List.of(new SimpleGrantedAuthority("ROLE_COLABORADOR"))
        );
    }

    @Test
    void register_ShouldRegisterUserSuccessfully() {
        RegisterRequest request = new RegisterRequest("Test User", "user@test.com", "password123");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.type()).isEqualTo("Bearer");
        assertThat(response.perfil()).isEqualTo(Perfil.COLABORADOR);
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
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.perfil()).isEqualTo(Perfil.COLABORADOR);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_ShouldIncludePerfilClaimInToken() {
        user.setPerfil(Perfil.ADMINISTRADOR);
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(customUserDetailsService.toUserDetails(user)).thenReturn(userDetails);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-token");

        authService.login(request);

        verify(jwtService).generateToken(claimsCaptor.capture(), any(UserDetails.class));
        assertThat(claimsCaptor.getValue()).containsEntry("perfil", "ADMINISTRADOR");
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
    void login_ShouldThrowPasswordNotSetException_WhenSenhaNaoDefinida() {
        user.setSenhaDefinida(false);
        LoginRequest request = new LoginRequest("user@test.com", "qualquerSenha123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(PasswordNotSetException.class, () -> authService.login(request));

        // A restricao central do S1: rejeitar antes de qualquer chamada ao PasswordEncoder.
        verify(authenticationManager, never()).authenticate(any());
        verify(jwtService, never()).generateToken(anyMap(), any(UserDetails.class));
    }

    @Test
    void login_ShouldThrowPasswordNotSetException_WhenPasswordIsNull() {
        user.setPassword(null).setSenhaDefinida(true);
        LoginRequest request = new LoginRequest("user@test.com", "qualquerSenha123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(PasswordNotSetException.class, () -> authService.login(request));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_ShouldThrowInvalidLoginException_WhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest("user@test.com", "wrongpassword");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidLoginException.class, () -> authService.login(request));
    }
}
