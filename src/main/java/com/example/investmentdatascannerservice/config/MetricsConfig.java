package com.example.investmentdatascannerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Конфигурация метрик и мониторинга
 * 
 * Настраивает систему метрик для отслеживания производительности
 */
@Configuration
public class MetricsConfig {

    /**
     * Реестр метрик
     */
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Аспект для автоматического измерения времени выполнения методов
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
