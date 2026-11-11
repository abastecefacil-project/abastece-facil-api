package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.car.CarResponse;
import com.github.api_abastecefacil.dto.car.CreateCarRequest;
import com.github.api_abastecefacil.dto.car.UpdateCarRequest;
import com.github.api_abastecefacil.exception.CarAlreadyExistsException;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.CarMapper;
import com.github.api_abastecefacil.model.Car;
import com.github.api_abastecefacil.model.User;
import com.github.api_abastecefacil.model.Perfil;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.repository.RegionalRepository;
import com.github.api_abastecefacil.exception.RegionalNaoPermitidaException;
import com.github.api_abastecefacil.repository.CarRepository;
import com.github.api_abastecefacil.util.PlanilhaXlsx;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.github.api_abastecefacil.constants.CarConstants.CAR_NOT_FOUND_MESSAGE;
import static com.github.api_abastecefacil.constants.CarConstants.LICENSE_PLATE_ALREADY_EXISTS_MESSAGE;

@Service
@Transactional(readOnly = true)
public class CarService {

    private static final List<String> CABECALHOS_EXPORTACAO =
            List.of("Placa", "Modelo", "Regional", "Situação", "Cadastrado em", "Atualizado em");

    private final CarRepository carRepository;
    private final CarMapper carMapper;
    private final RegionalRepository regionalRepository;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final AutorizacaoOperacional autorizacaoOperacional;

    public CarService(
            CarRepository carRepository,
            CarMapper carMapper,
            AutorizacaoOperacional autorizacaoOperacional,
            RegionalRepository regionalRepository,
            UsuarioAutenticadoProvider usuarioAutenticadoProvider) {
        this.regionalRepository = regionalRepository;
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
        this.carRepository = carRepository;
        this.carMapper = carMapper;
        this.autorizacaoOperacional = autorizacaoOperacional;
    }

    @Transactional
    public CarResponse createCar(CreateCarRequest request) {
        User autor = autorizacaoOperacional.autorizarEscrita();
        validateLicensePlateDoesNotExist(request.licensePlate());
        Car car = carMapper.toEntity(request);
        car.setRegional(resolverRegional(autor, request.regionalId()));
        Car savedCar = carRepository.save(car);
        return carMapper.toResponse(savedCar);
    }

    public CarResponse getCarById(Long carId) {
        User autor = autorizacaoOperacional.autorizarConsulta();
        Car car = findCarByIdOrThrow(carId);
        autorizarVeiculo(autor, car);
        return carMapper.toResponse(car);
    }

    @Transactional
    public CarResponse updateCar(Long carId, UpdateCarRequest request) {
        User autor = autorizacaoOperacional.autorizarEscrita();
        Car car = findCarByIdOrThrow(carId);
        autorizarVeiculo(autor, car);
        if (request.regionalId() != null) {
            car.setRegional(resolverRegional(autor, request.regionalId()));
        }
        updateLicensePlateIfProvided(car, carId, request.licensePlate());
        updateModelIfProvided(car, request.model());
        updateActiveStatusIfProvided(car, request.isActive());
        Car updatedCar = carRepository.save(car);
        return carMapper.toResponse(updatedCar);
    }

    @Transactional
    public void deleteCar(Long carId) {
        User autor = autorizacaoOperacional.autorizarEscrita();
        Car car = findCarByIdOrThrow(carId);
        autorizarVeiculo(autor, car);
        carRepository.delete(car);
    }

    // Todos os perfis autenticados podem selecionar um veículo ativo ao registrar ocorrência.
    public List<CarResponse> getAvailableCars() {
        User autor = usuarioAutenticadoProvider.obterUsuarioAutenticado();
        return carRepository.findByFilters(null, true, regionalDaConsulta(autor), Pageable.unpaged())
                .map(carMapper::toResponse).getContent();
    }

    public Page<CarResponse> getCarsByFilters(String search, Boolean active, Pageable pageable) {
        User autor = autorizacaoOperacional.autorizarConsulta();
        Page<Car> carsPage = carRepository.findByFilters(search, active, regionalDaConsulta(autor), pageable);
        return carsPage.map(carMapper::toResponse);
    }

