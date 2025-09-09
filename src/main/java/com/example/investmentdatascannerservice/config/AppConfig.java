package com.example.investmentdatascannerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация приложения
 * 
 * Содержит общие настройки приложения, такие как временная зона
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    /**
     * Временная зона приложения для расчетов отклонений и временных меток
     */
    private String timezone = "Europe/Moscow";

    // Геттеры и сеттеры
    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
