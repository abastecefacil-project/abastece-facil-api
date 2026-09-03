package com.github.api_abastecefacil.mapper;

import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.dto.regional.RegionalSummaryResponse;
import com.github.api_abastecefacil.dto.user.CreateUserRequest;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void toResponse_ShouldLeaveConviteEnviadoNull_OnReads() {
        // Em GET a pergunta nao se aplica: a resposta descreve o usuario, nao uma
        // tentativa de envio. null NAO significa falha -- so false significa.
        assertThat(userMapper.toResponse(user).conviteEnviado()).isNull();
    }

    @Test
    void toResponse_ShouldCarryConviteEnviado_WhenProvided() {
        assertThat(userMapper.toResponse(user, true).conviteEnviado()).isTrue();
        assertThat(userMapper.toResponse(user, false).conviteEnviado()).isFalse();
    }

    // ---------- toEntity(CreateUserRequest, Regional) — cadastro administrativo ----------

    private CreateUserRequest pedido(Perfil perfil) {
        return new CreateUserRequest(
                "Nova Colaboradora", "nova@fiesc.org.br", "(47) 99999-8888", "12345", perfil, 1L);
    }

    @Test
    void toEntityFromCreateUserRequest_ShouldCreateUserWithoutPassword() {
        // A regra central do S2a: nenhum administrador escolhe a senha de outra pessoa.
        // O usuario nasce sem senha e a define pelo convite do S2b1.
        User entity = userMapper.toEntity(pedido(Perfil.COLABORADOR), regional);

        assertThat(entity.getPassword()).isNull();
        assertThat(entity.getSenhaDefinida()).isFalse();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void toEntityFromCreateUserRequest_ShouldCreateActiveUser() {
        // Ativo, apenas sem senha. Quem barra o login nesse intervalo e o
        // AuthService.validateSenhaDefinida, com 401 PASSWORD_NOT_SET.
        User entity = userMapper.toEntity(pedido(Perfil.COLABORADOR), regional);

        assertThat(entity.getActive()).isTrue();
    }

    @Test
    void toEntityFromCreateUserRequest_ShouldTakeThePerfilFromTheRequest() {
        // Ao contrario do registro publico, que crava COLABORADOR.
        assertThat(userMapper.toEntity(pedido(Perfil.GESTOR_FROTA), regional).getPerfil())
                .isEqualTo(Perfil.GESTOR_FROTA);
        assertThat(userMapper.toEntity(pedido(Perfil.ADMINISTRADOR), regional).getPerfil())
                .isEqualTo(Perfil.ADMINISTRADOR);
    }

    @Test
    void toEntityFromCreateUserRequest_ShouldFillIdentityFields() {
        User entity = userMapper.toEntity(pedido(Perfil.COLABORADOR), regional);

        assertThat(entity.getName()).isEqualTo("Nova Colaboradora");
        assertThat(entity.getEmail()).isEqualTo("nova@fiesc.org.br");
        assertThat(entity.getMatricula()).isEqualTo("12345");
        assertThat(entity.getRegional()).isSameAs(regional);
        // Sem normalizar aqui: quem tira a mascara e o @PrePersist da entidade.
        assertThat(entity.getTelefone()).isEqualTo("(47) 99999-8888");
    }

    @Test
    void toEntityFromCreateUserRequest_ShouldAcceptNullRegional() {
        // Administrador pode nao pertencer a regional nenhuma, como o inicial do A3.
        CreateUserRequest request = new CreateUserRequest(
                "Admin", "admin@fiesc.org.br", null, null, Perfil.ADMINISTRADOR, null);

        User entity = userMapper.toEntity(request, null);

        assertThat(entity.getRegional()).isNull();
        assertThat(entity.getMatricula()).isNull();
    }
}