    /**
     * Mesmos filtros, guarda e escopo de regional da listagem, sem paginação. A regional é LAZY e
     * é lida dentro desta transação: o contexto de persistência carrega cada regional uma vez.
     */
    public byte[] exportarVeiculos(String search, Boolean active) {
        User autor = autorizacaoOperacional.autorizarConsulta();
        List<List<Object>> linhas = carRepository
                .findByFilters(search, active, regionalDaConsulta(autor), Pageable.unpaged())
                .map(car -> Arrays.<Object>asList(
                        car.getLicensePlate(),
                        car.getModel(),
                        car.getRegional() == null ? null : car.getRegional().getNome(),
                        car.getActive(),
                        car.getCreatedAt(),
                        car.getUpdatedAt()))
                .getContent();
        return PlanilhaXlsx.gerar(CABECALHOS_EXPORTACAO, linhas);
    }

    /** Gestor só vê a própria regional; administrador vê todas ({@code null}). */
    private Long regionalDaConsulta(User autor) {
        return autor.getPerfil() == Perfil.GESTOR_FROTA ? regionalDoGestor(autor).getId() : null;
    }

    private Regional regionalDoGestor(User autor) {
        if (autor.getRegional() == null || autor.getRegional().getId() == null) {
            throw new RegionalNaoPermitidaException("Sua conta não possui regional para gerenciar veículos.");
        }
        return autor.getRegional();
    }

    private void autorizarVeiculo(User autor, Car car) {
        if (autor.getPerfil() == Perfil.GESTOR_FROTA
                && (car.getRegional() == null
                || !Objects.equals(regionalDoGestor(autor).getId(), car.getRegional().getId()))) {
            throw new RegionalNaoPermitidaException("Você só pode acessar veículos da sua regional.");
        }
    }

    private Regional resolverRegional(User autor, Long regionalId) {
        if (autor.getPerfil() == Perfil.GESTOR_FROTA) {
            Regional regional = regionalDoGestor(autor);
            if (regionalId != null && !regionalId.equals(regional.getId())) {
                throw new RegionalNaoPermitidaException("Você só pode cadastrar veículos na sua regional.");
            }
            return regional;
        }
        if (regionalId == null) {
            throw new RegionalNaoPermitidaException("Informe a regional do veículo.");
        }
        Regional regional = regionalRepository.findById(regionalId)
                .orElseThrow(() -> new NotFoundException("Regional não encontrada"));
        if (!Boolean.TRUE.equals(regional.getAtivo())) {
            throw new RegionalNaoPermitidaException("Selecione uma regional ativa.");
        }
        return regional;
    }

    private Car findCarByIdOrThrow(Long carId) {
        return carRepository.findById(carId)
                .orElseThrow(() -> new NotFoundException(CAR_NOT_FOUND_MESSAGE));
    }

    private void validateLicensePlateDoesNotExist(String licensePlate) {
        if (carRepository.existsCarByLicensePlate(licensePlate)) {
            throw new CarAlreadyExistsException(LICENSE_PLATE_ALREADY_EXISTS_MESSAGE);
        }
    }

    private void updateLicensePlateIfProvided(Car car, Long currentCarId, String newLicensePlate) {
        if (isBlank(newLicensePlate)) {
            return;
        }
        validateLicensePlateNotUsedByAnotherCar(currentCarId, newLicensePlate);
        car.setLicensePlate(newLicensePlate);
    }

    private void validateLicensePlateNotUsedByAnotherCar(Long currentCarId, String licensePlate) {
        carRepository.findByLicensePlate(licensePlate)
                .filter(existingCar -> !existingCar.getId().equals(currentCarId))
                .ifPresent(existingCar -> {
                    throw new CarAlreadyExistsException(LICENSE_PLATE_ALREADY_EXISTS_MESSAGE);
                });
    }

    private void updateModelIfProvided(Car car, String model) {
        if (isNotBlank(model)) {
            car.setModel(model);
        }
    }

    private void updateActiveStatusIfProvided(Car car, Boolean isActive) {
        if (isActive != null) {
            car.setActive(isActive);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}