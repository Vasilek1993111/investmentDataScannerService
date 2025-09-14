package com.example.investmentdatascannerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.Data;

/**
 * Конфигурация приложения
 * 
 * Содержит общие настройки приложения и управление переменными окружения
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {

    static {
        // Загружаем переменные из .env файла в статическом блоке
        Dotenv dotenv = Dotenv.configure().directory("./").filename(".env").ignoreIfMalformed()
                .ignoreIfMissing().load();

        if (dotenv != null) {
            for (var entry : dotenv.entries()) {
                // Устанавливаем в System Properties
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Временная зона приложения для расчетов отклонений и временных меток
     */
    private String timezone = "Europe/Moscow";

    @Bean
    @Primary
    public Dotenv dotenv() {
        return Dotenv.configure().directory("./").filename(".env").ignoreIfMalformed()
                .ignoreIfMissing().load();
    }
}
