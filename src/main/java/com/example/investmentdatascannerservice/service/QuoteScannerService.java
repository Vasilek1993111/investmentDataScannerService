package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import com.example.investmentdatascannerservice.dto.QuoteData;
import com.example.investmentdatascannerservice.utils.ClosePriceEveningSessionService;
import com.example.investmentdatascannerservice.utils.ClosePriceService;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import com.example.investmentdatascannerservice.utils.SessionTimeService;
import com.example.investmentdatascannerservice.utils.ShareService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.OrderBook;
import ru.tinkoff.piapi.contract.v1.Trade;

/**
 * Рефакторированный высокопроизводительный сервис для сканирования котировок
 * 
 * Использует разделение ответственности и делегирует обработку специализированным сервисам для
 * максимальной производительности и поддерживаемости
 */
@Service
public class QuoteScannerService {

    private static final Logger log = LoggerFactory.getLogger(QuoteScannerService.class);

    // Основные сервисы
    private final QuoteScannerConfig config;
    private final MarketDataProcessor marketDataProcessor;
    private final NotificationService notificationService;
    private final InstrumentCacheService instrumentCacheService;
    private final SessionTimeService sessionTimeService;
    private final InstrumentPairService instrumentPairService;
    private final MeterRegistry meterRegistry;

    // Вспомогательные сервисы для инициализации
    private final ClosePriceService closePriceService;
    private final ClosePriceEveningSessionService closePriceEveningSessionService;
    private final ShareService shareService;

    // Динамический список индексов для полоски
    private final List<IndexConfig> dynamicIndices = new CopyOnWriteArrayList<>();

    // Планировщик для периодических задач
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Статистика
    private final AtomicLong totalQuotesProcessed = new AtomicLong(0);
    private final AtomicLong totalQuotesSent = new AtomicLong(0);

    public QuoteScannerService(QuoteScannerConfig config, MarketDataProcessor marketDataProcessor,
            NotificationService notificationService, InstrumentCacheService instrumentCacheService,
            SessionTimeService sessionTimeService, InstrumentPairService instrumentPairService,
            MeterRegistry meterRegistry, ClosePriceService closePriceService,
            ClosePriceEveningSessionService closePriceEveningSessionService,
            ShareService shareService) {
        this.config = config;
        this.marketDataProcessor = marketDataProcessor;
        this.notificationService = notificationService;
        this.instrumentCacheService = instrumentCacheService;
        this.sessionTimeService = sessionTimeService;
        this.instrumentPairService = instrumentPairService;
        this.meterRegistry = meterRegistry;
        this.closePriceService = closePriceService;
        this.closePriceEveningSessionService = closePriceEveningSessionService;
        this.shareService = shareService;
    }

    @PostConstruct
    public void init() {
        log.info("=== REFACTORED QUOTE SCANNER SERVICE INITIALIZATION ===");
        log.info("Initializing refactored QuoteScannerService with specialized components");
        log.info("Max quotes per second: {}", config.getMaxQuotesPerSecond());
        log.info("Database saving enabled: {}", config.isEnableDatabaseSaving());
        log.info("WebSocket broadcast enabled: {}", config.isEnableWebSocketBroadcast());

        // Получаем инструменты для сканирования (из базы данных)
        List<String> instrumentsForScanning = instrumentCacheService.getInstrumentsForScanning();
        log.info("Instruments for scanning: {}", instrumentsForScanning.size());
        if (!instrumentsForScanning.isEmpty()) {
            log.info("First 5 instruments: {}",
                    instrumentsForScanning.subList(0, Math.min(5, instrumentsForScanning.size())));
        }

        // Инициализируем кэш инструментов
        instrumentCacheService.initializeCache();

        // Очищаем только накопленные объемы (сохраняем уже проторгованные)
        instrumentCacheService.loadWeekendExchangeVolumes();
        log.info("Accumulated volumes reset for new session (preserving weekend exchange volumes)");

        // Загружаем цены закрытия за предыдущий торговый день
        loadClosePrices();

        // Загружаем цены закрытия вечерней сессии за предыдущий торговый день
        loadEveningClosePrices();

        // Загрузка средних объемов отключена: таблица shares_aggregated_data больше не существует

        // Инициализируем список индексов по умолчанию
        initializeDefaultIndices();

        // Запускаем периодическую очистку неактивных подписчиков каждые 30 секунд
        scheduler.scheduleAtFixedRate(this::cleanupInactiveSubscribers, 30, 30, TimeUnit.SECONDS);

        // Запускаем периодическую проверку времени утренней сессии каждые 10 секунд
        scheduler.scheduleAtFixedRate(this::startScannerIfSessionTime, 0, 10, TimeUnit.SECONDS);

        log.info("Refactored QuoteScannerService initialized successfully");
        log.info("=============================================");
    }

