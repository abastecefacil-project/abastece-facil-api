package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.email.ConteudoEmail;
import com.github.api_abastecefacil.dto.email.MensagemAcesso;
import com.github.api_abastecefacil.dto.email.ResendEmailRequest;
import com.github.api_abastecefacil.dto.email.ResendEmailResponse;
import com.github.api_abastecefacil.exception.EnvioEmailException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.github.api_abastecefacil.constants.EmailConstants.*;

/**
 * Envio real, pela API HTTP do Resend.
 *
 * <p><b>Sem SDK.</b> É um único {@code POST /emails} com corpo JSON, e o
 * {@link RestClient} do Spring já faz isso — uma dependência a mais para uma chamada só
 * não se paga, e a stack do projeto é enxuta de propósito.
 *
 * <p><b>Primeiro uso de {@code RestClient} no projeto.</b> As duas integrações
 * anteriores (ViaCEP e Nominatim) usam OpenFeign, com a requisição declarada numa
 * interface em {@code client/}. Aqui o cliente é imperativo porque a autenticação é um
 * header fixo montado a partir de configuração e porque o tratamento de erro precisa ser
 * específico: o {@code handleFeignException} genérico devolveria a mensagem da exceção
 * do Feign ao cliente, e ela carrega trechos da resposta do provedor.
 *
 * <p><b>O construtor recebe o {@code RestClient.Builder} em vez de criar o seu.</b> É o
 * que permite ao teste chamar {@code MockRestServiceServer.bindTo(builder)} e exercitar
 * o adaptador sem rede e sem subir contexto Spring, mantendo a suíte no estilo Mockito
 * puro do resto do projeto.
 *
 * <p><b>Nada aqui loga a URL nem a chave.</b> A URL contém o token em claro e só o
 * {@link EnviadorEmailLog} pode registrá-la; a chave não pode aparecer em lugar nenhum.
 * O log de falha também omite o corpo da resposta, que pode ecoar configuração.
 */
public class ResendEnviadorEmail implements EnviadorEmail {

    private static final Logger log = LoggerFactory.getLogger(ResendEnviadorEmail.class);

    private final RestClient restClient;
    private final String remetente;

    public ResendEnviadorEmail(RestClient.Builder restClientBuilder, String apiUrl, String apiKey, String remetente) {
        this.restClient = restClientBuilder
                .baseUrl(apiUrl)
                // A chave vive so aqui, como header default. Nao e guardada em campo para
                // que nenhum log, toString ou mensagem de erro possa alcanca-la por
                // engano.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.remetente = remetente;
    }

    @Override
    public void enviar(MensagemAcesso mensagem) {
        ConteudoEmail conteudo = ConteudoEmail.de(mensagem);
        ResendEmailRequest payload = ResendEmailRequest.de(remetente, mensagem.destinatario(), conteudo);

        ResendEmailResponse resposta;
        try {
            resposta = restClient.post()
                    .uri(RESEND_PATH_EMAILS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    // Sem esta conversao o RestClient lancaria HttpClientErrorException, que
                    // escaparia crua ate o BasicErrorController: o GlobalExceptionHandler nao
                    // tem fallback registrado, entao viraria um 500 sem ErrorResponse.
                    .onStatus(HttpStatusCode::isError, (requisicao, respostaHttp) -> {
                        log.error(
                                RESEND_FALHA_HTTP_MESSAGE,
                                mensagem.destinatario(),
                                mensagem.finalidade(),
                                respostaHttp.getStatusCode().value()
                        );
                        throw new EnvioEmailException(ENVIO_FALHOU_MESSAGE);
                    })
                    .body(ResendEmailResponse.class);
        } catch (RestClientException e) {
            // Falha de transporte: DNS, timeout, conexao recusada, TLS. A causa e
            // encadeada para o stack trace do log; a mensagem que chega ao cliente
            // continua sendo a constante generica.
            log.error(RESEND_FALHA_REDE_MESSAGE, mensagem.destinatario(), mensagem.finalidade(), e);
            throw new EnvioEmailException(ENVIO_FALHOU_MESSAGE, e);
        }

        log.info(
                RESEND_ENVIADO_MESSAGE,
                mensagem.destinatario(),
                mensagem.finalidade(),
                resposta == null ? null : resposta.id()
        );
    }
}
