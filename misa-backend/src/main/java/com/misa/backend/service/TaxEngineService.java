package com.misa.backend.service;

import java.math.BigDecimal;

public interface TaxEngineService {

    BigDecimal calculateTaxWithheld(BigDecimal taxableIncome);
}
