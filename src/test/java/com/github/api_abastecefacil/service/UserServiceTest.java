package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.dto.user.UpdateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.UserAlreadyDeletedException;
import com.github.api_abastecefacil.exception.UserAlreadyExistsException;
import com.github.api_abastecefacil.mapper.UserMapper;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        user = new User()
                .setId(1L)
                .setName("User One")
                .setEmail("one@test.com")
                .setPassword("pass123")
                .setActive(true)
                .setCreatedAt(LocalDateTime.now())
                .setPerfil(Perfil.COLABORADOR)
                .setSenhaDefinida(true);

        userResponse = new UserResponse(
                1L, "User One", "one@test.com", true, LocalDateTime.now(), null,
                Perfil.COLABORADOR, null, "47999998888", "12345", true);
    }

    @Test
    void createUser_ShouldCreateUserSuccessfully() {
        RegisterRequest request = new RegisterRequest("User One", "one@test.com", "pass123");
        when(userRepository.existsByEmail("one@test.com")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.createUser(request);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("one@test.com");
        verify(userRepository).save(user);
    }

    @Test
    void createUser_ShouldThrowUserAlreadyExistsException_WhenEmailExists() {
        RegisterRequest request = new RegisterRequest("User One", "one@test.com", "pass123");
        when(userRepository.existsByEmail("one@test.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_ShouldReturnUser_WhenUserExistsAndIsActive() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.getUserById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getUserById_ShouldThrowNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void getUserById_ShouldThrowUserAlreadyDeletedException_WhenUserIsInactive() {
        user.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(UserAlreadyDeletedException.class, () -> userService.getUserById(1L));
    }

    @Test
    void updateUser_ShouldUpdateUserSuccessfully() {
        UpdateUserRequest request = new UpdateUserRequest("Updated Name", "new@test.com", "newpass");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("encodedNewPass");
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.updateUser(1L, request);

        assertThat(response).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_ShouldDeactivateUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        assertThat(user.getActive()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void getAllUsers_ShouldReturnPagedUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user));

        when(userRepository.findByIsActiveAndNameContainingIgnoreCase(true, "User", pageable)).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers(true, "User", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void countAllActiveUsers_ShouldReturnCount() {
        when(userRepository.countByIsActiveTrue()).thenReturn(5L);

        Long count = userService.countAllActiveUsers();

        assertThat(count).isEqualTo(5L);
    }
}
