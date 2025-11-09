package com.example.investmentdatascannerservice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;

/**
 * Конфигурация для пар инструментов
 * 
 * Позволяет настраивать пары инструментов через application.properties
 */
@Slf4j
@ConfigurationProperties(prefix = "instrument-pairs")
@Data
public class InstrumentPairConfig implements ApplicationContextAware {

    /**
     * Список пар инструментов
     */
    private List<PairConfig> pairs = List.of();

    @PostConstruct
    public void init() {
        log.info("InstrumentPairConfig initialized with {} pairs", pairs.size());
        pairs.forEach(pair -> log.debug("Pair loaded: pairId={}, firstInstrument={}, secondInstrument={}, firstInstrumentName={}, secondInstrumentName={}",
                pair.getPairId(), pair.getFirstInstrument(), pair.getSecondInstrument(),
                pair.getFirstInstrumentName(), pair.getSecondInstrumentName()));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        // Метод требуется для ApplicationContextAware, но в данном случае не используется
    }

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
