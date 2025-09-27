package com.example.investmentdatascannerservice.entity;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Составной ключ для OpenPriceEntity
 * 
 * Содержит FIGI инструмента и дату торгов для уникальной идентификации цены открытия.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenPriceKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate priceDate;
    private String figi;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        OpenPriceKey that = (OpenPriceKey) o;
        return priceDate != null ? priceDate.equals(that.priceDate)
                : that.priceDate == null && figi != null ? figi.equals(that.figi)
                        : that.figi == null;
    }

    @Override
    public int hashCode() {
        int result = priceDate != null ? priceDate.hashCode() : 0;
        result = 31 * result + (figi != null ? figi.hashCode() : 0);
        return result;
    }
}
