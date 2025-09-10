package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import com.example.investmentdatascannerservice.dto.QuoteData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.OrderBook;
import ru.tinkoff.piapi.contract.v1.Trade;
import ru.tinkoff.piapi.contract.v1.TradeDirection;

/**
 * Высокопроизводительный сервис для сканирования котировок в реальном времени
 * 
 * Обеспечивает получение котировок через STREAM, расчет разниц между ценами и передачу данных в
 * браузер с минимальными задержками. Сохранение в БД не выполняется для максимальной
 * производительности.
 */
@Service
public class QuoteScannerService {

    private static final Logger log = LoggerFactory.getLogger(QuoteScannerService.class);

    // Конфигурация производительности
    private static final int PROCESSING_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private final QuoteScannerConfig config;
    private final InstrumentPairService instrumentPairService;
    private final ClosePriceService closePriceService;
    private final ClosePriceEveningSessionService closePriceEveningSessionService;

    // Хранилище последних цен для расчета разниц
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();
    private final Map<String, String> instrumentNames = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> closePrices = new ConcurrentHashMap<>();

    // Хранилище данных стакана заявок
    private final Map<String, BigDecimal> bestBids = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> bestAsks = new ConcurrentHashMap<>();
    private final Map<String, Long> bestBidQuantities = new ConcurrentHashMap<>();
    private final Map<String, Long> bestAskQuantities = new ConcurrentHashMap<>();

    // Подписчики на обновления котировок (для WebSocket)
    private final Set<Consumer<QuoteData>> quoteSubscribers = new CopyOnWriteArraySet<>();

    // Потоки для обработки
    private final ExecutorService processingExecutor =
            Executors.newFixedThreadPool(PROCESSING_THREADS);

    // Планировщик для периодических задач
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Статистика
    private final AtomicLong totalQuotesProcessed = new AtomicLong(0);
    private final AtomicLong totalQuotesSent = new AtomicLong(0);

    public QuoteScannerService(QuoteScannerConfig config,
            InstrumentPairService instrumentPairService, ClosePriceService closePriceService,
            ClosePriceEveningSessionService closePriceEveningSessionService) {
        this.config = config;
        this.instrumentPairService = instrumentPairService;
        this.closePriceService = closePriceService;
        this.closePriceEveningSessionService = closePriceEveningSessionService;
    }

    @PostConstruct
    public void init() {
        log.info("=== QUOTE SCANNER SERVICE INITIALIZATION ===");
        log.info("Initializing QuoteScannerService with {} processing threads", PROCESSING_THREADS);
        log.info("Max quotes per second: {}", config.getMaxQuotesPerSecond());
        log.info("Database saving enabled: {}", config.isEnableDatabaseSaving());
        log.info("WebSocket broadcast enabled: {}", config.isEnableWebSocketBroadcast());
        log.info("Configured instruments: {}", config.getInstruments().size());
        log.info("Configured instruments list: {}", config.getInstruments());
        log.info("Instrument names configured: {}", config.getInstrumentNames().size());
        log.info("Instrument names: {}", config.getInstrumentNames());

        // Загружаем имена инструментов из конфигурации
        instrumentNames.putAll(config.getInstrumentNames());

        // Загружаем цены закрытия за предыдущий торговый день
        loadClosePrices();

        // Загружаем цены закрытия вечерней сессии за предыдущий торговый день
        loadEveningClosePrices();

        // Запускаем периодическую очистку неактивных подписчиков каждые 30 секунд
        scheduler.scheduleAtFixedRate(this::cleanupInactiveSubscribers, 30, 30, TimeUnit.SECONDS);

        log.info("=============================================");
    }

    /**
     * Загружает цены закрытия за предыдущий торговый день для всех настроенных инструментов
     */
    private void loadClosePrices() {
        try {
            log.info("Loading close prices for previous trading day...");
            Map<String, BigDecimal> loadedClosePrices =
                    closePriceService.getClosePricesForPreviousDay(config.getInstruments());
            closePrices.putAll(loadedClosePrices);
            log.info("Loaded {} close prices for previous trading day", loadedClosePrices.size());
        } catch (Exception e) {
            log.error("Error loading close prices", e);
        }
    }

