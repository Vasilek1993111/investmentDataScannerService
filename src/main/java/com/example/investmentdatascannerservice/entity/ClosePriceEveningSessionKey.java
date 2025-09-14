package com.example.investmentdatascannerservice.entity;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.Data;

/**
 * Составной ключ для таблицы close_prices_evening_session
 */
@Data
public class ClosePriceEveningSessionKey implements Serializable {

    private LocalDate priceDate;
    private String figi;

    public ClosePriceEveningSessionKey() {}

    public ClosePriceEveningSessionKey(LocalDate priceDate, String figi) {
        this.priceDate = priceDate;
        this.figi = figi;
    }
}
