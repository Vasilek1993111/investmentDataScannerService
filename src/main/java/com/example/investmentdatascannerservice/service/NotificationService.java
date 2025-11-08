package com.example.investmentdatascannerservice.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.dto.QuoteData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Сервис для асинхронных уведомлений подписчиков
 * 
 * Обеспечивает высокопроизводительную доставку уведомлений с минимальными задержками
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Set<Consumer<QuoteData>> subscribers = ConcurrentHashMap.newKeySet();
    private final ExecutorService notificationExecutor;

    // Метрики
    private final Counter notificationsSent;
    private final Counter notificationsFailed;
    private final Counter subscribersCount;

    public NotificationService(
            @Qualifier("notificationExecutor") ExecutorService notificationExecutor,
            MeterRegistry meterRegistry) {
        this.notificationExecutor = notificationExecutor;

        // Инициализация метрик
        this.notificationsSent = Counter.builder("notifications.sent")
                .description("Total notifications sent to subscribers").register(meterRegistry);
        this.notificationsFailed = Counter.builder("notifications.failed")
                .description("Total failed notifications").register(meterRegistry);
        this.subscribersCount = Counter.builder("notifications.subscribers")
                .description("Current number of subscribers").register(meterRegistry);
    }

    /**
     * Уведомление всех подписчиков о новой котировке
     */
    public void notifySubscribers(QuoteData quoteData) {
        if (subscribers.isEmpty()) {
            log.debug("No subscribers available, skipping notification for {}",
                    quoteData.getTicker());
            return;
        }

        log.debug("Notifying {} subscribers about quote data for {}: {}", subscribers.size(),
                quoteData.getTicker(), quoteData);

        // Параллельная отправка уведомлений
        subscribers.parallelStream().forEach(subscriber -> {
            notificationExecutor.submit(() -> {
                try {
                    subscriber.accept(quoteData);
                    notificationsSent.increment();
                    log.debug("Successfully notified subscriber about {}", quoteData.getTicker());
                } catch (Exception e) {
                    log.warn("Error notifying subscriber about {}", quoteData.getTicker(), e);
                    notificationsFailed.increment();
                }
            });
        });
    }

    /**
     * Подписка на обновления котировок
     */
    public void subscribe(Consumer<QuoteData> subscriber) {
        subscribers.add(subscriber);
        subscribersCount.increment();
        log.info("New quote subscriber added. Total subscribers: {}", subscribers.size());
    }

    /**
     * Отписка от обновлений котировок
     */
    public void unsubscribe(Consumer<QuoteData> subscriber) {
        boolean removed = subscribers.remove(subscriber);
        if (removed) {
            subscribersCount.increment(-1);
            log.info("Quote subscriber removed. Total subscribers: {}", subscribers.size());
        } else {
            log.warn("Attempted to remove non-existent subscriber");
        }
    }

    /**
     * Получение количества активных подписчиков
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    /**
     * Проверка наличия подписчиков
     */
    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    /**
     * Очистка всех подписчиков
     */
    public void clearSubscribers() {
        int count = subscribers.size();
        subscribers.clear();
        subscribersCount.increment(-count);
        log.info("Cleared {} subscribers", count);
    }

    /**
     * Получение статистики сервиса
     */
    public java.util.Map<String, Object> getStats() {
        // Преобразуем double в long для целочисленных значений счетчиков
        long sentCount = (long) notificationsSent.count();
        long failedCount = (long) notificationsFailed.count();

        return java.util.Map.of("subscriberCount", subscribers.size(), "notificationsSent",
                sentCount, "notificationsFailed", failedCount, "hasSubscribers", hasSubscribers());
    }
}
