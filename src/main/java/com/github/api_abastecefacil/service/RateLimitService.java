package com.github.api_abastecefacil.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.api_abastecefacil.constants.RateLimitConstants.*;

/**
 * Limite de requisições por janela deslizante, em memória.
 *
 * <p><b>Por que não Redis, nem Caffeine, nem Bucket4j.</b> O projeto não tem nenhum
 * cache — não há {@code @EnableCaching}, {@code CacheManager} nem
 * {@code spring-boot-starter-cache} no {@code pom.xml} — e a stack é enxuta de propósito.
 * Um {@code ConcurrentHashMap} de deques resolve o problema inteiro em algumas dezenas de
 * linhas, sem dependência nova e, principalmente, <b>testável pela suíte que existe</b>:
 * nenhum teste do projeto sobe contexto Spring, então uma solução baseada em
 * {@code @Cacheable} ficaria sem cobertura nenhuma justamente numa regra de segurança.
 *
 * <p><b>O {@link Clock} é injetado, e não é detalhe.</b> Com {@code Instant.now()} fixo no
 * código, provar que a janela de quinze minutos desliza exigiria dormir quinze minutos.
 * Com o relógio injetado, o teste o avança artificialmente e a mesma lógica é exercitada
 * em milissegundos.
 *
 * <p><b>Limitação conhecida: o estado é por instância.</b> Com mais de um nó da aplicação
 * atrás de um balanceador, cada um conta o seu próprio, e o limite efetivo é multiplicado
 * pelo número de nós. É aceitável para o alvo deste sistema, que roda num container só, e
 * é o ponto que exigiria um contador compartilhado se isso mudar. O mesmo vale para
 * reinício: os contadores começam zerados.
 *
 * <p>A política — quantos, em quanto tempo — <b>não mora aqui</b>. Este serviço recebe
 * chave, limite e janela; quem compõe as regras da recuperação de senha é o
 * {@code AuthService}, com as constantes de {@code RateLimitConstants}.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final Clock clock;

    /**
     * Uma fila de instantes por chave, do mais antigo para o mais recente. Os deques são
     * pequenos por construção — nunca passam do limite mais um —, então varrer é barato.
     */
    private final Map<String, Deque<Instant>> registros = new ConcurrentHashMap<>();

    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Diz se a chave já atingiu o limite dentro da janela, <b>sem registrar nada</b>.
     *
     * <p>Consultar e registrar são métodos separados de propósito: quem aplica duas
     * regras ao mesmo tempo — a recuperação aplica uma por e-mail e outra por IP —
     * precisa conferir as duas antes de gravar qualquer uma. Se {@code excedeu} também
     * registrasse, uma solicitação recusada pelo limite de IP teria consumido cota do
     * e-mail no caminho, e o usuário legítimo pagaria pelo vizinho de rede.
     */
    public boolean excedeu(String chave, int limite, Duration janela) {
        Deque<Instant> instantes = registros.get(chave);

        if (instantes == null) {
            return false;
        }

        synchronized (instantes) {
            descartarAnterioresA(instantes, clock.instant().minus(janela));
            return instantes.size() >= limite;
        }
    }

    /**
     * Contabiliza uma requisição para a chave.
     *
     * <p>A poda acontece na consulta, não aqui: a fila de uma chave viva é sempre
     * pequena, e podar na escrita exigiria conhecer a janela — que é do chamador, não
     * deste método.
     */
    public void registrar(String chave) {
        Deque<Instant> instantes = registros.computeIfAbsent(chave, k -> new ArrayDeque<>());

        synchronized (instantes) {
            instantes.addLast(clock.instant());
        }
    }

    /**
     * Remove as chaves sem nenhum registro dentro da maior janela em uso.
     *
     * <p>Uma chave consultada com frequência se poda sozinha em {@link #excedeu}. Esta
     * varredura existe para as <b>abandonadas</b>: o e-mail que pediu recuperação uma vez
     * e nunca mais voltou continuaria ocupando uma entrada para sempre, e num sistema que
     * fica meses no ar isso é um vazamento de memória lento.
     *
     * <p>O {@code @Scheduled} fica no serviço que possui o dado, e não numa classe de
     * configuração — é exatamente a convenção que o M2 fixou com
     * {@code TokenAcessoService.limparTokensExpirados}. O {@code SchedulingConfig} só
     * carrega o {@code @EnableScheduling}.
     */
    @Scheduled(cron = CRON_LIMPEZA_RATE_LIMIT)
    public void limpar() {
        Instant limite = clock.instant().minus(JANELA_MAXIMA);
        int antes = registros.size();

        registros.values().removeIf(instantes -> {
            synchronized (instantes) {
                descartarAnterioresA(instantes, limite);
                return instantes.isEmpty();
            }
        });

        int removidas = antes - registros.size();

        if (removidas > 0) {
            // Apenas a contagem: as chaves carregam e-mail e IP e nao vao para o log.
            log.info(LIMPEZA_LOG, removidas);
        }
    }

    /**
     * Descarta o que caiu fora da janela. Como a fila está em ordem cronológica, parar no
     * primeiro instante que sobrevive basta — não é preciso varrer o resto.
     */
    private static void descartarAnterioresA(Deque<Instant> instantes, Instant limite) {
        while (!instantes.isEmpty() && !instantes.peekFirst().isAfter(limite)) {
            instantes.removeFirst();
        }
    }
}
