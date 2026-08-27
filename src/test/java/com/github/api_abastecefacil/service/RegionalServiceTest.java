package com.github.api_abastecefacil.service;

import com.github.api_abastecefacil.dto.regional.RegionalResponse;
import com.github.api_abastecefacil.exception.NotFoundException;
import com.github.api_abastecefacil.mapper.RegionalMapper;
import com.github.api_abastecefacil.model.Regional;
import com.github.api_abastecefacil.repository.RegionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegionalServiceTest {

    @Mock
    private RegionalRepository regionalRepository;

    @Mock
    private RegionalMapper regionalMapper;

    @InjectMocks
    private RegionalService regionalService;

    private Regional regional;
    private RegionalResponse regionalResponse;

    @BeforeEach
    void setUp() {
        regional = new Regional()
                .setId(1L)
                .setNome("Joinville")
                .setSigla("JOI")
                .setAtivo(true)
                .setCreatedAt(LocalDateTime.now());

        regionalResponse = new RegionalResponse(1L, "Joinville", "JOI", true, LocalDateTime.now(), null);
    }

    @Test
    void getRegionais_ShouldReturnPagedRegionais() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Regional> regionalPage = new PageImpl<>(List.of(regional));

        when(regionalRepository.findAll(pageable)).thenReturn(regionalPage);
        when(regionalMapper.toResponse(regional)).thenReturn(regionalResponse);

        Page<RegionalResponse> result = regionalService.getRegionais(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).sigla()).isEqualTo("JOI");
    }

    @Test
    void getRegionalById_ShouldReturnRegional_WhenIdExists() {
        when(regionalRepository.findById(1L)).thenReturn(Optional.of(regional));
        when(regionalMapper.toResponse(regional)).thenReturn(regionalResponse);

        RegionalResponse result = regionalService.getRegionalById(1L);

        assertThat(result).isNotNull();
        assertThat(result.nome()).isEqualTo("Joinville");
        verify(regionalRepository).findById(1L);
    }

    @Test
    void getRegionalById_ShouldThrowNotFoundException_WhenIdDoesNotExist() {
        when(regionalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> regionalService.getRegionalById(99L));

        verify(regionalMapper, never()).toResponse(any());
    }
}
