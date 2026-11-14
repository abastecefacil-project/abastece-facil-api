package com.github.api_abastecefacil.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class NotificacaoRepositoryTest {

    // Sem banco na suíte: confere o SQL. O tipo vai como literal porque query nativa binda enum pelo ordinal.
    @Test
    void notificarRecusaGravaTipoRecusaNeo() throws NoSuchMethodException {
        String sql = NotificacaoRepository.class.getMethod("notificarRecusa", Long.class, Long.class)
                .getAnnotation(Query.class).value();
        assertThat(sql).contains("INSERT INTO notificacoes_neo (tipo,").contains("SELECT 'RECUSA_NEO',");
    }
}
