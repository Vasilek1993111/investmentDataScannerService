package com.example.investmentdatascannerservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Основные тесты приложения Investment Data Scanner Service
 * 
 * Проверяет корректность загрузки контекста Spring Boot и инициализацию всех компонентов
 * приложения.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvestmentDataScannerServiceTests {

    @Test
    void contextLoads() {
        // Тест проверяет, что Spring Boot контекст загружается корректно
        // и все бины инициализируются без ошибок
    }
}
