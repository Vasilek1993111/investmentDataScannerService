package com.example.investmentdatascannerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.example.investmentdatascannerservice.config.AppConfig;
import com.example.investmentdatascannerservice.config.InstrumentPairConfig;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Главный класс приложения Investment Data Scanner Service
 * 
 * Сервис для сканирования и обработки инвестиционных данных в реальном времени с использованием
 * T-Invest API, PostgreSQL и gRPC.
 * 
 * @author Investment Data Scanner Service Team
 * @version 1.0.0
 * @since 2024
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties({AppConfig.class, QuoteScannerConfig.class,
        InstrumentPairConfig.class})
@EnableScheduling
public class InvestmentDataScannerService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InvestmentDataScannerService.class);

    /**
     * Точка входа в приложение
     * 
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        logger.info("Starting Investment Data Scanner Service application...");
        try {
            SpringApplication.run(InvestmentDataScannerService.class, args);
            logger.info("Investment Data Scanner Service application started successfully");
        } catch (Exception e) {
            logger.error("Failed to start Investment Data Scanner Service application: {}", e.getMessage(), e);
            throw e;
        }
    }
}
