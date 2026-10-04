package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.ItemPlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.dto.gasStation.OcorrenciaPlanilha;
import com.github.api_abastecefacil.dto.gasStation.PlanoImportacao;
import com.github.api_abastecefacil.dto.gasStation.ResultadoLeituraPlanilha;
import com.github.api_abastecefacil.exception.PlanilhaSemPostosNoEscopoException;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.repository.GasStationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.github.api_abastecefacil.constants.PlanilhaPostosConstants.*;
import static com.github.api_abastecefacil.validation.NormalizadorPlanilhaPostos.chaveComparacao;
import static com.github.api_abastecefacil.validation.NormalizadorPlanilhaPostos.cnpjDigitos;
import static com.github.api_abastecefacil.validation.NormalizadorPlanilhaPostos.cnpjFormatado;

/**
 * Calcula o plano de sincronização entre a planilha lida e o cadastro de postos. Não grava
 * nada e não geocodifica: o mesmo plano serve à prévia e à execução.
 *
 * <p><b>Correspondência por CNPJ em dígitos, dos dois lados.</b> O backend grava o CNPJ
 * como recebe, então o banco tem postos com e sem máscara.
 *
 * <p><b>Classificação de cada linha válida:</b>
 * <ul>
 *   <li>INSERIR — CNPJ inexistente no banco;</li>
 *   <li>REATIVAR — existe com {@code isActive = false}, com ou sem campo alterado;</li>
 *   <li>ATUALIZAR — existe ativo e ao menos um campo difere;</li>
 *   <li>sem alteração — existe ativo e nada difere; só entra na contagem.</li>
 * </ul>
 *
 * <p><b>Valor nulo na planilha nunca sobrescreve o banco nem conta como diferença</b>, em
 * nenhum campo — na prática Nome Fantasia, Telefone e Horário, porque os obrigatórios nulos
 * já viram erro de linha no leitor. Vazio na exportação é ausência de informação na fonte,
 * não remoção, e a regra protege o que o administrador completou à mão.
 *
 * <p><b>Diferença de texto e necessidade de geocodificar são perguntas diferentes.</b> Os
 * campos são comparados exatamente, e qualquer diferença — até só de caixa ou acento —
 * entra em {@code camposAlterados} e atualiza o texto. Mas a geocodificação só é pedida se
 * o CEP mudar nos dígitos, ou Endereço, Bairro, Cidade ou UF mudarem depois de ignorar
 * caixa, acento e espaços: {@code "Rua X, 1"} para {@code "RUA X, 1"} é o mesmo lugar e não
 * justifica uma chamada ao Nominatim. O CEP é comparado só pelos dígitos em tudo, então um
 * posto gravado sem hífen não aparece como alterado.
 *
 * <p><b>Desativação.</b> Vão para desativar os postos ativos cujo CNPJ, em dígitos, não
 * está entre os presentes na planilha. Presentes são os das linhas válidas <b>e</b> os das
 * linhas com erro que têm CNPJ válido: uma linha com defeito não atualiza o posto, mas o
 * posto continua credenciado. Posto com CNPJ que não tem 14 dígitos não casa com linha
 * nenhuma e é desativado — é o que "sincronizar" significa, e a prévia o mostra antes.
 *
 * <p><b>CNPJ duplicado no banco.</b> A unicidade é sobre o texto gravado, então
 * {@code 12345678000126} e {@code 12.345.678/0001-26} convivem. Fica um posto só, escolhido
 * nesta ordem: o que já grava o CNPJ mascarado — assim atualizar o CNPJ nunca colide com o
 * texto do outro na constraint única —, depois o ativo, depois o de menor id. Os demais
 * ativos vão para desativar, e sai um aviso.
 *
 * <p>Sem autorização aqui: ela acontece no serviço de entrada, na thread da requisição.
 */
@Service
@Transactional(readOnly = true)
public class PlanejadorImportacaoPostos {

    private final GasStationRepository gasStationRepository;

    public PlanejadorImportacaoPostos(GasStationRepository gasStationRepository) {
        this.gasStationRepository = gasStationRepository;
    }

