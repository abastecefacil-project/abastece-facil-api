package com.github.api_abastecefacil.constants;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Padrões e formatos da normalização da planilha de postos credenciados.
 *
 * <p>Os formatos de saída reproduzem o que o sistema já grava pela interface: CNPJ igual
 * ao {@code formatCnpj} do {@code PostoDialog.vue} e ao seed do {@code dump.sql}, CEP e
 * telefone iguais às máscaras do mesmo formulário, e horário no {@code "HH:mm - HH:mm"}
 * que a tela de postos separa pelo hífen.
 */
public final class PlanilhaPostosConstants {

    private PlanilhaPostosConstants() {
        throw new UnsupportedOperationException("Esta é uma classe utilitária e não pode ser instanciada");
    }

    public static final Pattern NAO_DIGITO = Pattern.compile("\\D");

    /**
     * Espaço, tab, quebra de linha e o espaço não separável (U+00A0), comum em célula de
     * planilha. O {@code \s} do Java não cobre o U+00A0, e o {@code trim()} também não.
     */
    public static final Pattern ESPACOS = Pattern.compile("[\\s\\u00A0]+");

    public static final Pattern CNPJ_DIGITOS = Pattern.compile("^\\d{14}$");

    public static final int CNPJ_TAMANHO = 14;
    public static final int CEP_TAMANHO = 8;
    public static final int TELEFONE_FIXO_TAMANHO = 10;
    public static final int TELEFONE_CELULAR_TAMANHO = 11;

    public static final String CNPJ_FORMATO = "%s.%s.%s/%s-%s";
    public static final String CEP_FORMATO = "%s-%s";
    public static final String TELEFONE_FORMATO = "(%s) %s-%s";

    /** "S N", "SN", "S/N", "S.N", sem diferenciar caixa. */
    public static final Pattern SEM_NUMERO = Pattern.compile("^S\\s*[/.]?\\s*N$", Pattern.CASE_INSENSITIVE);

    public static final String SEM_NUMERO_PADRAO = "S/N";

