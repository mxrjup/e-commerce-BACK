package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.VatRateRequest;
import com.ecommerce.catalog.dto.VatRateResponse;
import com.ecommerce.catalog.entity.VatRate;
import com.ecommerce.catalog.repository.VatRateRepository;
import com.ecommerce.catalog.support.Persistence;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * VAT rates, with exactly one default: the rate a new product gets.
 *
 * <p>The default can only move, never disappear: marking a rate as default
 * takes the flag off the previous one, and the current default can neither be
 * unmarked nor deleted. Every write locks the table first, so concurrent
 * writes cannot leave two defaults behind.
 */
@Service
@RequiredArgsConstructor
public class VatRateService {

    private final VatRateRepository vatRateRepository;

    @Transactional(readOnly = true)
    public List<VatRateResponse> list() {
        return vatRateRepository.findAll(Sort.by(Sort.Direction.DESC, "rate")).stream()
                .map(VatRateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VatRateResponse get(Long id) {
        return VatRateResponse.from(find(id));
    }

    @Transactional
    public VatRateResponse create(VatRateRequest request) {
        vatRateRepository.lockAll();
        return VatRateResponse.from(save(new VatRate(), request));
    }

    @Transactional
    public VatRateResponse update(Long id, VatRateRequest request) {
        vatRateRepository.lockAll();
        VatRate vatRate = find(id);
        if (vatRate.isDefaultRate() && !request.defaultRate()) {
            throw new ConflictException("'" + vatRate.getLabel()
                    + "' is the default VAT rate: mark another rate as default instead");
        }
        return VatRateResponse.from(save(vatRate, request));
    }

    @Transactional
    public void delete(Long id) {
        vatRateRepository.lockAll();
        VatRate vatRate = find(id);
        if (vatRate.isDefaultRate()) {
            throw new ConflictException("'" + vatRate.getLabel()
                    + "' is the default VAT rate: mark another rate as default before deleting it");
        }
        Persistence.orConflict(() -> {
            vatRateRepository.delete(vatRate);
            vatRateRepository.flush();
        }, "VAT rate '" + vatRate.getLabel() + "' is still used and cannot be deleted");
    }

    private VatRate save(VatRate vatRate, VatRateRequest request) {
        String label = request.label().trim();
        vatRateRepository.findByLabelIgnoreCase(label)
                .filter(other -> !Objects.equals(other.getId(), vatRate.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A VAT rate labelled '" + label + "' already exists");
                });

        // Le tout premier taux devient le defaut, faute de quoi il n'y en aurait aucun.
        boolean becomesDefault = request.defaultRate() || vatRateRepository.findByDefaultRateTrue().isEmpty();
        if (becomesDefault) {
            vatRateRepository.findByDefaultRateTrue()
                    .filter(current -> !Objects.equals(current.getId(), vatRate.getId()))
                    .ifPresent(current -> current.setDefaultRate(false));
        }

        vatRate.setLabel(label);
        // Deux decimales, comme en base : 13 est renvoye 13.00 des la creation.
        vatRate.setRate(request.rate().setScale(2, RoundingMode.UNNECESSARY));
        vatRate.setDefaultRate(becomesDefault || vatRate.isDefaultRate());
        return Persistence.orConflict(() -> vatRateRepository.saveAndFlush(vatRate),
                "A VAT rate with this label already exists");
    }

    private VatRate find(Long id) {
        return vatRateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VAT rate " + id + " not found"));
    }
}
