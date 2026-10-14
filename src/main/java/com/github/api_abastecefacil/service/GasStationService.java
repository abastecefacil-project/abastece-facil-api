package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.gasStation.CreateGasStationRequest;
import com.github.api_abastecefacil.dto.gasStation.GasStationResponse;
import com.github.api_abastecefacil.dto.gasStation.UpdateGasStationRequest;
import com.github.api_abastecefacil.exception.CoordinatesNotFoundException;
import com.github.api_abastecefacil.exception.GasStationAlreadyExistsException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.GasStationMapper;
import com.github.api_abastecefacil.model.GasStation;
import com.github.api_abastecefacil.repository.GasStationRepository;
import com.github.api_abastecefacil.service.OpenStreetMapService.Coordinates;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import static com.github.api_abastecefacil.constants.GasStationConstants.*;
import static com.github.api_abastecefacil.validation.NormalizadorPlanilhaPostos.chaveComparacao;

@Service
@Transactional(readOnly = true)
public class GasStationService {

    private final GasStationRepository gasStationRepository;
    private final OpenStreetMapService openStreetMapService;
    private final GasStationMapper gasStationMapper;
    private final AutorizacaoOperacional autorizacaoOperacional;

    public GasStationService(
            GasStationRepository gasStationRepository,
            OpenStreetMapService openStreetMapService,
            GasStationMapper gasStationMapper,
            AutorizacaoOperacional autorizacaoOperacional
    ) {
        this.gasStationRepository = gasStationRepository;
        this.openStreetMapService = openStreetMapService;
        this.gasStationMapper = gasStationMapper;
        this.autorizacaoOperacional = autorizacaoOperacional;
    }

    /**
     * Com latitude e longitude no request, usa as informadas e não consulta o Nominatim — é o
     * caminho para o posto que a geocodificação não localiza. Sem elas, geocodifica.
     */
    @Transactional
    public GasStationResponse create(CreateGasStationRequest request) {
        autorizacaoOperacional.autorizarEscrita();
        validateCnpjDoesNotExist(request.cnpj());
        Coordinates coordinates = request.coordenadasInformadas()
                ? informadas(request.latitude(), request.longitude())
                : fetchCoordinatesFromAddress(request.address(), request.city(), request.state());
        GasStation gasStation = createGasStationEntity(request, coordinates);
        GasStation savedGasStation = gasStationRepository.save(gasStation);
        return gasStationMapper.toResponse(savedGasStation);
    }

    /**
     * As coordenadas são resolvidas <b>antes</b> de qualquer campo ser copiado para a entidade:
     * se a geocodificação falhar, a exceção sai com o posto intacto e nada é gravado.
     *
     * <ul>
     *   <li>com latitude e longitude no request: usa as informadas, sem consulta, mesmo com
     *       endereço alterado;</li>
     *   <li>sem elas, e com endereço, cidade ou UF diferentes do banco: geocodifica;</li>
     *   <li>sem elas, e com o lugar igual: mantém as coordenadas atuais, sem consulta.
     *       Mudança só de CEP, bairro, telefone, nome ou horário não toca no Nominatim.</li>
     * </ul>
     */
    @Transactional
    public GasStationResponse update(Long id, UpdateGasStationRequest request) {
        autorizacaoOperacional.autorizarEscrita();
        GasStation gasStation = findGasStationByIdOrThrow(id);
        validateCnpjNotUsedByAnotherGasStation(gasStation, request.cnpj());
        Coordinates coordinates = resolveUpdatedCoordinates(gasStation, request);
        updateGasStationBasicFields(gasStation, request);
        gasStation.setLatitude(coordinates.latitude());
        gasStation.setLongitude(coordinates.longitude());
        GasStation updatedGasStation = gasStationRepository.save(gasStation);
        return gasStationMapper.toResponse(updatedGasStation);
    }

    @Transactional
    public void deleteGasStation(Long id) {
        autorizacaoOperacional.autorizarEscrita();
        GasStation gasStation = findGasStationByIdOrThrow(id);
        gasStationRepository.delete(gasStation);
    }