    /**
     * Horário explícito, em três formas alternativas:
     *
     * <ol>
     *   <li>{@code H:MM} ou {@code HH:MM}, com sufixo opcional ({@code 6:30hs},
     *       {@code 22:00 horas}) — grupos 1 e 2. O sufixo é consumido para {@code 30hs} não
     *       ser lido de novo como {@code 30h};</li>
     *   <li>{@code 7h30} — grupos 3 e 4. Sem esta forma, a terceira leria {@code 07:00} e
     *       descartaria o {@code 30} em silêncio;</li>
     *   <li>{@code 7h}, {@code 22hs}, {@code 24hrs}, {@code 22horas}, com o sufixo colado
     *       ao número — grupo 5.</li>
     * </ol>
     *
     * <p>Número sem {@code :} e sem {@code h} não é horário: {@code 2ª} e {@code 6ª} ficam
     * de fora.
     */
    public static final Pattern HORARIO_TOKEN = Pattern.compile(
            "(?<![\\d:])(?:"
                    + "(\\d{1,2}):(\\d{2})(?!\\d)(?:\\s*h(?:oras?|rs?|s)?)?"
                    + "|(\\d{1,2})h(\\d{2})(?!\\d)"
                    + "|(\\d{1,2})h(?:oras?|rs?|s)?(?![\\p{L}\\d])"
                    + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * "24" seguido de h, hs, hr, hrs ou horas, com ou sem espaço. Só é procurado no trecho
     * que sobrou depois de retirados os horários de {@link #HORARIO_TOKEN} — é o
     * indicador "livre" de 24 horas, como em {@code "24 HORAS"}.
     */
    public static final Pattern INDICADOR_24H = Pattern.compile(
            "(?<![\\d:])24\\s*h(?:oras?|rs?|s)?(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static final int HORA_24 = 24;
    public static final String HORA_FORMATO = "%02d:%02d";
    public static final String INTERVALO_FORMATO = "%s - %s";

    /** 24:00 não é horário válido no {@code input type="time"} do formulário. */
    public static final String FIM_DO_DIA = "23:59";
    public static final String INTERVALO_24H = "00:00 - " + FIM_DO_DIA;

    // ------------------------------------------------------------------ leitura

    public static final String COLUNA_NOME_FANTASIA = "Nome Fantasia";
    public static final String COLUNA_RAZAO_SOCIAL = "Razão Social";
    public static final String COLUNA_CNPJ = "CNPJ";
    public static final String COLUNA_CEP = "CEP";
    public static final String COLUNA_LOGRADOURO = "Logradouro";
    public static final String COLUNA_ENDERECO = "Endereço";
    public static final String COLUNA_NUMERO = "Número";
    public static final String COLUNA_BAIRRO = "Bairro";
    public static final String COLUNA_UF = "UF";
    public static final String COLUNA_CIDADE = "Cidade";
    public static final String COLUNA_TELEFONE = "Telefone";
    public static final String COLUNA_TIPO = "Tipo";
    public static final String COLUNA_HORARIO = "Horario Funcionamento";

    /** Na ordem em que são citadas nas mensagens. */
    public static final List<String> COLUNAS_OBRIGATORIAS = List.of(
            COLUNA_NOME_FANTASIA, COLUNA_RAZAO_SOCIAL, COLUNA_CNPJ, COLUNA_CEP,
            COLUNA_LOGRADOURO, COLUNA_ENDERECO, COLUNA_NUMERO, COLUNA_BAIRRO, COLUNA_UF,
            COLUNA_CIDADE, COLUNA_TELEFONE, COLUNA_TIPO, COLUNA_HORARIO);

    /**
     * Uma linha que casa ao menos metade das colunas obrigatórias é tratada como cabeçalho
     * incompleto, e a mensagem lista o que falta. Abaixo disso, a planilha não tem
     * cabeçalho reconhecível.
     */
    public static final int MINIMO_COLUNAS_CABECALHO_PARCIAL = (COLUNAS_OBRIGATORIAS.size() + 1) / 2;

    /** Marcas diacríticas que sobram da decomposição NFD — os acentos. */
    public static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    public static final String FORMATO_GERAL = "General";

    public static final String PLANILHA_INVALIDA_MESSAGE =
            "O arquivo enviado não é uma planilha .xlsx válida. "
                    + "Se estiver no formato .xls, abra no Excel e salve como .xlsx.";

    public static final String CABECALHO_NAO_ENCONTRADO_MESSAGE =
            "Cabeçalho não encontrado na primeira aba da planilha. Colunas obrigatórias: "
                    + String.join(", ", COLUNAS_OBRIGATORIAS) + ".";

    public static final String COLUNAS_AUSENTES_MESSAGE =
            "Colunas obrigatórias ausentes no cabeçalho da linha %d: %s.";

    public static final String CNPJ_INVALIDO_MESSAGE = "CNPJ inválido (deve ter 14 dígitos)";
    public static final String CEP_INVALIDO_MESSAGE = "CEP inválido (deve ter 8 dígitos)";
    public static final String CAMPO_VAZIO_MESSAGE = "%s vazio";
    public static final String CAMPO_VAZIA_MESSAGE = "%s vazia";

    public static final String CNPJ_DUPLICADO_MESSAGE = "CNPJ duplicado; mantida a linha %d";

    // -------------------------------------------------------------- planejamento

    /** Nome do campo em {@code camposAlterados}; os demais reusam os nomes das colunas. */
    public static final String CAMPO_HORARIO = "Horário";

    public static final String PLANILHA_SEM_POSTOS_NO_ESCOPO_MESSAGE =
            "Nenhuma linha válida no escopo da importação (%d lidas, %d no escopo, %d com erro). "
                    + "Nenhum posto foi alterado.";

    public static final String CNPJ_DUPLICADO_BANCO_MESSAGE =
            "CNPJ cadastrado em %d postos; mantido o id %d, os demais ativos serão desativados";

    // ------------------------------------------------------------------ execução

    /**
     * Linha usada nas ocorrências de itens de desativar, que não vêm da planilha: são postos
     * do banco ausentes dela. O Excel numera a partir de 1, então 0 nunca colide com uma
     * linha real, e o campo continua {@code int} no record e número no JSON.
     */
    public static final int LINHA_FORA_DA_PLANILHA = 0;

    public static final String THREAD_IMPORTACAO_PREFIXO = "importacao-postos-";
    public static final int SEGUNDOS_ESPERA_DESLIGAMENTO = 10;

    public static final String IMPORTACAO_EM_ANDAMENTO_MESSAGE =
            "Já existe uma importação de postos em andamento (id %s). Aguarde a conclusão para iniciar outra.";
    public static final String IMPORTACAO_NAO_ENCONTRADA_MESSAGE =
            "Importação de postos não encontrada. Registros finalizados são descartados após 24 horas.";
    public static final String IMPORTACAO_NAO_EM_ANDAMENTO_MESSAGE =
            "A importação de postos já foi finalizada (status %s) e não pode ser cancelada.";

    public static final String COORDENADAS_NAO_ENCONTRADAS_INSERIR_MESSAGE =
            "Endereço não localizado pelo serviço de geocodificação; posto não inserido";
    public static final String COORDENADAS_NAO_ENCONTRADAS_ATUALIZAR_MESSAGE =
            "Endereço alterado na planilha não foi localizado; endereço e coordenadas mantidos";
    public static final String FALHA_GEOCODIFICACAO_INSERIR_MESSAGE =
            "Falha de comunicação com o serviço de geocodificação; posto não inserido";
    public static final String FALHA_GEOCODIFICACAO_ATUALIZAR_MESSAGE =
            "Falha de comunicação com o serviço de geocodificação; endereço e coordenadas mantidos";
    public static final String CONFLITO_GRAVACAO_MESSAGE =
            "Não foi possível gravar o posto: conflito com outro posto cadastrado (por exemplo, o mesmo CNPJ)";
    public static final String ERRO_GRAVACAO_MESSAGE =
            "Não foi possível gravar o posto por um erro inesperado";

    public static final String IMPORTACAO_CONCLUIDA_MESSAGE = "Importação concluída.";
    public static final String IMPORTACAO_INTERROMPIDA_FALHAS_MESSAGE =
            "Importação interrompida: o serviço de geocodificação está indisponível ou bloqueando as "
                    + "requisições (%d falhas consecutivas). %d de %d itens processados; o que já foi gravado "
                    + "permanece e nenhum posto foi desativado.";
    /** Só para log: estruturada, só no fallback, não localizados, resultados rejeitados pela UF. */
    public static final String METRICAS_GEOCODIFICACAO_FORMAT =
            "geocodificação: %d na consulta estruturada, %d só no fallback, %d não localizados, "
                    + "%d resultados rejeitados pela UF";
    public static final String IMPORTACAO_CANCELADA_MESSAGE =
            "Importação cancelada após %d de %d itens; o que já foi gravado permanece e nenhum posto foi "
                    + "desativado.";
    public static final String IMPORTACAO_INTERROMPIDA_ERRO_MESSAGE =
            "Importação interrompida por erro inesperado. %d de %d itens processados; o que já foi gravado "
                    + "permanece e nenhum posto foi desativado.";

    // ------------------------------------------------------------------ upload

    public static final String EXTENSAO_XLSX = ".xlsx";
    public static final String ARQUIVO_VAZIO_MESSAGE = "O arquivo enviado está vazio.";
    public static final String ARQUIVO_ILEGIVEL_MESSAGE =
            "Não foi possível ler o arquivo enviado. Tente enviá-lo novamente.";
    public static final String ARQUIVO_OBRIGATORIO_MESSAGE =
            "Envie a planilha na parte 'arquivo' da requisição.";
    public static final String MULTIPART_OBRIGATORIO_MESSAGE =
            "Envie a planilha como multipart/form-data, na parte 'arquivo'";
    public static final String ARQUIVO_MUITO_GRANDE_MESSAGE =
            "O arquivo excede o tamanho máximo permitido de %s.";
    public static final String ARQUIVO_MUITO_GRANDE_SEM_LIMITE_MESSAGE =
            "O arquivo excede o tamanho máximo permitido.";
}
