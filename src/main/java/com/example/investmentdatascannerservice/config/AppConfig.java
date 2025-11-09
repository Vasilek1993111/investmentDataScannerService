package com.example.investmentdatascannerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Конфигурация приложения
 * 
 * Содержит общие настройки приложения и управление переменными окружения
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {

    static {
        // Загружаем переменные из .env файла в статическом блоке
        Dotenv dotenv = Dotenv.configure().directory("./").filename(".env").ignoreIfMalformed()
                .ignoreIfMissing().load();

        if (dotenv != null) {
            int loadedCount = 0;
            for (var entry : dotenv.entries()) {
                // Устанавливаем в System Properties
                System.setProperty(entry.getKey(), entry.getValue());
                loadedCount++;
            }
            org.slf4j.LoggerFactory.getLogger(AppConfig.class)
                    .info("Loaded {} environment variables from .env file", loadedCount);
        } else {
            org.slf4j.LoggerFactory.getLogger(AppConfig.class)
                    .warn(".env file not found or could not be loaded");
        }
    }

    /**
     * Временная зона приложения для расчетов отклонений и временных меток
     */
    private String timezone = "Europe/Moscow";

    @Bean
    @Primary
    public Dotenv dotenv() {
        Dotenv dotenv = Dotenv.configure().directory("./").filename(".env").ignoreIfMalformed()
                .ignoreIfMissing().load();
        if (dotenv != null) {
            log.info("Dotenv bean created successfully with {} variables", dotenv.entries().size());
        } else {
            log.warn("Dotenv bean created but .env file was not loaded");
        }
        return dotenv;
    }
}
