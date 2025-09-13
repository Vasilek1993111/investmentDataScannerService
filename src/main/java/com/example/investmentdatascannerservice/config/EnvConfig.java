package com.example.investmentdatascannerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import io.github.cdimascio.dotenv.Dotenv;

@Configuration
public class EnvConfig {

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

    @Bean
    @Primary
    public Dotenv dotenv() {
        return Dotenv.configure().directory("./").filename(".env").ignoreIfMalformed()
                .ignoreIfMissing().load();
    }
}
