package com.example.investmentdatascannerservice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Конфигурация для пар инструментов
 * 
 * Позволяет настраивать пары инструментов через application.properties
 */
@ConfigurationProperties(prefix = "instrument-pairs")
@Data
public class InstrumentPairConfig {

    /**
     * Список пар инструментов
     */
    private List<PairConfig> pairs = List.of();

    /**
     * Конфигурация одной пары инструментов
     */
    @Data
    public static class PairConfig {
        private String pairId;
        private String firstInstrument;
        private String secondInstrument;
        private String firstInstrumentName;
        private String secondInstrumentName;
    }
}
