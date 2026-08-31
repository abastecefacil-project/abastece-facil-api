package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.github.api_abastecefacil.constants.AuthConstants.USER_NOT_FOUND_BY_EMAIL_MESSAGE;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND_BY_EMAIL_MESSAGE + email));

        return toUserDetails(user);
    }

    /**
     * Único ponto do projeto que constrói as authorities do Spring Security. A
     * authority sai no formato {@code ROLE_<PERFIL>}, vindo de
     * {@link com.github.api_abastecefacil.model.Perfil#authority()}, e por isso deve
     * ser consumida por {@code hasRole("<PERFIL>")} — nunca por
     * {@code hasAuthority("<PERFIL>")}, que exigiria o nome sem prefixo.
     */
    public UserDetails toUserDetails(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                // O construtor do Spring rejeita senha nula (IllegalArgumentException) mas
                // aceita string vazia. Como o projeto nao tem @ExceptionHandler generico,
                // a nula viraria um 500 cru neste caminho, alcancavel pelo
                // JwtAuthenticationFilter e pelo DaoAuthenticationProvider. Com "" o
                // BCrypt nao casa com senha nenhuma: da 401, nunca 500 e nunca acesso.
                user.getPassword() != null ? user.getPassword() : "",
                List.of(new SimpleGrantedAuthority(user.getPerfil().authority()))
        );
    }
}
