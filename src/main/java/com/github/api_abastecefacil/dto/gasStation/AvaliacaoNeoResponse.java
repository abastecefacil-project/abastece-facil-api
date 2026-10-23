package com.github.api_abastecefacil.dto.gasStation;

import java.time.LocalDateTime;

public record AvaliacaoNeoResponse(Boolean minhaResposta, long passaram, long naoPassaram,
                                   LocalDateTime ultimoRelatoEm) {}
