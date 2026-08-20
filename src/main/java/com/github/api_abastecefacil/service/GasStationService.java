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

import static com.github.api_abastecefacil.constants.GasStationConstants.*;

@Service
@Transactional(readOnly = true)
public class GasStationService {

    private final GasStationRepository gasStationRepository;
    private final OpenStreetMapService openStreetMapService;
    private final GasStationMapper gasStationMapper;

    public GasStationService(
            GasStationRepository gasStationRepository,
            OpenStreetMapService openStreetMapService,
            GasStationMapper gasStationMapper
    ) {
        this.gasStationRepository = gasStationRepository;
        this.openStreetMapService = openStreetMapService;
        this.gasStationMapper = gasStationMapper;
    }

    @Transactional
    public GasStationResponse create(CreateGasStationRequest request) {
        validateCnpjDoesNotExist(request.cnpj());
        Coordinates coordinates = fetchCoordinatesFromAddress(
                request.address(),
                request.district(),
                request.city(),
                request.state(),
                request.cep()
        );
        GasStation gasStation = createGasStationEntity(request, coordinates);
        GasStation savedGasStation = gasStationRepository.save(gasStation);
        return gasStationMapper.toResponse(savedGasStation);
    }

    @Transactional
    public GasStationResponse update(Long id, UpdateGasStationRequest request) {
        GasStation gasStation = findGasStationByIdOrThrow(id);
        validateCnpjNotUsedByAnotherGasStation(gasStation, request.cnpj());
        updateGasStationBasicFields(gasStation, request);
        updateGasStationCoordinates(gasStation, request);
        GasStation updatedGasStation = gasStationRepository.save(gasStation);
        return gasStationMapper.toResponse(updatedGasStation);
    }

    @Transactional
    public void deleteGasStation(Long id) {
        GasStation gasStation = findGasStationByIdOrThrow(id);
        gasStationRepository.delete(gasStation);
    }

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

    private Coordinates fetchCoordinatesFromAddress(String address, String district, String city, String state, String cep) {
        String addressQuery = buildAddressQuery(address, district, city, state, cep);

        return openStreetMapService.getCoordinates(addressQuery)
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

    private void updateGasStationCoordinates(GasStation gasStation, UpdateGasStationRequest request) {
        Coordinates coordinates = fetchCoordinatesFromAddress(
                request.address(),
                request.district(),
                request.city(),
                request.state(),
                request.cep()
        );

        gasStation.setLatitude(coordinates.latitude());
        gasStation.setLongitude(coordinates.longitude());
    }

    private String buildAddressQuery(String address, String district, String city, String state, String cep) {
        return String.format(ADDRESS_FORMAT, address, district, city, state, cep);
    }
}