package com.example.investmentdatascannerservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.service.MarketDataStreamingService;
import com.example.investmentdatascannerservice.service.MarketDataStreamingService.ServiceStats;
import lombok.extern.slf4j.Slf4j;

/**
 * REST контроллер для мониторинга и управления потоковым сервисом
 * 
 * Предоставляет endpoints для мониторинга производительности и управления потоковым сервисом
 * данных.
 */
@Slf4j
@RestController
@RequestMapping("/api/streaming-service")
public class StreamingServiceController {

    private final MarketDataStreamingService streamingService;

    public StreamingServiceController(MarketDataStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * Получить статистику производительности потокового сервиса
     * 
     * @return статистика сервиса
     */
    @GetMapping("/stats")
    public ResponseEntity<ServiceStats> getServiceStats() {
        log.debug("Request to get streaming service stats");
        ServiceStats stats = streamingService.getServiceStats();
        log.info("Streaming service stats: isRunning={}, isConnected={}, totalReceivedAll={}",
                stats.isRunning(), stats.isConnected(), stats.getTotalReceivedAll());
        return ResponseEntity.ok(stats);
    }

    /**
     * Принудительное переподключение к T-Invest API
     * 
     * @return HTTP 200 OK при успешном запросе переподключения
     */
    @PostMapping("/reconnect")
    public ResponseEntity<Void> forceReconnect() {
        log.info("Request to force reconnect streaming service");
        streamingService.forceReconnect();
        log.info("Streaming service reconnect requested");
        return ResponseEntity.ok().build();
    }

    /**
     * Получить состояние подключения
     * 
     * @return true если подключен к T-Invest API
     */
    @GetMapping("/status")
    public ResponseEntity<Boolean> getConnectionStatus() {
        log.debug("Request to get streaming service connection status");
        ServiceStats stats = streamingService.getServiceStats();
        boolean isConnected = stats.isConnected();
        log.debug("Streaming service connection status: {}", isConnected);
        return ResponseEntity.ok(isConnected);
    }

    /**
     * Получить детальную информацию о состоянии сервиса
     * 
     * @return детальная информация о сервисе
     */
    @GetMapping("/health")
    public ResponseEntity<ServiceHealth> getServiceHealth() {
        log.debug("Request to get streaming service health");
        ServiceStats stats = streamingService.getServiceStats();
        ServiceHealth health = new ServiceHealth(stats.isRunning(), stats.isConnected(),
                stats.getTotalReceivedAll(), 0L, // totalErrors - не отслеживается
                stats.getTotalReceivedAll(), 0, // availableInserts - не используется
                0, // maxConcurrentInserts - не используется
                0.0, // insertUtilization - не используется
                0.0, // errorRate - не отслеживается
                0.0); // processingRate - не отслеживается
        log.debug("Streaming service health: isRunning={}, isConnected={}, totalProcessed={}",
                health.isRunning(), health.isConnected(), health.getTotalProcessed());
        return ResponseEntity.ok(health);
    }



    /**
     * Детальная информация о состоянии сервиса
     */
    public static class ServiceHealth {
        private final boolean isRunning;
        private final boolean isConnected;
        private final long totalProcessed;
        private final long totalErrors;
        private final long totalReceived;
        private final int availableInserts;
        private final int maxConcurrentInserts;
        private final double insertUtilization;
        private final double errorRate;
        private final double processingRate;

        public ServiceHealth(boolean isRunning, boolean isConnected, long totalProcessed,
                long totalErrors, long totalReceived, int availableInserts,
                int maxConcurrentInserts, double insertUtilization, double errorRate,
                double processingRate) {
            this.isRunning = isRunning;
            this.isConnected = isConnected;
            this.totalProcessed = totalProcessed;
            this.totalErrors = totalErrors;
            this.totalReceived = totalReceived;
            this.availableInserts = availableInserts;
            this.maxConcurrentInserts = maxConcurrentInserts;
            this.insertUtilization = insertUtilization;
            this.errorRate = errorRate;
            this.processingRate = processingRate;
        }

        public boolean isRunning() {
            return isRunning;
        }

        public boolean isConnected() {
            return isConnected;
        }

        public long getTotalProcessed() {
            return totalProcessed;
        }

        public long getTotalErrors() {
            return totalErrors;
        }

        public long getTotalReceived() {
            return totalReceived;
        }

        public int getAvailableInserts() {
            return availableInserts;
        }

        public int getMaxConcurrentInserts() {
            return maxConcurrentInserts;
        }

        public double getInsertUtilization() {
            return insertUtilization;
        }

        public double getErrorRate() {
            return errorRate;
        }

        public double getProcessingRate() {
            return processingRate;
        }
    }
}
