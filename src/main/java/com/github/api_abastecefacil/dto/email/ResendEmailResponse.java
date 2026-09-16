package com.github.api_abastecefacil.dto.email;

/**
 * Resposta de sucesso do {@code POST /emails} do Resend.
 *
 * <p>Só o {@code id}, que é o que serve para rastrear a mensagem no painel do Resend
 * quando alguém reclama que não recebeu o convite. Campos que o Resend venha a
 * acrescentar são ignorados: o Spring Boot desliga
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} por padrão, então nenhum campo novo do lado deles
 * quebra o envio aqui.
 */
public record ResendEmailResponse(String id) {
}