    /**
     * Загружает цены закрытия вечерней сессии за предыдущий торговый день для всех настроенных
     * инструментов
     */
    private void loadEveningClosePrices() {
        try {
            log.info("Loading evening close prices for previous trading day...");
            Map<String, BigDecimal> loadedEveningClosePrices = closePriceEveningSessionService
                    .loadEveningClosePricesForPreviousDay(config.getInstruments());
            log.info("Loaded {} evening close prices for previous trading day",
                    loadedEveningClosePrices.size());
            if (!loadedEveningClosePrices.isEmpty()) {
                log.info("Evening close prices loaded for instruments: {}",
                        loadedEveningClosePrices.keySet());
            }
        } catch (Exception e) {
            log.error("Error loading evening close prices", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down QuoteScannerService...");
        scheduler.shutdown();
        processingExecutor.shutdown();
        log.info("QuoteScannerService shutdown completed. Total processed: {} quotes",
                totalQuotesProcessed.get());
    }

    /**
     * Обработка данных о последней цене с максимальной производительностью
     */
    public void processLastPrice(LastPrice price) {
        processingExecutor.submit(() -> {
            try {
                String figi = price.getFigi();

                // Фильтрация по настроенным инструментам
                if (!config.getInstruments().isEmpty() && !config.getInstruments().contains(figi)) {
                    log.debug(
                            "Filtering out LastPrice for instrument {} - not in configured list: {}",
                            figi, config.getInstruments());
                    return;
                }

                log.debug("Processing LastPrice for instrument {} - in configured list: {}", figi,
                        config.getInstruments());
                Instant eventInstant = Instant.ofEpochSecond(price.getTime().getSeconds(),
                        price.getTime().getNanos());
                LocalDateTime eventTime =
                        LocalDateTime.ofInstant(eventInstant, ZoneOffset.of("+3"));

                BigDecimal currentPrice = BigDecimal.valueOf(price.getPrice().getUnits())
                        .add(BigDecimal.valueOf(price.getPrice().getNano()).movePointLeft(9));

                // Получаем предыдущую цену для расчета разницы
                BigDecimal previousPrice = lastPrices.get(figi);

                // Обновляем последнюю цену
                lastPrices.put(figi, currentPrice);

                // Определяем направление на основе изменения цены
                String direction = "NEUTRAL";
                if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0) {
                    int comparison = currentPrice.compareTo(previousPrice);
                    if (comparison > 0) {
                        direction = "UP";
                    } else if (comparison < 0) {
                        direction = "DOWN";
                    }
                }

                // Получаем цену закрытия за предыдущий торговый день
                BigDecimal closePrice = closePrices.get(figi);

                // Получаем цену закрытия вечерней сессии за предыдущий торговый день
                BigDecimal closePriceVS =
                        closePriceEveningSessionService.getEveningClosePrice(figi);

                // Получаем данные стакана для этого инструмента
                BigDecimal bestBid = bestBids.getOrDefault(figi, BigDecimal.ZERO);
                BigDecimal bestAsk = bestAsks.getOrDefault(figi, BigDecimal.ZERO);
                long bestBidQuantity = bestBidQuantities.getOrDefault(figi, 0L);
                long bestAskQuantity = bestAskQuantities.getOrDefault(figi, 0L);

                // Создаем данные о котировке
                QuoteData quoteData = new QuoteData(figi, instrumentNames.getOrDefault(figi, figi), // Используем
                                                                                                    // FIGI
                                                                                                    // если
                                                                                                    // имя
                                                                                                    // не
                                                                                                    // найдено
                        currentPrice, previousPrice, closePrice, null, closePriceVS, // closePriceOS,
                                                                                     // closePriceVS
                        bestBid, bestAsk, bestBidQuantity, bestAskQuantity, eventTime, 1L, // Для
                                                                                           // LastPrice
                                                                                           // объем
                                                                                           // = 1
                        1L, // totalVolume
                        direction);

                totalQuotesProcessed.incrementAndGet();

                // Отправляем всем подписчикам
                notifySubscribers(quoteData);

                // Передаем данные о цене в InstrumentPairService для расчета пар
                instrumentPairService.updateInstrumentPrice(figi, currentPrice, eventTime);

                if (log.isDebugEnabled()) {
                    log.debug("Processed LastPrice: {}", quoteData);
                }

            } catch (Exception e) {
                log.error("Error processing LastPrice for {}", price.getFigi(), e);
            }
        });
    }

    /**
     * Обработка данных о сделке с максимальной производительностью
     */
    public void processTrade(Trade trade) {
        processingExecutor.submit(() -> {
            try {
                String figi = trade.getFigi();

                // Фильтрация по настроенным инструментам
                if (!config.getInstruments().isEmpty() && !config.getInstruments().contains(figi)) {
                    log.debug("Filtering out Trade for instrument {} - not in configured list: {}",
                            figi, config.getInstruments());
                    return;
                }

                log.debug("Processing Trade for instrument {} - in configured list: {}", figi,
                        config.getInstruments());
                Instant eventInstant = Instant.ofEpochSecond(trade.getTime().getSeconds(),
                        trade.getTime().getNanos());
                LocalDateTime eventTime =
                        LocalDateTime.ofInstant(eventInstant, ZoneOffset.of("+3"));

                BigDecimal currentPrice = BigDecimal.valueOf(trade.getPrice().getUnits())
                        .add(BigDecimal.valueOf(trade.getPrice().getNano()).movePointLeft(9));

                // Получаем предыдущую цену для расчета разницы
                BigDecimal previousPrice = lastPrices.get(figi);

                // Обновляем последнюю цену
                lastPrices.put(figi, currentPrice);

                // Определяем направление сделки (преобразуем BUY/SELL в UP/DOWN для JavaScript)
                String direction = "NEUTRAL";
                if (trade.getDirection() == TradeDirection.TRADE_DIRECTION_BUY) {
                    direction = "UP";
                } else if (trade.getDirection() == TradeDirection.TRADE_DIRECTION_SELL) {
                    direction = "DOWN";
                }

                // Получаем цену закрытия за предыдущий торговый день
                BigDecimal closePrice = closePrices.get(figi);

                // Получаем цену закрытия вечерней сессии за предыдущий торговый день
                BigDecimal closePriceVS =
                        closePriceEveningSessionService.getEveningClosePrice(figi);

                // Получаем данные стакана для этого инструмента
                BigDecimal bestBid = bestBids.getOrDefault(figi, BigDecimal.ZERO);
                BigDecimal bestAsk = bestAsks.getOrDefault(figi, BigDecimal.ZERO);
                long bestBidQuantity = bestBidQuantities.getOrDefault(figi, 0L);
                long bestAskQuantity = bestAskQuantities.getOrDefault(figi, 0L);

                // Создаем данные о котировке
                QuoteData quoteData = new QuoteData(figi, instrumentNames.getOrDefault(figi, figi),
                        currentPrice, previousPrice, closePrice, null, closePriceVS, // closePriceOS,
                                                                                     // closePriceVS
                        bestBid, bestAsk, bestBidQuantity, bestAskQuantity, eventTime,
                        trade.getQuantity(), trade.getQuantity(), // volume, totalVolume
                        direction);

                totalQuotesProcessed.incrementAndGet();

                // Отправляем всем подписчикам
                notifySubscribers(quoteData);

                // Передаем данные о цене в InstrumentPairService для расчета пар
                instrumentPairService.updateInstrumentPrice(figi, currentPrice, eventTime);

                if (log.isDebugEnabled()) {
                    log.debug("Processed Trade: {}", quoteData);
                }

            } catch (Exception e) {
                log.error("Error processing Trade for {}", trade.getFigi(), e);
            }
        });
    }

    /**
     * Уведомление всех подписчиков о новой котировке
     */
    private void notifySubscribers(QuoteData quoteData) {
        log.debug("Notifying {} subscribers about quote data: {}", quoteSubscribers.size(),
                quoteData);

        // Проверяем, включена ли WebSocket трансляция
        if (!config.isEnableWebSocketBroadcast()) {
            log.debug("WebSocket broadcast is disabled, skipping notification");
            return;
        }

        // Если нет подписчиков, не тратим время на обработку
        if (quoteSubscribers.isEmpty()) {
            log.debug("No subscribers available, skipping notification");
            return;
        }

        int notifiedCount = 0;
        for (Consumer<QuoteData> subscriber : quoteSubscribers) {
            try {
                subscriber.accept(quoteData);
                totalQuotesSent.incrementAndGet();
                notifiedCount++;
                log.debug("Successfully notified subscriber {}", notifiedCount);
            } catch (Exception e) {
                log.warn("Error notifying subscriber", e);
            }
        }
        log.debug("Notified {} subscribers successfully", notifiedCount);
    }

    /**
     * Подписка на обновления котировок
     */
    public void subscribeToQuotes(Consumer<QuoteData> subscriber) {
        quoteSubscribers.add(subscriber);
        log.info("New quote subscriber added. Total subscribers: {}", quoteSubscribers.size());
    }

    /**
     * Отписка от обновлений котировок
     */
    public void unsubscribeFromQuotes(Consumer<QuoteData> subscriber) {
        quoteSubscribers.remove(subscriber);
        log.info("Quote subscriber removed. Total subscribers: {}", quoteSubscribers.size());
    }

    /**
     * Очистка неактивных подписчиков
     */
    private void cleanupInactiveSubscribers() {
        int initialSize = quoteSubscribers.size();
        if (initialSize > 0) {
            log.debug("Cleaning up inactive subscribers. Current count: {}", initialSize);
            // Здесь можно добавить логику для проверки активности подписчиков
            // Пока что просто логируем количество подписчиков
            log.debug("Active subscribers: {}", quoteSubscribers.size());
        }
    }

    /**
     * Установка имен инструментов для отображения
     */
    public void setInstrumentNames(Map<String, String> names) {
        instrumentNames.putAll(names);
        log.info("Updated instrument names: {} instruments", names.size());
    }


    /**
     * Получение статистики сервиса
     */
    public Map<String, Object> getStats() {
        return Map.of("totalQuotesProcessed", totalQuotesProcessed.get(), "totalQuotesSent",
                totalQuotesSent.get(), "activeSubscribers", quoteSubscribers.size(),
                "trackedInstruments", lastPrices.size(), "instruments", config.getInstruments(),
                "availableInstruments", lastPrices.size());
    }

    /**
     * Получение всех доступных инструментов с ценами
     */
    public Map<String, BigDecimal> getAvailableInstruments() {
        return new HashMap<>(lastPrices);
    }

    /**
     * Получение всех доступных инструментов с именами
     */
    public Map<String, String> getAvailableInstrumentNames() {
        return new HashMap<>(instrumentNames);
    }

    /**
     * Получение текущих цен в виде Map для REST API
     */
    public Map<String, Object> getCurrentPrices() {
        return Map.of("prices", Map.copyOf(lastPrices), "instrumentNames",
                Map.copyOf(instrumentNames), "count", lastPrices.size());
    }

    /**
     * Получение списка отслеживаемых инструментов
     */
    public Set<String> getInstruments() {
        return Set.copyOf(config.getInstruments());
    }

    /**
     * Обработка данных стакана заявок из T-Invest API
     */
    public void processOrderBook(OrderBook orderBook) {
        processingExecutor.submit(() -> {
            try {
                String figi = orderBook.getFigi();

                // Фильтрация по настроенным инструментам
                if (!config.getInstruments().isEmpty() && !config.getInstruments().contains(figi)) {
                    log.debug(
                            "Filtering out OrderBook for instrument {} - not in configured list: {}",
                            figi, config.getInstruments());
                    return;
                }

                log.debug("Processing OrderBook for instrument {} - in configured list: {}", figi,
                        config.getInstruments());

                // Получаем лучший BID (первая заявка на покупку)
                BigDecimal bestBid = BigDecimal.ZERO;
                long bestBidQuantity = 0;
                if (orderBook.getBidsCount() > 0) {
                    var bestBidOrder = orderBook.getBids(0);
                    bestBid = BigDecimal.valueOf(bestBidOrder.getPrice().getUnits()).add(
                            BigDecimal.valueOf(bestBidOrder.getPrice().getNano()).movePointLeft(9));
                    bestBidQuantity = bestBidOrder.getQuantity();
                }

                // Получаем лучший ASK (первая заявка на продажу)
                BigDecimal bestAsk = BigDecimal.ZERO;
                long bestAskQuantity = 0;
                if (orderBook.getAsksCount() > 0) {
                    var bestAskOrder = orderBook.getAsks(0);
                    bestAsk = BigDecimal.valueOf(bestAskOrder.getPrice().getUnits()).add(
                            BigDecimal.valueOf(bestAskOrder.getPrice().getNano()).movePointLeft(9));
                    bestAskQuantity = bestAskOrder.getQuantity();
                }

                // Обновляем данные стакана в кеше
                bestBids.put(figi, bestBid);
                bestAsks.put(figi, bestAsk);
                bestBidQuantities.put(figi, bestBidQuantity);
                bestAskQuantities.put(figi, bestAskQuantity);

                log.debug("Order book processed for FIGI: {}, BID: {} ({}), ASK: {} ({})", figi,
                        bestBid, bestBidQuantity, bestAsk, bestAskQuantity);

            } catch (Exception e) {
                log.error("Error processing order book for FIGI: {}", orderBook.getFigi(), e);
            }
        });
    }

    /**
     * Обработка данных стакана заявок (устаревший метод для совместимости)
     */
    public void processOrderBook(String figi, BigDecimal bestBid, BigDecimal bestAsk,
            long bestBidQuantity, long bestAskQuantity) {
        try {
            log.debug("Processing order book for FIGI: {}, BID: {} ({}), ASK: {} ({})", figi,
                    bestBid, bestBidQuantity, bestAsk, bestAskQuantity);

            // Обновляем данные стакана в кеше
            bestBids.put(figi, bestBid);
            bestAsks.put(figi, bestAsk);
            bestBidQuantities.put(figi, bestBidQuantity);
            bestAskQuantities.put(figi, bestAskQuantity);

            log.debug("Order book processed for FIGI: {}", figi);

        } catch (Exception e) {
            log.error("Error processing order book for FIGI: {}", figi, e);
        }
    }

    /**
     * Статистика сканера котировок
     */
    public static class ScannerStats {
        private final long totalQuotesProcessed;
        private final long totalQuotesSent;
        private final int activeSubscribers;
        private final int trackedInstruments;

        public ScannerStats(long totalQuotesProcessed, long totalQuotesSent, int activeSubscribers,
                int trackedInstruments) {
            this.totalQuotesProcessed = totalQuotesProcessed;
            this.totalQuotesSent = totalQuotesSent;
            this.activeSubscribers = activeSubscribers;
            this.trackedInstruments = trackedInstruments;
        }

        public long getTotalQuotesProcessed() {
            return totalQuotesProcessed;
        }

        public long getTotalQuotesSent() {
            return totalQuotesSent;
        }

        public int getActiveSubscribers() {
            return activeSubscribers;
        }

        public int getTrackedInstruments() {
            return trackedInstruments;
        }
    }
}