    /**
     * Загружает цены закрытия за предыдущий торговый день для всех акций из базы данных
     */
    private void loadClosePrices() {
        try {
            log.info("Loading close prices for previous trading day...");
            // Получаем все FIGI акций из базы данных
            List<String> allShareFigis = shareService.getAllShareFigis();
            Map<String, BigDecimal> loadedClosePrices =
                    closePriceService.getClosePricesForPreviousDay(allShareFigis);
            instrumentCacheService.loadClosePrices(loadedClosePrices);
            log.info("Loaded {} close prices for previous trading day from {} shares",
                    loadedClosePrices.size(), allShareFigis.size());

            if (!loadedClosePrices.isEmpty()) {
                log.info("First 5 close prices: {}",
                        loadedClosePrices.entrySet().stream().limit(5)
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.error("Error loading close prices", e);
        }
    }


    /**
     * Инициализирует список индексов по умолчанию
     */
    private void initializeDefaultIndices() {
        dynamicIndices.clear();
        dynamicIndices.add(new IndexConfig("BBG00KDWPPW3", "IMOEX2", "IMOEX2"));
        dynamicIndices.add(new IndexConfig("BBG004730N9", "IMOEX", "IMOEX"));
        dynamicIndices.add(new IndexConfig("BBG004730Z0", "RTSI", "RTSI"));
        dynamicIndices.add(new IndexConfig("BBG0013HGFT4", "XAG", "XAG"));
        dynamicIndices.add(new IndexConfig("BBG0013HJJ31", "XAU", "XAU"));
        dynamicIndices.add(new IndexConfig("BBG0013HGJ36", "XPD", "XPD"));
        dynamicIndices.add(new IndexConfig("BBG0013HGJ44", "XPT", "XPT"));

        log.info("Initialized {} default indices", dynamicIndices.size());
    }

    /**
     * Загружает цены закрытия вечерней сессии за предыдущий торговый день для всех акций из базы
     * данных
     */
    private void loadEveningClosePrices() {
        try {
            log.info("Loading evening close prices for previous trading day...");
            // Получаем все FIGI акций из базы данных
            List<String> allShareFigis = shareService.getAllShareFigis();
            Map<String, BigDecimal> loadedEveningClosePrices = closePriceEveningSessionService
                    .loadEveningClosePricesForPreviousDay(allShareFigis);
            log.info("Loaded {} evening close prices for previous trading day from {} shares",
                    loadedEveningClosePrices.size(), allShareFigis.size());
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
        log.info("Shutting down refactored QuoteScannerService...");
        scheduler.shutdown();
        log.info("Refactored QuoteScannerService shutdown completed. Total processed: {} quotes",
                totalQuotesProcessed.get());
    }

    /**
     * Обработка данных о последней цене - делегируем MarketDataProcessor
     */
    public void processLastPrice(LastPrice price) {
        log.debug("Delegating LastPrice processing to MarketDataProcessor for FIGI: {}",
                price.getFigi());
        marketDataProcessor.processLastPrice(price);
        totalQuotesProcessed.incrementAndGet();
    }

    /**
     * Обработка данных о сделке - делегируем MarketDataProcessor
     */
    public void processTrade(Trade trade) {
        log.debug("Delegating Trade processing to MarketDataProcessor for FIGI: {}",
                trade.getFigi());
        marketDataProcessor.processTrade(trade);
        totalQuotesProcessed.incrementAndGet();
    }

    /**
     * Подписка на обновления котировок - делегируем NotificationService
     */
    public void subscribeToQuotes(Consumer<QuoteData> subscriber) {
        notificationService.subscribe(subscriber);
        log.info("Subscribed to quotes via NotificationService. Total subscribers: {}",
                notificationService.getSubscriberCount());
    }

    /**
     * Отписка от обновлений котировок - делегируем NotificationService
     */
    public void unsubscribeFromQuotes(Consumer<QuoteData> subscriber) {
        notificationService.unsubscribe(subscriber);
        log.info("Unsubscribed from quotes via NotificationService. Total subscribers: {}",
                notificationService.getSubscriberCount());
    }

    /**
     * Очистка неактивных подписчиков - делегируем NotificationService
     */
    private void cleanupInactiveSubscribers() {
        int subscriberCount = notificationService.getSubscriberCount();
        if (subscriberCount > 0) {
            log.debug("Current active subscribers: {}", subscriberCount);
        } else {
            log.debug("No active subscribers");
        }
        // Примечание: Реальная очистка происходит в QuoteWebSocketController:
        // - При закрытии соединения (afterConnectionClosed)
        // - При ошибках отправки (broadcastQuote удаляет закрытые сессии)
    }

    /**
     * Установка имен инструментов для отображения
     */
    public void setInstrumentNames(Map<String, String> names) {
        instrumentCacheService.loadInstrumentNames(names);
        log.info("Updated instrument names: {} instruments", names.size());
    }


    /**
     * Получение статистики сервиса - агрегируем данные из всех компонентов
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Основная статистика
        stats.put("totalQuotesProcessed", totalQuotesProcessed.get());
        stats.put("sharesMode", config.isEnableSharesMode());
        stats.put("trackedInstruments", instrumentCacheService.getTrackedInstruments().size());

        // Статистика MarketDataProcessor
        stats.put("marketDataProcessor", marketDataProcessor.getStats());

        // Статистика NotificationService
        stats.put("notificationService", notificationService.getStats());

        // Статистика кэша
        stats.put("cacheStats", instrumentCacheService.getCacheStats());

        // Информация о сессии
        stats.put("sessionInfo", sessionTimeService.getCurrentSessionInfo());
        stats.put("isScannerActive", isScannerActive());

        return stats;
    }

    /**
     * Получение всех доступных инструментов с ценами
     */
    public Map<String, BigDecimal> getAvailableInstruments() {
        return instrumentCacheService.getAvailableInstruments();
    }

    /**
     * Получение всех доступных инструментов с именами
     */
    public Map<String, String> getAvailableInstrumentNames() {
        return instrumentCacheService.getAvailableInstrumentNames();
    }

    /**
     * Получение текущих цен в виде Map для REST API
     */
    public Map<String, Object> getCurrentPrices() {
        return Map.of("prices", instrumentCacheService.getAvailableInstruments(), "instrumentNames",
                instrumentCacheService.getAvailableInstrumentNames(), "count",
                instrumentCacheService.getTrackedInstruments().size());
    }

    /**
     * Получение списка отслеживаемых инструментов
     */
    public Set<String> getInstruments() {
        return instrumentCacheService.getTrackedInstruments();
    }

    /**
     * Обработка данных стакана заявок - делегируем MarketDataProcessor
     */
    public void processOrderBook(OrderBook orderBook) {
        log.debug("Delegating OrderBook processing to MarketDataProcessor for FIGI: {}",
                orderBook.getFigi());
        marketDataProcessor.processOrderBook(orderBook);
    }


    /**
     * Статистика рефакторированного сканера котировок
     */
    public static class ScannerStats {
        private final long totalQuotesProcessed;
        private final int activeSubscribers;
        private final int trackedInstruments;
        private final String sessionInfo;
        private final boolean isScannerActive;

        public ScannerStats(long totalQuotesProcessed, int activeSubscribers,
                int trackedInstruments, String sessionInfo, boolean isScannerActive) {
            this.totalQuotesProcessed = totalQuotesProcessed;
            this.activeSubscribers = activeSubscribers;
            this.trackedInstruments = trackedInstruments;
            this.sessionInfo = sessionInfo;
            this.isScannerActive = isScannerActive;
        }

        public long getTotalQuotesProcessed() {
            return totalQuotesProcessed;
        }

        public int getActiveSubscribers() {
            return activeSubscribers;
        }

        public int getTrackedInstruments() {
            return trackedInstruments;
        }

        public String getSessionInfo() {
            return sessionInfo;
        }

        public boolean isScannerActive() {
            return isScannerActive;
        }
    }


    /**
     * Запускает сканер, если время утренней сессии - делегируем SessionTimeService
     */
    public void startScannerIfSessionTime() {
        if (sessionTimeService.isMorningSessionTime()) {
            if (config.isEnableTestMode()) {
                log.info("Morning session detected - TEST MODE ENABLED");
            } else {
                log.info("Morning session detected - scanner ready");
            }
        } else {
            if (config.isEnableTestMode()) {
                log.info("Outside morning session - TEST MODE ENABLED");
            } else {
                log.info("Outside morning session - scanner inactive");
            }
        }
    }

    /**
     * Запускает сканер, если время сессии выходного дня - делегируем SessionTimeService
     */
    public void startScannerIfWeekendSessionTime() {
        if (sessionTimeService.isWeekendSessionTime()) {
            if (config.isEnableTestMode()) {
                log.info("Weekend session detected - TEST MODE ENABLED");
            } else {
                log.info("Weekend session detected - scanner ready");
            }
        } else {
            if (config.isEnableTestMode()) {
                log.info("Outside weekend session - TEST MODE ENABLED");
            } else {
                log.info("Outside weekend session - scanner inactive");
            }
        }
    }

    /**
     * Проверяет, активен ли сканер - делегируем SessionTimeService
     */
    public boolean isScannerActive() {
        return sessionTimeService.isAnySessionActive();
    }

    /**
     * Проверяет, является ли текущее время сессией выходного дня - делегируем SessionTimeService
     */
    public boolean checkWeekendSessionTime() {
        return sessionTimeService.checkWeekendSessionTime();
    }

    /**
     * Останавливает сканер принудительно - в рефакторированной версии не нужен
     */
    public void stopScanner() {
        log.info("Stop scanner requested - session management delegated to SessionTimeService");
    }

    /**
     * Получить текущий список индексов
     */
    public List<Map<String, String>> getCurrentIndices() {
        return dynamicIndices.stream().map(config -> {
            Map<String, String> index = new HashMap<>();
            index.put("name", config.ticker);
            index.put("displayName", config.displayName);
            return index;
        }).collect(Collectors.toList());
    }

    /**
     * Добавить новый индекс (старый метод с FIGI)
     */
    public boolean addIndex(String figi, String ticker, String displayName) {
        // Проверяем, не существует ли уже индекс с таким ticker
        boolean exists = dynamicIndices.stream().anyMatch(config -> config.ticker.equals(ticker));

        if (exists) {
            log.warn("Index with ticker '{}' already exists", ticker);
            return false;
        }

        // Добавляем новый индекс
        IndexConfig newIndex = new IndexConfig(figi, ticker, displayName);
        dynamicIndices.add(newIndex);

        log.info("Added new index: {} (FIGI: {})", ticker, figi);
        return true;
    }

    /**
     * Добавить новый индекс (только по тикеру)
     */
    public boolean addIndex(String name, String displayName) {
        // Проверяем, не существует ли уже индекс с таким ticker
        boolean exists = dynamicIndices.stream().anyMatch(config -> config.ticker.equals(name));

        if (exists) {
            log.warn("Index with ticker '{}' already exists", name);
            return false;
        }

        // Добавляем новый индекс (используем name как figi для совместимости)
        IndexConfig newIndex = new IndexConfig(name, name, displayName);
        dynamicIndices.add(newIndex);

        log.info("Added new index: {} (displayName: {})", name, displayName);

        // Уведомляем о необходимости обновить подписку
        notifySubscriptionUpdate();

        return true;
    }

    /**
     * Удалить индекс по ticker
     */
    public boolean removeIndex(String ticker) {
        boolean removed = dynamicIndices.removeIf(config -> config.ticker.equals(ticker));

        if (removed) {
            log.info("Removed index: {}", ticker);
            // Уведомляем о необходимости обновить подписку
            notifySubscriptionUpdate();
        } else {
            log.warn("Index with ticker '{}' not found", ticker);
        }

        return removed;
    }

    /**
     * Класс для конфигурации индекса
     */
    public static class IndexConfig {
        public final String figi;
        public final String ticker;
        public final String displayName;

        public IndexConfig(String figi, String ticker, String displayName) {
            this.figi = figi;
            this.ticker = ticker;
            this.displayName = displayName;
        }
    }



    /**
     * Получить ShareService для доступа к акциям
     */
    public ShareService getShareService() {
        return instrumentCacheService.getShareService();
    }

    /**
     * Получить InstrumentCacheService для доступа к кэшу инструментов
     */
    public InstrumentCacheService getInstrumentCacheService() {
        return instrumentCacheService;
    }

    /**
     * Получить инструменты для сканирования включая динамические индексы
     */
    public List<String> getInstrumentsForScanning() {
        List<String> baseInstruments = instrumentCacheService.getInstrumentsForScanning();

        // Добавляем динамические индексы
        List<String> dynamicIndicesFigis =
                dynamicIndices.stream().map(config -> config.figi).collect(Collectors.toList());

        List<String> allInstruments = new ArrayList<>();
        allInstruments.addAll(baseInstruments);
        allInstruments.addAll(dynamicIndicesFigis);

        log.debug("Total instruments for scanning: {} (base: {}, dynamic: {})",
                allInstruments.size(), baseInstruments.size(), dynamicIndicesFigis.size());

        return allInstruments;
    }

    /**
     * Уведомить о необходимости обновить подписку
     */
    private void notifySubscriptionUpdate() {
        // Здесь можно добавить логику для уведомления MarketDataStreamingService
        // о необходимости обновить подписку на инструменты
        log.info("Subscription update notification sent - {} dynamic indices available",
                dynamicIndices.size());
    }
}
