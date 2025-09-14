package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
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
    private final SharesAggregatedDataService sharesAggregatedDataService;
    private final ShareService shareService;
    private final IndicativeService indicativeService;

    // Хранилище последних цен для расчета разниц
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();
    private final Map<String, String> instrumentNames = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> avgVolumeMorningMap = new ConcurrentHashMap<>();

    // Кэш для средних объемов выходного дня
    private final Map<String, BigDecimal> avgVolumeWeekendMap = new ConcurrentHashMap<>();
    private final Map<String, String> instrumentTickers = new ConcurrentHashMap<>();

    // Динамический список индексов для полоски
    private final List<IndexConfig> dynamicIndices = new CopyOnWriteArrayList<>();
    private final Map<String, BigDecimal> closePrices = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> openPrices = new ConcurrentHashMap<>();
    private final Map<String, Long> accumulatedVolumes = new ConcurrentHashMap<>();

    // Хранилище данных стакана заявок
    private final Map<String, BigDecimal> bestBids = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> bestAsks = new ConcurrentHashMap<>();
    private final Map<String, Long> bestBidQuantities = new ConcurrentHashMap<>();
    private final Map<String, Long> bestAskQuantities = new ConcurrentHashMap<>();

    // Подписчики на обновления котировок (для WebSocket)
    private final Set<Consumer<QuoteData>> quoteSubscribers = new CopyOnWriteArraySet<>();

    // Время утренней сессии (Московское время)
    private static final int MORNING_SESSION_START_HOUR = 6;
    private static final int MORNING_SESSION_START_MINUTE = 50;
    private static final int MORNING_SESSION_END_HOUR = 9;
    private static final int MORNING_SESSION_END_MINUTE = 49;
    private static final int MORNING_SESSION_END_SECOND = 59;

    // Время сессий выходного дня (суббота и воскресенье, Московское время)
    private static final int WEEKEND_SESSION_START_HOUR = 2;
    private static final int WEEKEND_SESSION_END_HOUR = 23;
    private static final int WEEKEND_SESSION_END_MINUTE = 50;

    // Потоки для обработки
    private final ExecutorService processingExecutor =
            Executors.newFixedThreadPool(PROCESSING_THREADS);

    // Планировщик для периодических задач
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Флаг активности сканера
    private volatile boolean isScannerActive = false;

    // Статистика
    private final AtomicLong totalQuotesProcessed = new AtomicLong(0);
    private final AtomicLong totalQuotesSent = new AtomicLong(0);

    public QuoteScannerService(QuoteScannerConfig config,
            InstrumentPairService instrumentPairService, ClosePriceService closePriceService,
            ClosePriceEveningSessionService closePriceEveningSessionService,
            SharesAggregatedDataService sharesAggregatedDataService, ShareService shareService,
            IndicativeService indicativeService) {
        this.config = config;
        this.instrumentPairService = instrumentPairService;
        this.closePriceService = closePriceService;
        this.closePriceEveningSessionService = closePriceEveningSessionService;
        this.sharesAggregatedDataService = sharesAggregatedDataService;
        this.shareService = shareService;
        this.indicativeService = indicativeService;
    }

    @PostConstruct
    public void init() {
        log.info("=== QUOTE SCANNER SERVICE INITIALIZATION ===");
        log.info("Initializing QuoteScannerService with {} processing threads", PROCESSING_THREADS);
        log.info("Max quotes per second: {}", config.getMaxQuotesPerSecond());
        log.info("Database saving enabled: {}", config.isEnableDatabaseSaving());
        log.info("WebSocket broadcast enabled: {}", config.isEnableWebSocketBroadcast());
        // Получаем инструменты для сканирования (из базы данных)
        List<String> instrumentsForScanning = getInstrumentsForScanning();
        log.info("Instruments for scanning: {}", instrumentsForScanning.size());
        if (!instrumentsForScanning.isEmpty()) {
            log.info("First 5 instruments: {}",
                    instrumentsForScanning.subList(0, Math.min(5, instrumentsForScanning.size())));
        }

        // Загружаем имена инструментов в зависимости от режима
        Map<String, String> namesToLoad = getInstrumentNamesForScanning();
        instrumentNames.putAll(namesToLoad);
        log.info("Loaded {} instrument names into cache", instrumentNames.size());

        // Загружаем тикеры инструментов в зависимости от режима
        Map<String, String> tickersToLoad = getInstrumentTickersForScanning();
        instrumentTickers.putAll(tickersToLoad);
        log.info("Loaded {} tickers into cache", instrumentTickers.size());
        if (!instrumentTickers.isEmpty()) {
            log.info("First 5 tickers in cache: {}",
                    instrumentTickers.entrySet().stream().limit(5)
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.toList()));
        }

        // Сбрасываем накопленный объем при инициализации
        accumulatedVolumes.clear();
        log.info("Accumulated volumes reset for new session");

        // Загружаем цены закрытия за предыдущий торговый день
        loadClosePrices();

        // Загружаем цены закрытия вечерней сессии за предыдущий торговый день
        loadEveningClosePrices();

        // Загружаем средние утренние объемы
        loadAvgVolumeMorning();

        // Загружаем средние объемы выходного дня
        loadAvgVolumeWeekend();

        // Инициализируем список индексов по умолчанию
        initializeDefaultIndices();

        // Запускаем периодическую очистку неактивных подписчиков каждые 30 секунд
        scheduler.scheduleAtFixedRate(this::cleanupInactiveSubscribers, 30, 30, TimeUnit.SECONDS);

        // Запускаем периодическую проверку времени утренней сессии каждые 10 секунд
        scheduler.scheduleAtFixedRate(this::startScannerIfSessionTime, 0, 10, TimeUnit.SECONDS);

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
            closePrices.putAll(loadedClosePrices);
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
     * Загружает средние утренние объемы для всех акций из базы данных
     */
    private void loadAvgVolumeMorning() {
        try {
            log.info("Loading average morning volumes...");
            // Получаем все FIGI акций из базы данных
            List<String> allShareFigis = shareService.getAllShareFigis();
            Map<String, BigDecimal> loadedAvgVolumes =
                    sharesAggregatedDataService.getAvgVolumeMorningMap(allShareFigis);
            avgVolumeMorningMap.putAll(loadedAvgVolumes);
            log.info("Loaded {} average morning volumes from {} shares", loadedAvgVolumes.size(),
                    allShareFigis.size());

            if (!loadedAvgVolumes.isEmpty()) {
                log.info("First 5 average morning volumes: {}",
                        loadedAvgVolumes.entrySet().stream().limit(5)
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.error("Error loading average morning volumes", e);
        }
    }

    /**
     * Загружает средние объемы выходного дня для всех акций из базы данных
     */
    private void loadAvgVolumeWeekend() {
        try {
            log.info("Loading average weekend volumes...");
            // Получаем все FIGI акций из базы данных
            List<String> allShareFigis = shareService.getAllShareFigis();
            Map<String, BigDecimal> loadedAvgVolumes =
                    sharesAggregatedDataService.getAvgVolumeWeekendMap(allShareFigis);
            avgVolumeWeekendMap.putAll(loadedAvgVolumes);
            log.info("Loaded {} average weekend volumes from {} shares", loadedAvgVolumes.size(),
                    allShareFigis.size());

            if (!loadedAvgVolumes.isEmpty()) {
                log.info("First 5 average weekend volumes: {}",
                        loadedAvgVolumes.entrySet().stream().limit(5)
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.error("Error loading average weekend volumes", e);
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
        // Проверяем, активен ли сканер (время утренней сессии)
        if (!isScannerActive) {
            log.debug("Scanner is not active, skipping LastPrice for {}", price.getFigi());
            return;
        }

        log.debug("Processing LastPrice for FIGI: {}", price.getFigi());

        processingExecutor.submit(() -> {
            try {
                String figi = price.getFigi();

                // Фильтрация не нужна - обрабатываем все акции из базы данных

                log.debug("Processing LastPrice for instrument {} - shares mode: {}", figi,
                        config.isEnableSharesMode());
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

                // Если это первая цена за день, сохраняем как цену открытия
                if (!openPrices.containsKey(figi)) {
                    openPrices.put(figi, currentPrice);
                }

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

                // Получаем цену закрытия за предыдущий торговый день (основная сессия)
                BigDecimal closePrice = closePrices.get(figi);
                BigDecimal closePriceOS = closePrice; // Используем цену закрытия как цену ОС

                // Получаем цену закрытия вечерней сессии за предыдущий торговый день
                BigDecimal closePriceVS =
                        closePriceEveningSessionService.getEveningClosePrice(figi);

                // Получаем данные стакана для этого инструмента
                BigDecimal bestBid = bestBids.getOrDefault(figi, BigDecimal.ZERO);
                BigDecimal bestAsk = bestAsks.getOrDefault(figi, BigDecimal.ZERO);
                long bestBidQuantity = bestBidQuantities.getOrDefault(figi, 0L);
                long bestAskQuantity = bestAskQuantities.getOrDefault(figi, 0L);

                // Получаем накопленный объем для этого инструмента
                long accumulatedVolume = accumulatedVolumes.getOrDefault(figi, 0L);

                // Получаем тикер для инструмента
                String ticker = instrumentTickers.getOrDefault(figi, figi);
                String instrumentName = instrumentNames.getOrDefault(figi, figi);

                // Логируем первые несколько инструментов для отладки
                if (totalQuotesProcessed.get() < 5) {
                    log.info(
                            "Creating QuoteData for FIGI: {}, ticker: {}, name: {}, closePrice: {}, closePriceOS: {}",
                            figi, ticker, instrumentName, closePrice, closePriceOS);
                }

                // Создаем данные о котировке
                QuoteData quoteData = new QuoteData(figi, ticker, // Используем тикер или FIGI если
                                                                  // не найден
                        instrumentName, // Используем имя или FIGI если не найдено
                        currentPrice, previousPrice, closePrice, openPrices.get(figi), closePriceOS,
                        closePriceVS, // openPrice, closePriceOS (используем цену закрытия),
                                      // closePriceVS
                        bestBid, bestAsk, bestBidQuantity, bestAskQuantity, eventTime, 0L, // Для
                                                                                           // LastPrice
                                                                                           // объем
                                                                                           // =
                                                                                           // 0
                        accumulatedVolume, // totalVolume (накопленный объем)
                        direction, avgVolumeMorningMap.get(figi), avgVolumeWeekendMap.get(figi)); // avgVolumeMorning,
                                                                                                  // avgVolumeWeekend

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
        // Проверяем, активен ли сканер (время утренней сессии)
        if (!isScannerActive) {
            return;
        }

        processingExecutor.submit(() -> {
            try {
                String figi = trade.getFigi();

                // Фильтрация не нужна - обрабатываем все акции из базы данных

                log.debug("Processing Trade for instrument {} - shares mode: {}", figi,
                        config.isEnableSharesMode());
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

                // Получаем цену закрытия за предыдущий торговый день (основная сессия)
                BigDecimal closePrice = closePrices.get(figi);
                BigDecimal closePriceOS = closePrice; // Используем цену закрытия как цену ОС

                // Получаем цену закрытия вечерней сессии за предыдущий торговый день
                BigDecimal closePriceVS =
                        closePriceEveningSessionService.getEveningClosePrice(figi);

                // Получаем данные стакана для этого инструмента
                BigDecimal bestBid = bestBids.getOrDefault(figi, BigDecimal.ZERO);
                BigDecimal bestAsk = bestAsks.getOrDefault(figi, BigDecimal.ZERO);
                long bestBidQuantity = bestBidQuantities.getOrDefault(figi, 0L);
                long bestAskQuantity = bestAskQuantities.getOrDefault(figi, 0L);

                // Накопляем объем за время работы сканера только во время сессий выходного дня
                long tradeQuantity = trade.getQuantity();
                long currentAccumulatedVolume = accumulatedVolumes.getOrDefault(figi, 0L);
                long newAccumulatedVolume = currentAccumulatedVolume;

                // Добавляем объем только если сейчас сессия выходного дня
                if (isWeekendSessionTime()) {
                    newAccumulatedVolume = currentAccumulatedVolume + tradeQuantity;
                    accumulatedVolumes.put(figi, newAccumulatedVolume);
                }

                // Создаем данные о котировке
                QuoteData quoteData = new QuoteData(figi,
                        instrumentTickers.getOrDefault(figi, figi), // Используем
                                                                    // тикер или
                                                                    // FIGI если
                                                                    // не найден
                        instrumentNames.getOrDefault(figi, figi), // Используем имя или FIGI
                                                                  // если не найдено
                        currentPrice, previousPrice, closePrice, openPrices.get(figi), closePriceOS,
                        closePriceVS, // openPrice, closePriceOS (используем
                                      // цену закрытия), closePriceVS
                        bestBid, bestAsk, bestBidQuantity, bestAskQuantity, eventTime,
                        tradeQuantity, newAccumulatedVolume, // volume (текущая сделка),
                                                             // totalVolume (накопленный)
                        direction, avgVolumeMorningMap.get(figi), avgVolumeWeekendMap.get(figi)); // avgVolumeMorning,
                                                                                                  // avgVolumeWeekend

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
        log.info("Notifying {} subscribers about quote data for {}: {}", quoteSubscribers.size(),
                quoteData.getTicker(), quoteData);

        // Проверяем, включена ли WebSocket трансляция
        if (!config.isEnableWebSocketBroadcast()) {
            log.warn("WebSocket broadcast is disabled, skipping notification");
            return;
        }

        // Если нет подписчиков, не тратим время на обработку
        if (quoteSubscribers.isEmpty()) {
            log.warn("No subscribers available, skipping notification");
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
                "trackedInstruments", lastPrices.size(), "sharesMode", config.isEnableSharesMode(),
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
        return Set.copyOf(lastPrices.keySet());
    }

    /**
     * Обработка данных стакана заявок из T-Invest API
     */
    public void processOrderBook(OrderBook orderBook) {
        // Проверяем, активен ли сканер (время утренней сессии)
        if (!isScannerActive) {
            return;
        }

        // Обрабатываем стаканы синхронно для минимальной задержки
        try {
            String figi = orderBook.getFigi();

            // Фильтрация не нужна - обрабатываем все акции из базы данных

            log.debug("Processing OrderBook for instrument {} - shares mode: {}", figi,
                    config.isEnableSharesMode());

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

            // Немедленно отправляем обновление стакана в WebSocket (если включено)
            if (config.isEnableImmediateOrderBookUpdates()) {
                sendOrderBookUpdate(figi, bestBid, bestAsk, bestBidQuantity, bestAskQuantity);
            }

        } catch (Exception e) {
            log.error("Error processing order book for FIGI: {}", orderBook.getFigi(), e);
        }
    }

    /**
     * Немедленная отправка обновления стакана в WebSocket
     */
    private void sendOrderBookUpdate(String figi, BigDecimal bestBid, BigDecimal bestAsk,
            long bestBidQuantity, long bestAskQuantity) {
        try {
            // Получаем текущую цену из кеша
            BigDecimal currentPrice = lastPrices.get(figi);
            if (currentPrice == null) {
                // Если нет текущей цены, используем среднее между BID и ASK
                if (bestBid.compareTo(BigDecimal.ZERO) > 0
                        && bestAsk.compareTo(BigDecimal.ZERO) > 0) {
                    currentPrice = bestBid.add(bestAsk).divide(BigDecimal.valueOf(2), 9,
                            java.math.RoundingMode.HALF_UP);
                } else if (bestBid.compareTo(BigDecimal.ZERO) > 0) {
                    currentPrice = bestBid;
                } else if (bestAsk.compareTo(BigDecimal.ZERO) > 0) {
                    currentPrice = bestAsk;
                } else {
                    return; // Нет данных для отправки
                }
            }

            // Получаем предыдущую цену
            BigDecimal previousPrice = lastPrices.get(figi);

            // Получаем цену закрытия (основная сессия)
            BigDecimal closePrice = closePrices.get(figi);
            BigDecimal closePriceOS = closePrice; // Используем цену закрытия как цену ОС

            // Получаем цену закрытия вечерней сессии
            BigDecimal closePriceVS = closePriceEveningSessionService.getEveningClosePrice(figi);

            // Определяем направление
            String direction = "NEUTRAL";
            if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0) {
                int comparison = currentPrice.compareTo(previousPrice);
                if (comparison > 0) {
                    direction = "UP";
                } else if (comparison < 0) {
                    direction = "DOWN";
                }
            }

            // Получаем накопленный объем для этого инструмента
            long accumulatedVolume = accumulatedVolumes.getOrDefault(figi, 0L);

            // Создаем данные о котировке с обновленным стаканом
            QuoteData quoteData = new QuoteData(figi, instrumentTickers.getOrDefault(figi, figi), // Используем
                                                                                                  // тикер
                                                                                                  // или
                                                                                                  // FIGI
                                                                                                  // если
                                                                                                  // не
                                                                                                  // найден
                    instrumentNames.getOrDefault(figi, figi), // Используем имя или FIGI если не
                                                              // найдено
                    currentPrice, previousPrice, closePrice, openPrices.get(figi), closePriceOS,
                    closePriceVS, bestBid, bestAsk, bestBidQuantity, bestAskQuantity,
                    LocalDateTime.now(), 0L, accumulatedVolume, direction,
                    avgVolumeMorningMap.get(figi), avgVolumeWeekendMap.get(figi)); // avgVolumeMorning,
                                                                                   // avgVolumeWeekend

            // Отправляем всем подписчикам
            notifySubscribers(quoteData);

            log.debug("Order book update sent for FIGI: {}, BID: {} ({}), ASK: {} ({})", figi,
                    bestBid, bestBidQuantity, bestAsk, bestAskQuantity);

        } catch (Exception e) {
            log.error("Error sending order book update for FIGI: {}", figi, e);
        }
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

    /**
     * Проверяет, находится ли текущее время в рамках утренней сессии
     */
    private boolean isMorningSessionTime() {
        // Если включен тестовый режим, всегда возвращаем true
        if (config.isEnableTestMode()) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.of("+3")); // Московское время
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();
        int currentSecond = now.getSecond();

        // Проверяем, что время между 06:50:00 и 09:49:59
        if (currentHour > MORNING_SESSION_START_HOUR && currentHour < MORNING_SESSION_END_HOUR) {
            return true;
        }

        if (currentHour == MORNING_SESSION_START_HOUR) {
            return currentMinute >= MORNING_SESSION_START_MINUTE;
        }

        if (currentHour == MORNING_SESSION_END_HOUR) {
            return currentMinute < MORNING_SESSION_END_MINUTE
                    || (currentMinute == MORNING_SESSION_END_MINUTE
                            && currentSecond <= MORNING_SESSION_END_SECOND);
        }

        return false;
    }

    /**
     * Проверяет, является ли текущее время сессией выходного дня (суббота и воскресенье)
     */
    private boolean isWeekendSessionTime() {
        // Если включен тестовый режим, всегда возвращаем true
        if (config.isEnableTestMode()) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.of("+3")); // Московское время
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        // Проверяем, что это суббота или воскресенье
        if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
            return false;
        }

        // Проверяем, что время между 02:00 и 23:50
        if (currentHour > WEEKEND_SESSION_START_HOUR && currentHour < WEEKEND_SESSION_END_HOUR) {
            return true;
        }

        if (currentHour == WEEKEND_SESSION_START_HOUR) {
            return currentMinute >= 0; // 02:00 и далее
        }

        if (currentHour == WEEKEND_SESSION_END_HOUR) {
            return currentMinute <= WEEKEND_SESSION_END_MINUTE; // до 23:50
        }

        return false;
    }

    /**
     * Запускает сканер, если время утренней сессии
     */
    public void startScannerIfSessionTime() {
        if (isMorningSessionTime()) {
            if (!isScannerActive) {
                if (config.isEnableTestMode()) {
                    log.info(
                            "Starting scanner - TEST MODE ENABLED (ignoring session time restrictions)");
                } else {
                    log.info("Starting scanner - morning session time detected");
                }
                isScannerActive = true;
            }
        } else {
            if (isScannerActive) {
                if (config.isEnableTestMode()) {
                    log.info("Scanner remains active - TEST MODE ENABLED");
                } else {
                    log.info("Stopping scanner - outside morning session time");
                    isScannerActive = false;
                }
            }
        }
    }

    /**
     * Запускает сканер, если время сессии выходного дня
     */
    public void startScannerIfWeekendSessionTime() {
        if (isWeekendSessionTime()) {
            if (!isScannerActive) {
                if (config.isEnableTestMode()) {
                    log.info(
                            "Starting weekend scanner - TEST MODE ENABLED (ignoring session time restrictions)");
                } else {
                    log.info("Starting weekend scanner - weekend session time detected");
                }
                isScannerActive = true;
            }
        } else {
            if (isScannerActive) {
                if (config.isEnableTestMode()) {
                    log.info("Weekend scanner remains active - TEST MODE ENABLED");
                } else {
                    log.info("Stopping weekend scanner - outside weekend session time");
                    isScannerActive = false;
                }
            }
        }
    }

    /**
     * Проверяет, активен ли сканер
     */
    public boolean isScannerActive() {
        return isScannerActive;
    }

    /**
     * Проверяет, является ли текущее время сессией выходного дня (публичный метод)
     */
    public boolean checkWeekendSessionTime() {
        return isWeekendSessionTime();
    }

    /**
     * Останавливает сканер принудительно
     */
    public void stopScanner() {
        isScannerActive = false;
        log.info("Scanner stopped manually");
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
        return true;
    }

    /**
     * Удалить индекс по ticker
     */
    public boolean removeIndex(String ticker) {
        boolean removed = dynamicIndices.removeIf(config -> config.ticker.equals(ticker));

        if (removed) {
            log.info("Removed index: {}", ticker);
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
     * Получить инструменты для сканирования
     * 
     * Загружает все акции из таблицы invest.shares и индексы из таблицы invest.indicatives. Режим
     * shares-mode определяет только дополнительные настройки отображения.
     */
    public List<String> getInstrumentsForScanning() {
        // Загружаем все акции из базы данных
        List<String> shareFigis = shareService.getAllShareFigis();

        // Загружаем все индексы из базы данных
        List<String> indicativeFigis = indicativeService.getAllIndicativeFigis();

        // Объединяем списки
        List<String> allFigis = new java.util.ArrayList<>();
        allFigis.addAll(shareFigis);
        allFigis.addAll(indicativeFigis);

        if (config.isEnableSharesMode()) {
            log.info(
                    "Using shares mode: {} shares + {} indicatives = {} total instruments from database",
                    shareFigis.size(), indicativeFigis.size(), allFigis.size());
        } else {
            log.info(
                    "Using config mode: {} shares + {} indicatives = {} total instruments from database",
                    shareFigis.size(), indicativeFigis.size(), allFigis.size());
        }

        if (allFigis.isEmpty()) {
            log.warn(
                    "No instruments found in database! Check if tables invest.shares and invest.indicatives have data");
        } else {
            log.info("First 5 instruments: {}", allFigis.subList(0, Math.min(5, allFigis.size())));
        }

        return allFigis;
    }

    /**
     * Получить имена инструментов для сканирования
     * 
     * Загружает имена из таблиц invest.shares и invest.indicatives.
     */
    public Map<String, String> getInstrumentNamesForScanning() {
        // Загружаем имена акций из базы данных
        Map<String, String> shareNames = shareService.getShareNames();

        // Загружаем имена индексов из базы данных
        Map<String, String> indicativeNames = indicativeService.getIndicativeNames();

        // Объединяем карты
        Map<String, String> allNames = new java.util.HashMap<>();
        allNames.putAll(shareNames);
        allNames.putAll(indicativeNames);

        if (config.isEnableSharesMode()) {
            log.info(
                    "Using shares names: {} shares + {} indicatives = {} total names from database",
                    shareNames.size(), indicativeNames.size(), allNames.size());
        } else {
            log.info(
                    "Using config mode names: {} shares + {} indicatives = {} total names from database",
                    shareNames.size(), indicativeNames.size(), allNames.size());
        }

        return allNames;
    }

    /**
     * Получить тикеры инструментов для сканирования
     * 
     * Загружает тикеры из таблиц invest.shares и invest.indicatives.
     */
    public Map<String, String> getInstrumentTickersForScanning() {
        // Загружаем тикеры акций из базы данных
        Map<String, String> shareTickers = shareService.getShareTickers();

        // Загружаем тикеры индексов из базы данных
        Map<String, String> indicativeTickers = indicativeService.getIndicativeTickers();

        // Объединяем карты
        Map<String, String> allTickers = new java.util.HashMap<>();
        allTickers.putAll(shareTickers);
        allTickers.putAll(indicativeTickers);

        if (config.isEnableSharesMode()) {
            log.info(
                    "Using shares tickers: {} shares + {} indicatives = {} total tickers from database",
                    shareTickers.size(), indicativeTickers.size(), allTickers.size());
        } else {
            log.info(
                    "Using config mode tickers: {} shares + {} indicatives = {} total tickers from database",
                    shareTickers.size(), indicativeTickers.size(), allTickers.size());
        }

        return allTickers;
    }

    /**
     * Получить ShareService для доступа к акциям
     */
    public ShareService getShareService() {
        return shareService;
    }
}
