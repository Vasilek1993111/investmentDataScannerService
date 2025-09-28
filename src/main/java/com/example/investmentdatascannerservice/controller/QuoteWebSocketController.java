package com.example.investmentdatascannerservice.controller;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import com.example.investmentdatascannerservice.dto.QuoteData;
import com.example.investmentdatascannerservice.service.QuoteScannerService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WebSocket контроллер для трансляции котировок
 */
@Component
public class QuoteWebSocketController implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteWebSocketController.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final QuoteScannerService quoteScannerService;
    private final ObjectMapper objectMapper;

    public QuoteWebSocketController(QuoteScannerService quoteScannerService) {
        this.quoteScannerService = quoteScannerService;
        this.objectMapper = new ObjectMapper();

        // Настраиваем ObjectMapper для работы с LocalDateTime
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket соединение установлено. Всего соединений: {}", sessions.size());

        // Подписываемся на обновления котировок только при первом подключении
        if (sessions.size() == 1) {
            this.quoteScannerService.subscribeToQuotes(this::broadcastQuote);
            log.info("Subscribed to quote updates for WebSocket broadcasting");
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
            throws Exception {
        // Обработка входящих сообщений (если необходимо)
        log.debug("Получено сообщение от клиента: {}", message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        log.error("Ошибка WebSocket транспорта", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
            throws Exception {
        sessions.remove(session);
        log.info("WebSocket соединение закрыто. Всего соединений: {}", sessions.size());

        // Отписываемся от обновлений котировок, если нет активных соединений
        if (sessions.isEmpty()) {
            this.quoteScannerService.unsubscribeFromQuotes(this::broadcastQuote);
            log.info("Unsubscribed from quote updates - no active WebSocket connections");
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Очистка неактивных WebSocket сессий
     */
    public void cleanupInactiveSessions() {
        int initialSize = sessions.size();
        var iterator = sessions.iterator();
        while (iterator.hasNext()) {
            WebSocketSession session = iterator.next();
            if (!session.isOpen()) {
                log.debug("Cleaning up inactive session: {}", session.getId());
                iterator.remove();
            }
        }
        int finalSize = sessions.size();
        if (initialSize != finalSize) {
            log.info("Cleaned up {} inactive WebSocket sessions. Active sessions: {}",
                    initialSize - finalSize, finalSize);
        }
    }

    private void broadcastQuote(QuoteData quoteData) {

        log.debug("Broadcasting quote data: {} to {} sessions", quoteData, sessions.size());

        if (sessions.isEmpty()) {
            log.debug("No WebSocket sessions available for broadcasting");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(quoteData);
            TextMessage message = new TextMessage(json);
            log.debug("Serialized quote data: {}", json);

            // Отправляем всем подключенным клиентам
            int sentCount = 0;
            // Создаем копию для безопасной итерации
            Set<WebSocketSession> sessionsCopy = new java.util.HashSet<>(sessions);
            for (WebSocketSession session : sessionsCopy) {
                if (session.isOpen()) {
                    try {
                        // Синхронизируем отправку сообщения
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                        sentCount++;
                        log.debug("Quote data sent to session: {}", session.getId());
                    } catch (Exception e) {
                        log.error("Ошибка отправки сообщения клиенту, удаляем сессию: {}",
                                session.getId(), e);
                        // Безопасное удаление из CopyOnWriteArraySet
                        sessions.remove(session);
                    }
                } else {
                    log.debug("Removing closed session: {}", session.getId());
                    // Безопасное удаление из CopyOnWriteArraySet
                    sessions.remove(session);
                }
            }
            log.debug("Quote data sent to {} sessions", sentCount);
        } catch (Exception e) {
            log.error("Ошибка сериализации данных котировки", e);
        }
    }
}
