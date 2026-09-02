package com.github.api_abastecefacil.dto.email;

import com.github.api_abastecefacil.model.FinalidadeToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConteudoEmailTest {

    private static final String NOME = "Rafaela Mendes";
    private static final String URL = "https://app.abastecefacil.com.br/definir-senha?token=abc123";

    private static MensagemAcesso mensagem(FinalidadeToken finalidade, long validadeHoras) {
        return new MensagemAcesso("rafaela@abastecefacil.com", NOME, URL, finalidade, validadeHoras);
    }

    @Test
    void de_ShouldProduceDifferentSubject_ForEachFinalidade() {
        ConteudoEmail ativacao = ConteudoEmail.de(mensagem(FinalidadeToken.ATIVACAO, 48));
        ConteudoEmail recuperacao = ConteudoEmail.de(mensagem(FinalidadeToken.RECUPERACAO, 1));

        assertThat(ativacao.assunto()).isNotEqualTo(recuperacao.assunto());
    }

    @Test
    void de_ShouldProduceDifferentBodies_ForEachFinalidade() {
        ConteudoEmail ativacao = ConteudoEmail.de(mensagem(FinalidadeToken.ATIVACAO, 48));
        ConteudoEmail recuperacao = ConteudoEmail.de(mensagem(FinalidadeToken.RECUPERACAO, 1));

        assertThat(ativacao.corpoHtml()).isNotEqualTo(recuperacao.corpoHtml());
        assertThat(ativacao.corpoTexto()).isNotEqualTo(recuperacao.corpoTexto());
    }

    @Test
    void de_ShouldSpeakOfFirstAccess_WhenFinalidadeIsAtivacao() {
        ConteudoEmail conteudo = ConteudoEmail.de(mensagem(FinalidadeToken.ATIVACAO, 48));

        assertThat(conteudo.assunto()).containsIgnoringCase("primeiro acesso");
        assertThat(conteudo.corpoTexto()).containsIgnoringCase("primeiro acesso");
        // Ativacao nao fala em redefinir: a conta ainda nao tem senha nenhuma.
        assertThat(conteudo.corpoTexto()).doesNotContainIgnoringCase("redefinir a senha");
    }

    @Test
    void de_ShouldSpeakOfPasswordReset_WhenFinalidadeIsRecuperacao() {
        ConteudoEmail conteudo = ConteudoEmail.de(mensagem(FinalidadeToken.RECUPERACAO, 1));

        assertThat(conteudo.assunto()).containsIgnoringCase("redefini");
        assertThat(conteudo.corpoTexto()).containsIgnoringCase("redefinir a senha");
    }

    @Test
    void de_ShouldDisplayTheValidityFromTheParameter_ForBothFinalidades() {
        ConteudoEmail ativacao = ConteudoEmail.de(mensagem(FinalidadeToken.ATIVACAO, 48));
        ConteudoEmail recuperacao = ConteudoEmail.de(mensagem(FinalidadeToken.RECUPERACAO, 1));

        assertThat(ativacao.corpoHtml()).contains("48 horas");
        assertThat(ativacao.corpoTexto()).contains("48 horas");

        // "1 hora", nao "1 horas": a recuperacao usa exatamente esse prazo por padrao.
        assertThat(recuperacao.corpoHtml()).contains("1 hora");
        assertThat(recuperacao.corpoTexto()).contains("1 hora");
        assertThat(recuperacao.corpoTexto()).doesNotContain("1 horas");
    }

    @Test
    void de_ShouldDisplayAnArbitraryValidity_ProvingItIsNotAConstant() {
        // O ponto do teste: 72 nao e o TTL de nenhuma finalidade. Se o texto trouxesse
        // uma constante, ou reusasse o prazo default, este numero nao apareceria. Os TTLs
        // sao configuraveis desde o M2 e o e-mail nao pode divergir do prazo real.
        ConteudoEmail ativacao = ConteudoEmail.de(mensagem(FinalidadeToken.ATIVACAO, 72));
        ConteudoEmail recuperacao = ConteudoEmail.de(mensagem(FinalidadeToken.RECUPERACAO, 6));

        assertThat(ativacao.corpoTexto()).contains("72 horas").doesNotContain("48");
        assertThat(recuperacao.corpoTexto()).contains("6 horas").doesNotContain("1 hora ");
    }

    @Test
    void de_ShouldInterpolateRecipientNameAndUrl_InBothBodies() {
        for (FinalidadeToken finalidade : FinalidadeToken.values()) {
            ConteudoEmail conteudo = ConteudoEmail.de(mensagem(finalidade, 24));

            assertThat(conteudo.corpoHtml()).contains(NOME).contains(URL);
            assertThat(conteudo.corpoTexto()).contains(NOME).contains(URL);
        }
    }

    @Test
    void de_ShouldWarnAboutSingleUse_InEveryBody() {
        for (FinalidadeToken finalidade : FinalidadeToken.values()) {
            ConteudoEmail conteudo = ConteudoEmail.de(mensagem(finalidade, 24));

            assertThat(conteudo.corpoHtml()).containsIgnoringCase("uma única vez");
            assertThat(conteudo.corpoTexto()).containsIgnoringCase("uma única vez");
        }
    }

    @Test
    void de_ShouldTellTheReaderToIgnoreIfNotRequested_InEveryBody() {
        for (FinalidadeToken finalidade : FinalidadeToken.values()) {
            ConteudoEmail conteudo = ConteudoEmail.de(mensagem(finalidade, 24));

            assertThat(conteudo.corpoHtml()).containsIgnoringCase("ignore esta mensagem");
            assertThat(conteudo.corpoTexto()).containsIgnoringCase("ignore esta mensagem");
        }
    }

    @Test
    void de_ShouldRenderTheUrlTwiceInHtml_ForClientsThatDoNotRenderLinks() {
        // Uma vez no href e uma vez visivel, para quem le num cliente que nao renderiza
        // HTML ou nao deixa clicar.
        ConteudoEmail conteudo = ConteudoEmail.de(mensagem(FinalidadeToken.ATIVACAO, 48));

        assertThat(conteudo.corpoHtml().split(java.util.regex.Pattern.quote(URL), -1)).hasSize(3);
    }
}
