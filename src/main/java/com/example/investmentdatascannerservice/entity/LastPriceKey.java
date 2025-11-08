package com.example.investmentdatascannerservice.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Составной ключ для LastPriceEntity
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastPriceKey implements Serializable {

    @Column(nullable = false, length = 255)
    private String figi;

    @Column(nullable = false)
    private LocalDateTime time;
}

