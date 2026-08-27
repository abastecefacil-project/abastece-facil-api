package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.regional.RegionalResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.RegionalMapper;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.repository.RegionalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.github.api_abastecefacil.constants.RegionalConstants.REGIONAL_NOT_FOUND_MESSAGE;

@Service
@Transactional(readOnly = true)
public class RegionalService {

    private final RegionalRepository regionalRepository;
    private final RegionalMapper regionalMapper;

    public RegionalService(RegionalRepository regionalRepository, RegionalMapper regionalMapper) {
        this.regionalRepository = regionalRepository;
        this.regionalMapper = regionalMapper;
    }

    public Page<RegionalResponse> getRegionais(Pageable pageable) {
        Page<Regional> regionaisPage = regionalRepository.findAll(pageable);
        return regionaisPage.map(regionalMapper::toResponse);
    }

    public RegionalResponse getRegionalById(Long regionalId) {
        Regional regional = findRegionalByIdOrThrow(regionalId);
        return regionalMapper.toResponse(regional);
    }

    private Regional findRegionalByIdOrThrow(Long regionalId) {
        return regionalRepository.findById(regionalId)
                .orElseThrow(() -> new NotFoundException(REGIONAL_NOT_FOUND_MESSAGE));
    }
}
