package com.example.investmentdatascannerservice.entity;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Entity для таблицы invest.dividends
 */
@Entity
@Table(name = "dividends", schema = "invest")
@Data
public class DividendEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "figi", nullable = false, length = 255)
    private String figi;

    @Column(name = "declared_date", nullable = false)
    private LocalDate declaredDate;
}


