package com.github.api_abastecefacil.dto.user;

import com.github.api_abastecefacil.dto.regional.RegionalSummaryResponse;
import com.github.api_abastecefacil.model.Perfil;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Perfil perfil,
        RegionalSummaryResponse regional,
        String telefone,
        String matricula,
        Boolean senhaDefinida,
        /**
         * Resultado do envio do convite de ativação, e <b>não</b> um atributo do usuário.
         *
         * <p>Só é preenchido em {@code POST /api/users} e em
         * {@code POST /api/users/{id}/reenviar-ativacao}. Em <b>qualquer GET vem
         * {@code null}</b>, porque ali a pergunta não se aplica: a resposta descreve o
         * usuário, não uma tentativa de envio.
         *
         * <p><b>Não trate {@code null} como falha.</b> Só {@code false} significa que o
         * e-mail não saiu e o convite precisa ser reenviado.
         */
        Boolean conviteEnviado
) {
}