    /**
     * <b>Sem autorização de propósito.</b> Este método e o
     * {@code getGasStationsByFilters} servem {@code GET /api/public/gas-stations/{id}} e
     * {@code /filter}, que são públicos por contrato — o usuário final consulta os postos
     * sem login. Acrescentar guarda aqui derrubaria a lista e o mapa da área pública.
     */
    public GasStationResponse findById(Long id) {
        GasStation gasStation = findGasStationByIdOrThrow(id);
        return gasStationMapper.toResponse(gasStation);
    }

    public Page<GasStationResponse> getGasStationsByFilters(String search, Boolean active, Pageable pageable) {
        Page<GasStation> stationsPage = gasStationRepository.findByFilters(search, active, pageable);
        return stationsPage.map(gasStationMapper::toResponse);
    }


    private GasStation findGasStationByIdOrThrow(Long id) {
        return gasStationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(GAS_STATION_NOT_FOUND_MESSAGE));
    }

    private void validateCnpjDoesNotExist(String cnpj) {
        if (gasStationRepository.existsByCnpj(cnpj)) {
            throw new GasStationAlreadyExistsException(CNPJ_ALREADY_EXISTS_MESSAGE);
        }
    }

    private void validateCnpjNotUsedByAnotherGasStation(GasStation currentGasStation, String newCnpj) {
        if (gasStationRepository.existsByCnpj(newCnpj) && !currentGasStation.getCnpj().equals(newCnpj)) {
            throw new GasStationAlreadyExistsException(CNPJ_ALREADY_EXISTS_MESSAGE);
        }
    }

    /**
     * A mesma estratégia da importação — busca estruturada, depois texto livre, sem CEP e com
     * a regra de UF —, para que um endereço não localizado lá tenha a mesma chance aqui. Ver
     * {@link OpenStreetMapService#geocodificarComFallback}.
     */
    private Coordinates fetchCoordinatesFromAddress(String address, String city, String state) {
        return openStreetMapService.geocodificarComFallback(address, city, state)
                .coordenadas()
                .orElseThrow(() -> new CoordinatesNotFoundException(COORDINATES_NOT_FOUND_MESSAGE));
    }

    private GasStation createGasStationEntity(CreateGasStationRequest request, Coordinates coordinates) {
        return gasStationMapper.toEntity(
                request,
                coordinates.latitude(),
                coordinates.longitude()
        );
    }

    private void updateGasStationBasicFields(GasStation gasStation, UpdateGasStationRequest request) {
        gasStation.setName(request.name());
        gasStation.setFantasyName(request.fantasyName());
        gasStation.setCnpj(request.cnpj());
        gasStation.setCep(request.cep());
        gasStation.setDistrict(request.district());
        gasStation.setAddress(request.address());
        gasStation.setState(request.state());
        gasStation.setCity(request.city());
        gasStation.setActive(request.isActive());
        gasStation.setPhone(request.phone());
        gasStation.setBusinessHours(request.businessHours());
    }

    private Coordinates resolveUpdatedCoordinates(GasStation gasStation, UpdateGasStationRequest request) {
        if (request.coordenadasInformadas()) {
            return informadas(request.latitude(), request.longitude());
        }
        if (lugarMudou(gasStation, request)) {
            return fetchCoordinatesFromAddress(request.address(), request.city(), request.state());
        }
        return new Coordinates(gasStation.getLatitude(), gasStation.getLongitude());
    }

    /**
     * Só os campos que a geocodificação usa, sem caixa, acento e espaços repetidos — a mesma
     * regra do {@code PlanejadorImportacaoPostos.requerGeocodificacao}. CEP e bairro não
     * entram: nenhuma das consultas os envia, então geocodificar devolveria o mesmo ponto.
     */
    private static boolean lugarMudou(GasStation gasStation, UpdateGasStationRequest request) {
        return difere(request.address(), gasStation.getAddress())
                || difere(request.city(), gasStation.getCity())
                || difere(request.state(), gasStation.getState());
    }

    private static boolean difere(String novo, String atual) {
        return !Objects.equals(chaveComparacao(novo), chaveComparacao(atual));
    }

    /** Arredondada à escala da coluna, para a resposta mostrar o que o banco grava. */
    private static Coordinates informadas(BigDecimal latitude, BigDecimal longitude) {
        return new Coordinates(
                latitude.setScale(ESCALA_COORDENADAS, RoundingMode.HALF_UP),
                longitude.setScale(ESCALA_COORDENADAS, RoundingMode.HALF_UP));
    }
}