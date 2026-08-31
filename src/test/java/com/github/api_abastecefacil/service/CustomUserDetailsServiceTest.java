package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User()
                .setId(1L)
                .setName("Test User")
                .setEmail("user@test.com")
                .setPassword("encodedPassword")
                .setActive(true)
                .setPerfil(Perfil.COLABORADOR);
    }

    @Test
    void loadUserByUsername_ShouldGrantRolePrefixedAuthority_ForColaborador() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("user@test.com");

        assertThat(userDetails.getUsername()).isEqualTo("user@test.com");
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_COLABORADOR");
    }

    @Test
    void loadUserByUsername_ShouldGrantRolePrefixedAuthority_ForAdministrador() {
        user.setPerfil(Perfil.ADMINISTRADOR);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("user@test.com");

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMINISTRADOR");
    }

    /**
     * Trava contra 500: o construtor do UserDetails do Spring rejeita senha nula, e o
     * projeto nao tem @ExceptionHandler generico, entao a IllegalArgumentException
     * viraria um 500 cru a cada requisicao autenticada de um usuario sem senha.
     */
    @Test
    void loadUserByUsername_ShouldUseEmptyPassword_WhenPasswordIsNull() {
        user.setPassword(null).setSenhaDefinida(false);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("user@test.com");

        assertThat(userDetails.getPassword()).isEmpty();
        assertThat(userDetails.getUsername()).isEqualTo("user@test.com");
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_COLABORADOR");
    }

    @Test
    void loadUserByUsername_ShouldThrowUsernameNotFoundException_WhenEmailDoesNotExist() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("unknown@test.com")
        );
    }
}
