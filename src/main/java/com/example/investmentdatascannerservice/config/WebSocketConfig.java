package com.example.investmentdatascannerservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.example.investmentdatascannerservice.controller.PairWebSocketController;
import com.example.investmentdatascannerservice.controller.QuoteWebSocketController;
import lombok.extern.slf4j.Slf4j;

/**
 * Конфигурация WebSocket для real-time обновлений
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuoteWebSocketController quoteWebSocketController;
    private final PairWebSocketController pairWebSocketController;

    public WebSocketConfig(QuoteWebSocketController quoteWebSocketController,
            PairWebSocketController pairWebSocketController) {
        this.quoteWebSocketController = quoteWebSocketController;
        this.pairWebSocketController = pairWebSocketController;
        log.info("WebSocketConfig initialized with QuoteWebSocketController and PairWebSocketController");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // По умолчанию Spring разрешает только same-origin подключения, что безопаснее
        registry.addHandler(quoteWebSocketController, "/ws/quotes");
        log.info("Registered WebSocket handler for quotes at /ws/quotes");

        // По умолчанию Spring разрешает только same-origin подключения, что безопаснее
        registry.addHandler(pairWebSocketController, "/ws/pairs");
        log.info("Registered WebSocket handler for pairs at /ws/pairs");
    }
}
