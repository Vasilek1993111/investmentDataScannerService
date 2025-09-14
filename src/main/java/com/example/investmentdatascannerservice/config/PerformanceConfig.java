package com.example.investmentdatascannerservice.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Конфигурация производительности для высоконагруженных операций
 * 
 * Настраивает пулы потоков и асинхронную обработку для максимальной производительности
 */
@Configuration
@EnableAsync
public class PerformanceConfig {

    /**
     * Пул потоков для обработки рыночных данных
     * 
     * Оптимизирован для высокой пропускной способности и низкой задержки
     */
    @Bean(name = "marketDataExecutor")
    public ExecutorService marketDataExecutor() {
        // Размер пула основан на количестве ядер процессора
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        int maxPoolSize = Runtime.getRuntime().availableProcessors() * 4;

        return new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10000),
                r -> new Thread(r, "MarketData-" + System.currentTimeMillis()),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Пул потоков для уведомлений подписчиков
     * 
     * Оптимизирован для быстрой доставки уведомлений
     */
    @Bean(name = "notificationExecutor")
    public ExecutorService notificationExecutor() {
        return new ThreadPoolExecutor(5, 10, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000),
                r -> new Thread(r, "Notification-" + System.currentTimeMillis()),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Пул потоков для обработки пар инструментов
     * 
     * Оптимизирован для расчетов дельт между инструментами
     */
    @Bean(name = "pairProcessingExecutor")
    public ExecutorService pairProcessingExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;

        return new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(5000),
                r -> new Thread(r, "PairProcessing-" + System.currentTimeMillis()),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Пул потоков для операций с базой данных
     * 
     * Оптимизирован для batch операций и кэширования
     */
    @Bean(name = "databaseExecutor")
    public ExecutorService databaseExecutor() {
        return new ThreadPoolExecutor(3, 6, 120L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000),
                r -> new Thread(r, "Database-" + System.currentTimeMillis()),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
