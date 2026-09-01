package com.github.api_abastecefacil.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static com.github.api_abastecefacil.constants.AdministradorInicialConstants.NOME_PADRAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministradorInicialInitializerTest {

    private static final String NOME = "Administradora Geral";
    private static final String EMAIL = "admin.a3@abastecefacil.com";
    private static final String HASH = "$2a$10$E2UPv7arXmp3q0LzV5v1beRnH51/G51fJtYjW.u2FpYxJ9QjCj7.y";

    @Mock
    private UserRepository userRepository;

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        // Captura o que a classe loga, para provar que nenhum segredo escapa.
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AdministradorInicialInitializer.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    // @InjectMocks nao serve: o construtor recebe tres String de configuracao.
    private AdministradorInicialInitializer initializer(String nome, String email, String senhaHash) {
        return new AdministradorInicialInitializer(userRepository, nome, email, senhaHash);
    }

    @Test
    void run_ShouldCreateAdministrador_WhenEmailAndHashAreConfigured() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        initializer(NOME, EMAIL, HASH).run();

        User salvo = capturarSalvo();
        assertThat(salvo.getEmail()).isEqualTo(EMAIL);
        assertThat(salvo.getName()).isEqualTo(NOME);
        assertThat(salvo.getPerfil()).isEqualTo(Perfil.ADMINISTRADOR);
        assertThat(salvo.getActive()).isTrue();
        assertThat(salvo.getSenhaDefinida()).isTrue();
        assertThat(salvo.getRegional()).isNull();
        assertThat(salvo.getMatricula()).isNull();
    }

    @Test
    void run_ShouldStoreTheConfiguredHashVerbatim() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        initializer(NOME, EMAIL, HASH).run();

        // Identico ao configurado: nada de passar por PasswordEncoder, que produziria o
        // BCrypt de um BCrypt e faria o login falhar sem explicacao.
        assertThat(capturarSalvo().getPassword()).isEqualTo(HASH);
    }

    @Test
    void run_ShouldUseDefaultName_WhenNameIsBlank() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        initializer("   ", EMAIL, HASH).run();

        // users.name e NOT NULL: sem default, o insert quebraria.
        assertThat(capturarSalvo().getName()).isEqualTo(NOME_PADRAO);
    }

    @Test
    void run_ShouldSkip_WhenEmailIsBlank() {
        assertThatCode(() -> initializer(NOME, "", HASH).run()).doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void run_ShouldSkip_WhenHashIsBlank() {
        assertThatCode(() -> initializer(NOME, EMAIL, "").run()).doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_ShouldSkip_WhenHashIsNotBcrypt() {
        assertThatCode(() -> initializer(NOME, EMAIL, "senha-em-texto-plano").run())
                .doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
        assertThat(mensagens(Level.ERROR)).isNotEmpty();
    }

    @Test
    void run_ShouldSkip_WhenHashIsTruncated() {
        // Prefixo certo, comprimento errado -- o caso do copiar e colar incompleto, que
        // passaria por uma checagem so de prefixo e falharia apenas no login.
        assertThatCode(() -> initializer(NOME, EMAIL, "$2a$10$E2UPv7arXmp3q0LzV5v1be").run())
                .doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_ShouldAcceptAllBcryptPrefixes() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        for (String prefixo : new String[]{"$2a$", "$2b$", "$2y$"}) {
            String hash = prefixo + HASH.substring(4);
            initializer(NOME, EMAIL, hash).run();
        }

        verify(userRepository, times(3)).save(any(User.class));
    }

    @Test
    void run_ShouldReportFailureSummary_WhenHashIsInvalid() {
        AdministradorInicialInitializer inicializador = initializer(NOME, EMAIL, "nao-e-hash");

        inicializador.run();
        inicializador.resumirFalha();

        // Dois ERROR: o detalhado no ponto da falha e o resumo, que o
        // ApplicationReadyEvent emite como ultima linha da subida.
        assertThat(mensagens(Level.ERROR)).hasSize(2);
    }

    @Test
    void run_ShouldNotReportFailureSummary_WhenConfigurationIsAbsent() {
        AdministradorInicialInitializer inicializador = initializer(NOME, "", "");

        inicializador.run();
        inicializador.resumirFalha();

        // Ausencia de configuracao e o caminho normal em desenvolvimento, nao falha.
        assertThat(mensagens(Level.ERROR)).isEmpty();
    }

    @Test
    void run_ShouldSkip_WhenEmailAlreadyExistsAsAdministrador() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(new User().setEmail(EMAIL).setPerfil(Perfil.ADMINISTRADOR)));

        initializer(NOME, EMAIL, HASH).run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_ShouldWarnAndChangeNothing_WhenEmailBelongsToAnotherPerfil() {
        // O caso admin@abastecefacil.com do init-scripts/dump.sql.
        User doDump = new User().setEmail(EMAIL).setPerfil(Perfil.COLABORADOR).setPassword("hash-antigo");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(doDump));

        initializer(NOME, EMAIL, HASH).run();

        verify(userRepository, never()).save(any());
        assertThat(doDump.getPerfil()).isEqualTo(Perfil.COLABORADOR);
        assertThat(doDump.getPassword()).isEqualTo("hash-antigo");
        assertThat(mensagens(Level.WARN)).isNotEmpty();
    }

    @Test
    void run_ShouldNotFailStartup_WhenSaveViolatesUniqueConstraint() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk6dotkott2kjsp8vw4d0m25fb7"));

        // Um CommandLineRunner que lanca derruba a subida. A corrida entre instancias
        // nao pode virar outage.
        assertThatCode(() -> initializer(NOME, EMAIL, HASH).run()).doesNotThrowAnyException();
    }

    @Test
    void run_ShouldNeverLogTheConfiguredHash() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        initializer(NOME, EMAIL, HASH).run();

        assertThat(todasAsMensagens()).noneMatch(m -> m.contains(HASH));
    }

    @Test
    void run_ShouldNeverLogTheRejectedValue_WhenHashIsInvalid() {
        // O pior caso: alguem colou a senha em texto plano em vez do hash. Loga-la
        // transformaria um erro de configuracao em vazamento.
        String senhaColadaPorEngano = "Senha-Real-Do-Admin-42";

        initializer(NOME, EMAIL, senhaColadaPorEngano).run();

        assertThat(todasAsMensagens()).noneMatch(m -> m.contains(senhaColadaPorEngano));
    }

    private User capturarSalvo() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private java.util.List<String> mensagens(Level nivel) {
        return appender.list.stream()
                .filter(e -> e.getLevel().equals(nivel))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private java.util.List<String> todasAsMensagens() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
