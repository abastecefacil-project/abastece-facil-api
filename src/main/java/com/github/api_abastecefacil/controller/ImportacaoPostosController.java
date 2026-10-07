package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.gasStation.ImportacaoIniciadaResponse;
import com.github.api_abastecefacil.dto.gasStation.ImportacaoPostosStatus;
import com.github.api_abastecefacil.dto.gasStation.PreviaImportacaoResponse;
import com.github.api_abastecefacil.service.ImportacaoPostosService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * Importação da planilha de postos. Tudo fica sob {@code /api/gas-stations/**}, que o
 * {@code SecurityConfig} exige autenticado; a restrição a ADMINISTRADOR fica na
 * {@link ImportacaoPostosService}.
 *
 * <p>{@code /import/atual} vence {@code /import/{id}} porque o {@code PathPattern} do Spring
 * ordena segmento literal acima de segmento com variável, seja qual for a ordem de
 * declaração — o mesmo que faz {@code /api/users/me} vencer {@code /{userId}}. O método
 * fica declarado antes só por legibilidade.
 *
 * <p><b>Sem {@code consumes = multipart/form-data}, de propósito.</b> Com ele, uma requisição
 * JSON seria recusada antes do handler com {@code HttpMediaTypeNotSupportedException}, que
 * não tem handler e cairia no 403 vazio do {@code /error}. Sem ele, o
 * {@code @RequestPart} lança {@code MultipartException}, e o {@code GlobalExceptionHandler}
 * responde 400 {@code ARQUIVO_OBRIGATORIO} dizendo como enviar.
 */
@RestController
@RequestMapping("/api/gas-stations/import")
public class ImportacaoPostosController {

    private final ImportacaoPostosService importacaoPostosService;

    public ImportacaoPostosController(ImportacaoPostosService importacaoPostosService) {
        this.importacaoPostosService = importacaoPostosService;
    }

    @PostMapping("/preview")
    public ResponseEntity<PreviaImportacaoResponse> previa(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importacaoPostosService.previa(arquivo));
    }

    @PostMapping
    public ResponseEntity<ImportacaoIniciadaResponse> iniciar(@RequestPart("arquivo") MultipartFile arquivo) {
        UUID id = importacaoPostosService.iniciar(arquivo);
        URI location = URI.create("/api/gas-stations/import/" + id);
        return ResponseEntity.accepted().location(location).body(new ImportacaoIniciadaResponse(id));
    }

    @GetMapping("/atual")
    public ResponseEntity<ImportacaoPostosStatus> atual() {
        return importacaoPostosService.atual()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImportacaoPostosStatus> consultar(@PathVariable String id) {
        return ResponseEntity.ok(importacaoPostosService.consultar(id));
    }

    /**
     * 202 e não 200: o pedido foi registrado, mas a importação só para no próximo ponto de
     * verificação do executor. O corpo é o status naquele instante, ainda {@code EM_ANDAMENTO};
     * o desfecho sai no {@code GET /{id}}.
     */
    @PostMapping("/{id}/cancelamento")
    public ResponseEntity<ImportacaoPostosStatus> cancelar(@PathVariable String id) {
        return ResponseEntity.accepted().body(importacaoPostosService.cancelar(id));
    }
}
