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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.github.api_abastecefacil.constants.AuthConstants.*;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;

    public AuthService(
            UserRepository userRepository,
            UserMapper userMapper,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            CustomUserDetailsService customUserDetailsService
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateEmailDoesNotExist(request.email());

        User user = createAndSaveUser(request);
        String token = generateTokenFor(user);

        return createAuthResponse(token, REGISTER_SUCCESS_MESSAGE, user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = findUserByEmailOrThrow(request.email());
        validateUserIsActive(user);
        authenticateUser(request.email(), request.password());
        String token = generateTokenFor(user);
        return createAuthResponse(token, LOGIN_SUCCESS_MESSAGE, user);
    }

    private void validateEmailDoesNotExist(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(USER_ALREADY_EXISTS_MESSAGE);
        }
    }

    private User createAndSaveUser(RegisterRequest request) {
        User user = userMapper.toEntity(request);
        return userRepository.save(user);
    }

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
    }

    private void validateUserIsActive(User user) {
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new InvalidLoginException(USER_INACTIVE_MESSAGE);
        }
    }

    private void authenticateUser(String email, String password) {
        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, password);
            authenticationManager.authenticate(authToken);
        } catch (BadCredentialsException e) {
            throw new InvalidLoginException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    /**
     * O perfil viaja no token como claim para o frontend poder rotear sem uma
     * chamada extra. A expiração continua sendo a global de {@code jwt.expiration}.
     */
    private String generateTokenFor(User user) {
        UserDetails userDetails = customUserDetailsService.toUserDetails(user);
        Map<String, Object> extraClaims = Map.of(PERFIL_CLAIM, user.getPerfil().name());
        return jwtService.generateToken(extraClaims, userDetails);
    }

    private AuthResponse createAuthResponse(String token, String message, User user) {
        return new AuthResponse(token, TOKEN_TYPE, message, user.getPerfil());
    }
}
