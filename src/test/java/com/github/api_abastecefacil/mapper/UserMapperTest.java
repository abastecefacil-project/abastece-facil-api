package com.github.api_abastecefacil.mapper;

import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.dto.regional.RegionalSummaryResponse;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMapperTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserMapper userMapper;

    private User user;
    private Regional regional;

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 27, 10, 30, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 8, 28, 14, 45, 0);

    @BeforeEach
    void setUp() {
        // RegionalMapper real, nao mockado: e conversao pura, sem colaborador nenhum.
        userMapper = new UserMapper(passwordEncoder, new RegionalMapper());

        regional = new Regional()
                .setId(1L)
                .setNome("Joinville")
                .setSigla("JOI")
                .setAtivo(true)
                .setCreatedAt(CREATED_AT);

        user = new User()
                .setId(7L)
                .setName("Verificacao P02")
                .setEmail("verifica.p02@abastecefacil.com")
                .setPassword("encodedPassword")
                .setActive(true)
                .setCreatedAt(CREATED_AT)
                .setUpdatedAt(UPDATED_AT)
                .setPerfil(Perfil.GESTOR_FROTA)
                .setTelefone("47999998888")
                .setMatricula("12345")
                .setSenhaDefinida(false);
    }

    @Test
    void toResponse_ShouldMapRegionalSummary_WhenUserHasRegional() {
        user.setRegional(regional);

        UserResponse response = userMapper.toResponse(user);

        assertThat(response.regional()).isNotNull();
        assertThat(response.regional().id()).isEqualTo(1L);
        assertThat(response.regional().nome()).isEqualTo("Joinville");
        assertThat(response.regional().sigla()).isEqualTo("JOI");
    }

    @Test
    void toResponse_ShouldReturnNullRegional_WhenUserHasNoRegional() {
        user.setRegional(null);

        UserResponse response = userMapper.toResponse(user);

        assertThat(response).isNotNull();
        assertThat(response.regional()).isNull();
    }

    @Test
    void toResponse_ShouldFillEveryField() {
        user.setRegional(regional);

        UserResponse response = userMapper.toResponse(user);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Verificacao P02");
        assertThat(response.email()).isEqualTo("verifica.p02@abastecefacil.com");
        assertThat(response.isActive()).isTrue();
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(response.perfil()).isEqualTo(Perfil.GESTOR_FROTA);
        assertThat(response.regional()).isEqualTo(new RegionalSummaryResponse(1L, "Joinville", "JOI"));
        assertThat(response.telefone()).isEqualTo("47999998888");
        assertThat(response.matricula()).isEqualTo("12345");
        // Oposto de isActive de proposito: sao os dois unicos Boolean do record, entao
        // troca-los de posicao quebra este teste.
        assertThat(response.senhaDefinida()).isFalse();
    }

    @Test
    void toEntity_ShouldSetPerfilColaboradorAndEncodePassword() {
        RegisterRequest request = new RegisterRequest("Novo Usuario", "novo@test.com", "password123");
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");

        User entity = userMapper.toEntity(request);

        assertThat(entity.getPerfil()).isEqualTo(Perfil.COLABORADOR);
        assertThat(entity.getName()).isEqualTo("Novo Usuario");
        assertThat(entity.getEmail()).isEqualTo("novo@test.com");
        assertThat(entity.getPassword()).isEqualTo("encodedPassword");
        assertThat(entity.getRegional()).isNull();
        assertThat(entity.getSenhaDefinida()).isTrue();
        assertThat(entity.getTelefone()).isNull();
        assertThat(entity.getMatricula()).isNull();
    }
}
