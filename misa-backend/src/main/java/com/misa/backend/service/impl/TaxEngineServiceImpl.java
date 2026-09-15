package com.misa.backend.service.impl;

import com.misa.backend.configuration.MisaProperties;
import com.misa.backend.service.TaxEngineService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxEngineServiceImpl implements TaxEngineService {

    MisaProperties misaProperties;

    @Override
    public BigDecimal calculateTaxWithheld(BigDecimal taxableIncome) {
        BigDecimal rate = BigDecimal.valueOf(misaProperties.getTax().getWithholdingRate());
        return taxableIncome.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }
}
