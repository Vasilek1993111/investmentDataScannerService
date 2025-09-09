package com.example.investmentdatascannerservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.example.investmentdatascannerservice.controller.PairWebSocketController;
import com.example.investmentdatascannerservice.controller.QuoteWebSocketController;

/**
 * Конфигурация WebSocket для real-time обновлений
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuoteWebSocketController quoteWebSocketController;
    private final PairWebSocketController pairWebSocketController;

    public WebSocketConfig(QuoteWebSocketController quoteWebSocketController,
            PairWebSocketController pairWebSocketController) {
        this.quoteWebSocketController = quoteWebSocketController;
        this.pairWebSocketController = pairWebSocketController;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Регистрируем WebSocket для котировок
        registry.addHandler(quoteWebSocketController, "/ws/quotes").setAllowedOrigins("*");

        // Регистрируем WebSocket для пар инструментов
        registry.addHandler(pairWebSocketController, "/ws/pairs").setAllowedOrigins("*");

        // В продакшене следует ограничить домены
    }
}
