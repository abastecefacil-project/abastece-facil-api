package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.LinhaPlanilhaPosto;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

import static com.github.api_abastecefacil.constants.GasStationConstants.GAS_STATION_NOT_FOUND_MESSAGE;

/**
 * Grava um posto da importação, cada um na sua transação curta.
 *
 * <p><b>Bean separado de propósito.</b> O {@code ExecutorImportacaoPostos} chama estes métodos
 * pelo proxy, então o {@code @Transactional} vale. Se fossem métodos dele mesmo, a
 * auto-invocação contornaria o proxy e não haveria transação nenhuma. Pelo mesmo motivo, a
 * geocodificação acontece <b>antes</b>, no executor, e nunca segura uma conexão do pool
 * durante a espera do throttle.
 *
 * <p><b>Nulo nunca sobrescreve</b>, a mesma regra do {@code PlanejadorImportacaoPostos}: vazio
 * na exportação é ausência de informação, não remoção. O CNPJ é gravado mascarado, como vem
 * da linha.
 *
 * <p><b>Três métodos de atualização em vez de parâmetros.</b> Cada nome diz quais colunas o
 * método toca:
 * <ul>
 *   <li>{@link #atualizar}: todos os campos, coordenadas mantidas. É o caso em que o
 *       planejador concluiu que o lugar não mudou;</li>
 *   <li>{@link #atualizarComCoordenadas}: todos os campos, mais as coordenadas novas;</li>
 *   <li>{@link #atualizarSemEndereco}: só os campos que não são de endereço, para quando a
 *       geocodificação de um endereço alterado falhou.</li>
 * </ul>
 * Um {@code boolean aplicarEndereco} com coordenadas anuláveis permitiria justamente a
 * combinação que o terceiro método existe para impedir: endereço novo com coordenada antiga,
 * que deixaria o marcador no lugar errado em silêncio.
 */
@Service
public class GravadorImportacaoPostos {

    private final GasStationRepository gasStationRepository;

    public GravadorImportacaoPostos(GasStationRepository gasStationRepository) {
        this.gasStationRepository = gasStationRepository;
    }

    /** O {@code @PrePersist} da entidade define {@code createdAt} e {@code isActive = true}. */
    @Transactional
    public void inserir(LinhaPlanilhaPosto linha, Coordinates coordenadas) {
        GasStation posto = new GasStation();
        aplicarCamposNaoEndereco(posto, linha);
        aplicarEndereco(posto, linha);
        posto.setLatitude(coordenadas.latitude());
        posto.setLongitude(coordenadas.longitude());
        gasStationRepository.save(posto);
    }

    /** Todos os campos não nulos; latitude e longitude ficam como estão. */
    @Transactional
    public void atualizar(Long id, LinhaPlanilhaPosto linha, boolean reativar) {
        GasStation posto = buscar(id);
        aplicarCamposNaoEndereco(posto, linha);
        aplicarEndereco(posto, linha);
        salvar(posto, reativar);
    }

    @Transactional
    public void atualizarComCoordenadas(Long id, LinhaPlanilhaPosto linha, Coordinates coordenadas, boolean reativar) {
        GasStation posto = buscar(id);
        aplicarCamposNaoEndereco(posto, linha);
        aplicarEndereco(posto, linha);
        posto.setLatitude(coordenadas.latitude());
        posto.setLongitude(coordenadas.longitude());
        salvar(posto, reativar);
    }

    /**
     * Só razão social, nome fantasia, telefone, horário e CNPJ. Endereço, bairro, cidade, UF,
     * CEP e coordenadas ficam como estão no banco, e por isso a diferença de endereço
     * reaparece na próxima importação, que tenta geocodificar de novo.
     */
    @Transactional
    public void atualizarSemEndereco(Long id, LinhaPlanilhaPosto linha, boolean reativar) {
        GasStation posto = buscar(id);
        aplicarCamposNaoEndereco(posto, linha);
        salvar(posto, reativar);
    }

    /** Desativação lógica. Nunca {@code delete}: o posto continua no banco. */
    @Transactional
    public void desativar(Long id) {
        GasStation posto = buscar(id);
        posto.setActive(false);
        gasStationRepository.save(posto);
    }

    private GasStation buscar(Long id) {
        return gasStationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(GAS_STATION_NOT_FOUND_MESSAGE));
    }

    private void salvar(GasStation posto, boolean reativar) {
        if (reativar) {
            posto.setActive(true);
        }
        gasStationRepository.save(posto);
    }

    private static void aplicarCamposNaoEndereco(GasStation posto, LinhaPlanilhaPosto linha) {
        seNaoNulo(linha.name(), posto::setName);
        seNaoNulo(linha.fantasyName(), posto::setFantasyName);
        seNaoNulo(linha.phone(), posto::setPhone);
        seNaoNulo(linha.businessHours(), posto::setBusinessHours);
        seNaoNulo(linha.cnpj(), posto::setCnpj);
    }

    private static void aplicarEndereco(GasStation posto, LinhaPlanilhaPosto linha) {
        seNaoNulo(linha.cep(), posto::setCep);
        seNaoNulo(linha.address(), posto::setAddress);
        seNaoNulo(linha.district(), posto::setDistrict);
        seNaoNulo(linha.city(), posto::setCity);
        seNaoNulo(linha.state(), posto::setState);
    }

    private static void seNaoNulo(String valor, Consumer<String> setter) {
        if (valor != null) {
            setter.accept(valor);
        }
    }
}
