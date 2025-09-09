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
import com.example.investmentdatascannerservice.dto.PairComparisonResult;
import com.example.investmentdatascannerservice.service.InstrumentPairService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WebSocket контроллер для трансляции результатов сравнения пар
 */
@Component
public class PairWebSocketController implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PairWebSocketController.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final InstrumentPairService instrumentPairService;
    private final ObjectMapper objectMapper;

    public PairWebSocketController(InstrumentPairService instrumentPairService) {
        this.instrumentPairService = instrumentPairService;
        this.objectMapper = new ObjectMapper();

        // Настраиваем ObjectMapper для работы с LocalDateTime
        this.objectMapper.findAndRegisterModules();

        // Подписываемся на обновления сравнений пар
        this.instrumentPairService.subscribeToComparisons(this::broadcastComparison);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket соединение для пар установлено. Всего соединений: {}", sessions.size());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
            throws Exception {
        // Обработка входящих сообщений (если необходимо)
        log.debug("Получено сообщение от клиента пар: {}", message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        log.error("Ошибка WebSocket транспорта для пар", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
            throws Exception {
        sessions.remove(session);
        log.info("WebSocket соединение для пар закрыто. Всего соединений: {}", sessions.size());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Очистка неактивных WebSocket сессий
     */
    public void cleanupInactiveSessions() {
        var iterator = sessions.iterator();
        int removedCount = 0;
        while (iterator.hasNext()) {
            WebSocketSession session = iterator.next();
            if (!session.isOpen()) {
                iterator.remove();
                removedCount++;
                log.debug("Removed inactive pair session: {}", session.getId());
            }
        }
        if (removedCount > 0) {
            log.info("Cleaned up {} inactive pair sessions. Active sessions: {}", removedCount,
                    sessions.size());
        }
    }

    private void broadcastComparison(PairComparisonResult comparisonResult) {
        if (sessions.isEmpty()) {
            log.debug("No WebSocket sessions for pair comparisons, skipping broadcast");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(comparisonResult);
            TextMessage message = new TextMessage(json);
            log.debug("Broadcasting pair comparison to {} sessions: {}", sessions.size(),
                    comparisonResult);

            int sentCount = 0;
            // Используем итератор для безопасного удаления сессий
            var iterator = sessions.iterator();
            while (iterator.hasNext()) {
                WebSocketSession session = iterator.next();
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                        sentCount++;
                        log.debug("Successfully sent pair comparison to session {}",
                                session.getId());
                    } catch (Exception e) {
                        log.error("Ошибка отправки сообщения клиенту пар, удаляем сессию: {}",
                                session.getId(), e);
                        iterator.remove(); // Безопасное удаление через итератор
                    }
                } else {
                    log.debug("Removing closed pair session: {}", session.getId());
                    iterator.remove(); // Безопасное удаление через итератор
                }
            }
            log.debug("Sent pair comparison to {} sessions successfully", sentCount);
        } catch (Exception e) {
            log.error("Ошибка сериализации данных сравнения пар", e);
        }
    }
}
