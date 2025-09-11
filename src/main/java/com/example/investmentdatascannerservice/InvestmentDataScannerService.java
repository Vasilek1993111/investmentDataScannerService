package com.example.investmentdatascannerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.investmentdatascannerservice.config.AppConfig;
import com.example.investmentdatascannerservice.config.InstrumentPairConfig;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;

/**
 * Jg Главный класс приложения Investment Data Scanner Service
 * 
 * Сервис для сканирования и обработки инвестиционных данных в реальном времени с использованием
 * T-Invest API, PostgreSQL и gRPC.
 * 
 * @author Investment Data Scanner Service Team
 * @version 1.0.0
 * @since 2024
 */
@SpringBootApplication
@EnableConfigurationProperties({AppConfig.class, QuoteScannerConfig.class,
        InstrumentPairConfig.class})
public class InvestmentDataScannerService {

    /**
     * Точка входа в приложение
     * 
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(InvestmentDataScannerService.class, args);
    }
}
