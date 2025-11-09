package com.example.investmentdatascannerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Конфигурация метрик и мониторинга
 * 
 * Настраивает систему метрик для отслеживания производительности
 */
@Slf4j
@Configuration
public class MetricsConfig {

    /**
     * Реестр метрик
     */
    @Bean
    public MeterRegistry meterRegistry() {
        log.info("Creating SimpleMeterRegistry for metrics");
        return new SimpleMeterRegistry();
    }

    /**
     * Аспект для автоматического измерения времени выполнения методов
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        log.info("Creating TimedAspect for method execution timing");
        return new TimedAspect(registry);
    }
}
