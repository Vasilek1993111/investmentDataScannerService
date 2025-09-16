package com.example.investmentdatascannerservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Конфигурация кэширования для приложения
 * 
 * Настраивает кэширование цен закрытия, открытия и вечерней сессии с использованием
 * ConcurrentMapCacheManager для in-memory кэша.
 */
@Configuration
@EnableCaching
@EnableAsync
public class CacheConfig {

    /**
     * Конфигурация менеджера кэша
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("closePrices", "eveningSessionPrices",
                "lastClosePrices", "lastEveningSessionPrices");
    }
}
