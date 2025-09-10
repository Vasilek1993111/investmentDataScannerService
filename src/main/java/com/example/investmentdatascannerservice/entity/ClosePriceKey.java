package com.example.investmentdatascannerservice.entity;

import java.io.Serializable;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Составной ключ для ClosePriceEntity
 * 
 * Состоит из даты торгов и FIGI инструмента для уникальной идентификации цены закрытия за
 * конкретный день.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClosePriceKey implements Serializable {

    @Column(nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false, length = 255)
    private String figi;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ClosePriceKey that = (ClosePriceKey) o;
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
