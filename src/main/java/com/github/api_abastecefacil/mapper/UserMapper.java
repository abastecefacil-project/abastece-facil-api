package com.github.api_abastecefacil.mapper;


import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final PasswordEncoder passwordEncoder;
    private final RegionalMapper regionalMapper;

    public UserMapper(PasswordEncoder passwordEncoder, RegionalMapper regionalMapper) {
        this.passwordEncoder = passwordEncoder;
        this.regionalMapper = regionalMapper;
    }

    public User toEntity(RegisterRequest request) {
        return new User()
                .setName(request.name())
                .setEmail(request.email())
                .setPassword(passwordEncoder.encode(request.password()))
                .setPerfil(Perfil.COLABORADOR);
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getPerfil(),
                regionalMapper.toSummaryResponse(user.getRegional())
        );
    }


}