    /**
     * @throws PlanilhaSemPostosNoEscopoException se não houver nenhuma linha válida — antes
     *                                            de consultar o banco
     */
    public PlanoImportacao planejar(ResultadoLeituraPlanilha leitura) {
        if (leitura.linhasValidas().isEmpty()) {
            throw new PlanilhaSemPostosNoEscopoException(String.format(PLANILHA_SEM_POSTOS_NO_ESCOPO_MESSAGE,
                    leitura.totalLinhasLidas(), leitura.totalNoEscopo(), leitura.linhasComErro().size()));
        }

        List<GasStation> postos = new ArrayList<>(gasStationRepository.findAll());
        postos.sort(Comparator.comparing(GasStation::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, List<GasStation>> porCnpj = new LinkedHashMap<>();
        for (GasStation posto : postos) {
            String digitos = cnpjDigitos(posto.getCnpj());
            if (digitos != null) {
                porCnpj.computeIfAbsent(digitos, chave -> new ArrayList<>()).add(posto);
            }
        }

        List<ItemPlanoImportacao> inserir = new ArrayList<>();
        List<ItemPlanoImportacao> atualizar = new ArrayList<>();
        List<ItemPlanoImportacao> reativar = new ArrayList<>();
        List<OcorrenciaPlanilha> avisos = new ArrayList<>(leitura.avisos());
        Set<String> cnpjsValidos = new HashSet<>();
        Set<Long> mantidos = new HashSet<>();
        int semAlteracao = 0;

        for (LinhaPlanilhaPosto linha : leitura.linhasValidas()) {
            cnpjsValidos.add(linha.cnpjDigitos());
            List<GasStation> candidatos = porCnpj.getOrDefault(linha.cnpjDigitos(), List.of());

            if (candidatos.isEmpty()) {
                inserir.add(new ItemPlanoImportacao(null, linha.cnpj(), linha.name(), linha.fantasyName(),
                        linha.city(), List.of(), true, linha));
                continue;
            }

            GasStation posto = escolherMantido(candidatos, linha.cnpj());
            mantidos.add(posto.getId());
            if (candidatos.size() > 1) {
                avisos.add(new OcorrenciaPlanilha(linha.numeroLinha(), linha.cnpj(),
                        String.format(CNPJ_DUPLICADO_BANCO_MESSAGE, candidatos.size(), posto.getId())));
            }

            List<String> campos = camposAlterados(posto, linha);
            ItemPlanoImportacao item = new ItemPlanoImportacao(posto.getId(), linha.cnpj(), linha.name(),
                    nomeFantasiaFinal(posto, linha), linha.city(), campos, requerGeocodificacao(posto, linha), linha);

            if (!ativo(posto)) {
                reativar.add(item);
            } else if (campos.isEmpty()) {
                semAlteracao++;
            } else {
                atualizar.add(item);
            }
        }

        Set<String> cnpjsPresentes = new HashSet<>(cnpjsValidos);
        leitura.linhasComErro().stream()
                .map(LinhaPlanilhaPosto::cnpjDigitos)
                .filter(Objects::nonNull)
                .forEach(cnpjsPresentes::add);

        List<ItemPlanoImportacao> desativar = new ArrayList<>();
        for (GasStation posto : postos) {
            if (!ativo(posto)) {
                continue;
            }
            String digitos = cnpjDigitos(posto.getCnpj());
            boolean ausente = digitos == null || !cnpjsPresentes.contains(digitos);
            boolean duplicadoPreterido = digitos != null && cnpjsValidos.contains(digitos)
                    && !mantidos.contains(posto.getId());
            if (ausente || duplicadoPreterido) {
                String cnpj = digitos == null ? posto.getCnpj() : cnpjFormatado(digitos);
                desativar.add(new ItemPlanoImportacao(posto.getId(), cnpj, posto.getName(), posto.getFantasyName(),
                        posto.getCity(), List.of(), false, null));
            }
        }

        List<OcorrenciaPlanilha> erros = leitura.linhasComErro().stream()
                .flatMap(linha -> linha.erros().stream()
                        .map(mensagem -> new OcorrenciaPlanilha(linha.numeroLinha(), linha.cnpj(), mensagem)))
                .toList();

        int totalAtivosNoBanco = (int) postos.stream().filter(PlanejadorImportacaoPostos::ativo).count();

        return new PlanoImportacao(
                List.copyOf(inserir),
                List.copyOf(atualizar),
                List.copyOf(reativar),
                List.copyOf(desativar),
                semAlteracao,
                leitura.totalLinhasLidas(),
                leitura.totalNoEscopo(),
                totalAtivosNoBanco,
                erros,
                List.copyOf(avisos));
    }

    /** Mascarado primeiro, depois ativo, depois menor id. Ver javadoc da classe. */
    private static GasStation escolherMantido(List<GasStation> candidatos, String cnpjMascarado) {
        return candidatos.stream()
                .min(Comparator.comparing((GasStation posto) -> !cnpjMascarado.equals(posto.getCnpj()))
                        .thenComparing(posto -> !ativo(posto))
                        .thenComparing(GasStation::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
    }

    /**
     * O nome fantasia que o posto terá depois da importação: o da planilha, ou o do banco
     * quando a planilha não traz — a mesma regra de "nulo nunca sobrescreve" que a gravação
     * aplica. Só para atualizar e reativar; inserir não tem banco, e desativar não tem planilha.
     */
    private static String nomeFantasiaFinal(GasStation posto, LinhaPlanilhaPosto linha) {
        return linha.fantasyName() != null ? linha.fantasyName() : posto.getFantasyName();
    }

    /** Na ordem fixa em que o frontend exibe. */
    private static List<String> camposAlterados(GasStation posto, LinhaPlanilhaPosto linha) {
        List<String> campos = new ArrayList<>();
        adicionarSeDifere(campos, COLUNA_RAZAO_SOCIAL, linha.name(), posto.getName());
        adicionarSeDifere(campos, COLUNA_NOME_FANTASIA, linha.fantasyName(), posto.getFantasyName());
        adicionarSeDifere(campos, COLUNA_TELEFONE, linha.phone(), posto.getPhone());
        if (cepDifere(posto, linha)) {
            campos.add(COLUNA_CEP);
        }
        adicionarSeDifere(campos, COLUNA_ENDERECO, linha.address(), posto.getAddress());
        adicionarSeDifere(campos, COLUNA_BAIRRO, linha.district(), posto.getDistrict());
        adicionarSeDifere(campos, COLUNA_CIDADE, linha.city(), posto.getCity());
        adicionarSeDifere(campos, COLUNA_UF, linha.state(), posto.getState());
        adicionarSeDifere(campos, CAMPO_HORARIO, linha.businessHours(), posto.getBusinessHours());
        adicionarSeDifere(campos, COLUNA_CNPJ, linha.cnpj(), posto.getCnpj());
        return List.copyOf(campos);
    }

    /** Nulo na planilha nunca é diferença. Ver javadoc da classe. */
    private static void adicionarSeDifere(List<String> campos, String nome, String planilha, String banco) {
        if (planilha != null && !planilha.equals(banco)) {
            campos.add(nome);
        }
    }

    private static boolean cepDifere(GasStation posto, LinhaPlanilhaPosto linha) {
        return linha.cep() != null && !digitos(linha.cep()).equals(digitos(posto.getCep()));
    }

    private static boolean requerGeocodificacao(GasStation posto, LinhaPlanilhaPosto linha) {
        return cepDifere(posto, linha)
                || lugarDifere(linha.address(), posto.getAddress())
                || lugarDifere(linha.district(), posto.getDistrict())
                || lugarDifere(linha.city(), posto.getCity())
                || lugarDifere(linha.state(), posto.getState());
    }

    /** Sem caixa, sem acento, espaços colapsados — a mesma chave do cabeçalho da planilha. */
    private static boolean lugarDifere(String planilha, String banco) {
        return planilha != null && !Objects.equals(chaveComparacao(planilha), chaveComparacao(banco));
    }

    private static String digitos(String valor) {
        return valor == null ? "" : NAO_DIGITO.matcher(valor).replaceAll("");
    }

    private static boolean ativo(GasStation posto) {
        return Boolean.TRUE.equals(posto.getActive());
    }
}
