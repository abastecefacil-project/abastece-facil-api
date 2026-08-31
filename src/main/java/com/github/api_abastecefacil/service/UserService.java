package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.dto.user.UpdateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.exception.UserAlreadyDeletedException;
import com.github.api_abastecefacil.exception.UserAlreadyExistsException;
import com.github.api_abastecefacil.mapper.UserMapper;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.github.api_abastecefacil.constants.UserConstants.*;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(RegisterRequest request) {
        validateEmailDoesNotExist(request.email());
        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = findUserByIdOrThrow(userId);
        updateNameIfProvided(user, request.name());
        updateEmailIfProvided(user, request.email());
        updatePasswordIfProvided(user, request.password());
        User updatedUser = userRepository.save(user);
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findUserByIdOrThrow(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    public UserResponse getUserById(Long userId) {
        User user = findUserByIdOrThrow(userId);
        return userMapper.toResponse(user);
    }

    public Page<UserResponse> getAllUsers(Boolean active, String name, Pageable pageable) {
        Page<User> usersPage = userRepository.findByIsActiveAndNameContainingIgnoreCase(active, name, pageable);
        return usersPage.map(userMapper::toResponse);
    }

    public Long countAllActiveUsers() {
        return userRepository.countByIsActiveTrue();
    }

    private User findUserByIdOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
        validateUserIsActive(user);
        return user;
    }

    private void validateUserIsActive(User user) {
        if (!user.getActive()) {
            throw new UserAlreadyDeletedException(USER_ALREADY_DELETED_MESSAGE);
        }
    }

    private void validateEmailDoesNotExist(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(EMAIL_ALREADY_EXISTS_MESSAGE);
        }
    }

    private void updateNameIfProvided(User user, String name) {
        if (isNotBlank(name)) {
            user.setName(name);
        }
    }

    private void updateEmailIfProvided(User user, String email) {
        if (isBlank(email)) {
            return;
        }
        validateEmailDoesNotExist(email);
        user.setEmail(email);
    }

    private void updatePasswordIfProvided(User user, String password) {
        if (isNotBlank(password)) {
            String encodedPassword = passwordEncoder.encode(password);
            user.setPassword(encodedPassword);
            // Mantem o invariante password != null <=> senhaDefinida. Sem isto, um
            // usuario criado sem senha continuaria sem conseguir logar depois de
            // receber uma.
            user.setSenhaDefinida(true);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}