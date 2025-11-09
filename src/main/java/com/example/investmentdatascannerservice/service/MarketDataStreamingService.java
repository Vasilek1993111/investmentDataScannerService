package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import com.example.investmentdatascannerservice.utils.SessionTimeService;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.LastPriceInstrument;
import ru.tinkoff.piapi.contract.v1.MarketDataRequest;
import ru.tinkoff.piapi.contract.v1.MarketDataResponse;
import ru.tinkoff.piapi.contract.v1.MarketDataStreamServiceGrpc;
import ru.tinkoff.piapi.contract.v1.OrderBook;
import ru.tinkoff.piapi.contract.v1.OrderBookInstrument;
import ru.tinkoff.piapi.contract.v1.SubscribeLastPriceRequest;
import ru.tinkoff.piapi.contract.v1.SubscribeLastPriceResponse;
import ru.tinkoff.piapi.contract.v1.SubscribeOrderBookRequest;
import ru.tinkoff.piapi.contract.v1.SubscribeOrderBookResponse;
import ru.tinkoff.piapi.contract.v1.SubscribeTradesRequest;
import ru.tinkoff.piapi.contract.v1.SubscribeTradesResponse;
import ru.tinkoff.piapi.contract.v1.SubscriptionAction;
import ru.tinkoff.piapi.contract.v1.SubscriptionStatus;
import ru.tinkoff.piapi.contract.v1.Trade;
import ru.tinkoff.piapi.contract.v1.TradeDirection;

/**
 * Высокопроизводительный сервис для потоковой обработки рыночных данных
 * 
 * Обеспечивает получение данных в реальном времени от T-Invest API с минимальными задержками,
 * асинхронную обработку, автоматическое переподключение и максимальную производительность.
 */
@Service
public class MarketDataStreamingService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataStreamingService.class);

    // Конфигурация производительности
    private static final int RECONNECT_DELAY_MS = 1000;

    // Лимит T-Invest API на количество инструментов в одной подписке
    // Размер батча для подписки (акции, фьючерсы, индикативы отдельно)
    private static final int SUBSCRIPTION_BATCH_SIZE = 150;

    // Лимиты T-Invest API сервиса котировок (gRPC)
    // Максимум 300 инструментов на одно stream-соединение
    private static final int MAX_INSTRUMENTS_PER_STREAM = 300;

    // Максимум 300 запросов в минуту (общий лимит для всех stream-соединений)
    // Все типы подписок (LastPrice, Trades, OrderBook) считаются вместе
    private static final int MAX_REQUESTS_PER_MINUTE = 300;

    // Рассчитываем минимальную задержку между запросами для соблюдения лимита 300 запросов/мин
    // 60 секунд / 300 запросов = 0.2 секунды = 200 мс между запросами
    // Для безопасности используем 250 мс (консервативное значение)
    private static final long MIN_DELAY_BETWEEN_REQUESTS_MS = 250L; // 250 мс между запросами

    // Глобальный rate limiter для всех stream-соединений (300 запросов/мин)
    private final ReentrantLock globalRateLimiterLock = new ReentrantLock();
    private volatile long lastRequestTime = 0;

    private final MarketDataStreamServiceGrpc.MarketDataStreamServiceStub streamStub;
    private final QuoteScannerService quoteScannerService;
    private final QuoteScannerConfig config;
    private final InstrumentCacheService instrumentCacheService;
    private final SessionTimeService sessionTimeService;
    private final WeekendScannerService weekendScannerService;

    // Планировщик для переподключений
    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor();

    // Состояние сервиса
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicLong totalReceived = new AtomicLong(0);
    private final AtomicLong totalTradeReceived = new AtomicLong(0);
    private final AtomicLong totalOrderBookReceived = new AtomicLong(0);

    // Множественные stream-соединения (каждое может обрабатывать до 300 инструментов)
    private final List<StreamConnection> streamConnections = new CopyOnWriteArrayList<>();

    public MarketDataStreamingService(
            MarketDataStreamServiceGrpc.MarketDataStreamServiceStub streamStub,
            QuoteScannerService quoteScannerService, QuoteScannerConfig config,
            InstrumentCacheService instrumentCacheService, SessionTimeService sessionTimeService,
            WeekendScannerService weekendScannerService) {
        this.streamStub = streamStub;
        this.quoteScannerService = quoteScannerService;
        this.config = config;
        this.instrumentCacheService = instrumentCacheService;
        this.sessionTimeService = sessionTimeService;
        this.weekendScannerService = weekendScannerService;
    }

    /**
     * Инициализация высокопроизводительного сервиса с неблокирующими вставками
     */
    @PostConstruct
    public void init() {
        log.info("=== MARKET DATA STREAMING SERVICE INITIALIZATION ===");
        log.info("Initializing high-performance MarketDataStreamingService (no database saving)");

        isRunning.set(true);

        // Запуск потока данных
        log.info("Starting initial market data stream...");
        startLastPriceStream();

        log.info("MarketDataStreamingService initialized successfully");
        log.info("================================================================");
    }

    /**
     * Корректное завершение работы сервиса
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MarketDataStreamingService...");

        isRunning.set(false);

        // Закрываем все stream-соединения
        log.info("Closing {} stream connection(s)...", streamConnections.size());
        for (StreamConnection conn : streamConnections) {
            try {
                if (conn.requestObserver != null) {
                    conn.requestObserver.onCompleted();
                }
            } catch (Exception e) {
                log.warn("Error closing stream connection {}", conn.streamId, e);
            }
        }
        streamConnections.clear();

        // Завершение планировщика
        reconnectScheduler.shutdown();

        try {
            if (!reconnectScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during shutdown", e);
        }

        log.info("MarketDataStreamingService shutdown completed");
    }


    /**
     * Высокопроизводительная неблокирующая асинхронная вставка Trade данных в базу
     */



    /**
     * Получение списка FIGI всех инструментов для подписки
     * 
     * В режиме shares-mode загружает все акции из таблицы invest.shares и индексы из
     * invest.indicatives В обычном режиме использует инструменты из конфигурации
     * 
     * @return список FIGI всех инструментов для подписки
     */
    private List<String> getAllInstruments() {
        log.info("Getting instruments for subscription...");

        // Используем метод, который учитывает режим shares-mode и включает indicatives +
        // динамические индексы
        List<String> instruments = quoteScannerService.getInstrumentsForScanning();

        log.info("=== SUBSCRIPTION INSTRUMENTS SUMMARY ===");
        log.info("Total instruments for subscription: {}", instruments.size());
        if (!instruments.isEmpty()) {
            log.info("First 5 instruments: {}",
                    instruments.subList(0, Math.min(5, instruments.size())));
        }
        log.info("Shares mode enabled: {}", config.isEnableSharesMode());

        // Проверяем наличие индикативов в подписке
        long indicativeCount = instruments.stream()
                .filter(figi -> instrumentCacheService.isIndicative(figi)).count();
        log.info("Indicatives in subscription: {}", indicativeCount);
        log.info("=========================================");

        return instruments;
    }

    /**
     * Получение списка FIGI только акций
     * 
     * @return список FIGI акций
     */
    private List<String> getShares() {
        List<String> shareFigis = quoteScannerService.getShareService().getAllShareFigis();
        log.info("Loaded {} shares for subscription", shareFigis.size());
        return shareFigis;
    }

    /**
     * Получение списка FIGI только фьючерсов
     * 
     * @return список FIGI фьючерсов
     */
    private List<String> getFutures() {
        List<String> futureFigis = instrumentCacheService.getFutureService().getAllFutureFigis();
        log.info("Loaded {} futures for subscription", futureFigis.size());
        return futureFigis;
    }

    /**
     * Получение списка FIGI только индикативов
     * 
     * @return список FIGI индикативов (включая индексы из WeekendScannerService)
     */
    private List<String> getIndicatives() {
        List<String> indicativeFigis =
                instrumentCacheService.getIndicativeService().getAllIndicativeFigis();

        // Добавляем индексы из WeekendScannerService
        List<String> weekendIndices = weekendScannerService.getIndexFigis();
        if (!weekendIndices.isEmpty()) {
            log.info("Adding {} weekend scanner indices to indicatives subscription",
                    weekendIndices.size());
            // Объединяем списки, избегая дубликатов
            for (String figi : weekendIndices) {
                if (!indicativeFigis.contains(figi)) {
                    indicativeFigis.add(figi);
                }
            }
        }

        log.info("Loaded {} indicatives for subscription (including {} weekend scanner indices)",
                indicativeFigis.size(), weekendIndices.size());
        return indicativeFigis;
    }

    /**
     * Получение списка FIGI только акций для подписки на стаканы
     * 
     * @return список FIGI акций для подписки на стаканы
     */
    private List<String> getSharesForOrderBook() {
        log.info("Getting shares for order book subscription...");

        // Получаем только акции из базы данных
        List<String> shareFigis = getShares();

        log.info("Shares for order book subscription: {}", shareFigis.size());
        if (!shareFigis.isEmpty()) {
            log.info("First 5 shares: {}", shareFigis.subList(0, Math.min(5, shareFigis.size())));
        }

        return shareFigis;
    }

    /**
     * Внутренний класс для хранения информации о stream-соединении Каждое соединение может
     * обрабатывать до 300 инструментов
     */
    private static class StreamConnection {
        private final StreamObserver<MarketDataRequest> requestObserver;
        private final int streamId;
        private final AtomicBoolean isConnected = new AtomicBoolean(false);

        public StreamConnection(StreamObserver<MarketDataRequest> requestObserver, int streamId) {
            this.requestObserver = requestObserver;
            this.streamId = streamId;
        }
    }

    /**
     * Класс для хранения информации об инструменте с его типом
     */
    private static class InstrumentWithType {
        final String figi;
        final InstrumentType type;

        enum InstrumentType {
            SHARE, // Акции - LastPrice, Trades, OrderBook
            FUTURE, // Фьючерсы - LastPrice, Trades, OrderBook (с ограничениями по времени)
            INDICATIVE // Индикативы - только LastPrice
        }

        InstrumentWithType(String figi, InstrumentType type) {
            this.figi = figi;
            this.type = type;
        }
    }

    /**
     * Разделить список инструментов на группы для распределения между stream-соединениями Каждая
     * группа должна содержать не более MAX_INSTRUMENTS_PER_STREAM инструментов
     * 
     * @param instruments список инструментов с типами
     * @param maxInstrumentsPerStream максимальное количество инструментов на один stream
     * @return список групп инструментов
     */
    private List<List<InstrumentWithType>> splitIntoStreamGroups(
            List<InstrumentWithType> instruments, int maxInstrumentsPerStream) {
        if (instruments.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<InstrumentWithType>> groups = new ArrayList<>();
        for (int i = 0; i < instruments.size(); i += maxInstrumentsPerStream) {
            int end = Math.min(i + maxInstrumentsPerStream, instruments.size());
            groups.add(new ArrayList<>(instruments.subList(i, end)));
        }
        return groups;
    }

    /**
     * Создать список всех инструментов с типами для подписки
     */
    private List<InstrumentWithType> getAllInstrumentsWithTypes() {
        List<InstrumentWithType> instruments = new ArrayList<>();

        // Добавляем акции
        for (String figi : getShares()) {
            instruments.add(new InstrumentWithType(figi, InstrumentWithType.InstrumentType.SHARE));
        }

        // Добавляем фьючерсы
        for (String figi : getFutures()) {
            instruments.add(new InstrumentWithType(figi, InstrumentWithType.InstrumentType.FUTURE));
        }

        // Добавляем индикативы
        for (String figi : getIndicatives()) {
            instruments.add(
                    new InstrumentWithType(figi, InstrumentWithType.InstrumentType.INDICATIVE));
        }

        return instruments;
    }

    /**
     * Глобальный rate limiter для соблюдения лимита T-Invest API (300 запросов в минуту)
     * Гарантирует минимальную задержку 250 мс между запросами подписки
     * 
     * Все типы подписок (LastPrice, Trades, OrderBook) и все stream-соединения используют один
     * глобальный rate limiter. Это обеспечивает, что общее количество запросов не превысит лимит
     * 300 запросов/мин.
     */
    private void waitForRateLimit() {
        globalRateLimiterLock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastRequest = currentTime - lastRequestTime;

            // Если это первый запрос (lastRequestTime == 0), не добавляем задержку
            if (lastRequestTime > 0 && timeSinceLastRequest < MIN_DELAY_BETWEEN_REQUESTS_MS) {
                long sleepTime = MIN_DELAY_BETWEEN_REQUESTS_MS - timeSinceLastRequest;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                        log.trace(
                                "Global rate limiter: waited {} ms to comply with API limit ({} requests/min)",
                                sleepTime, MAX_REQUESTS_PER_MINUTE);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Global rate limiter interrupted", e);
                    }
                }
            }

            lastRequestTime = System.currentTimeMillis();
        } finally {
            globalRateLimiterLock.unlock();
        }
    }

    /**
     * Разбить список инструментов на батчи для подписки
     * 
     * @param instruments список FIGI инструментов
     * @param batchSize размер батча
     * @return список батчей (каждый батч - новый список)
     */
    private List<List<String>> splitIntoBatches(List<String> instruments, int batchSize) {
        List<List<String>> batches = new java.util.ArrayList<>();
        for (int i = 0; i < instruments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, instruments.size());
            // Создаем новый список для каждого батча, чтобы избежать проблем с subList()
            batches.add(new java.util.ArrayList<>(instruments.subList(i, end)));
        }
        return batches;
    }

    /**
     * Создание и настройка одного stream-соединения для группы инструментов
     * 
     * @param instruments группа инструментов для подписки
     * @param streamId идентификатор stream-соединения
     */
    private void createAndSubscribeStream(List<InstrumentWithType> instruments, int streamId) {
        if (instruments.isEmpty()) {
            log.warn("Stream {}: No instruments provided, skipping stream creation", streamId);
            return;
        }

        log.info("Stream {}: Creating stream connection for {} instruments", streamId,
                instruments.size());

        try {
            // Разделяем инструменты по типам
            List<String> allFigis = new ArrayList<>();
            List<String> shareFigis = new ArrayList<>();
            List<String> futureFigis = new ArrayList<>();
            List<String> indicativeFigis = new ArrayList<>();

            for (InstrumentWithType inst : instruments) {
                allFigis.add(inst.figi);
                switch (inst.type) {
                    case SHARE:
                        shareFigis.add(inst.figi);
                        break;
                    case FUTURE:
                        futureFigis.add(inst.figi);
                        break;
                    case INDICATIVE:
                        indicativeFigis.add(inst.figi);
                        break;
                }
            }

            log.info("Stream {}: Instruments breakdown - Shares: {}, Futures: {}, Indicatives: {}",
                    streamId, shareFigis.size(), futureFigis.size(), indicativeFigis.size());

            // Создаем StreamObserver для этого stream
            StreamObserver<MarketDataResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(MarketDataResponse resp) {
                    if (resp.hasSubscribeLastPriceResponse()) {
                        SubscribeLastPriceResponse sr = resp.getSubscribeLastPriceResponse();
                        StreamConnection streamConn = findStreamConnection(streamId);
                        if (streamConn != null) {
                            streamConn.isConnected.set(true);
                        }
                        isConnected.set(true);
                        log.info("Stream {}: === LastPrice SUBSCRIPTION RESPONSE ===", streamId);
                        log.info("Stream {}: Total LastPrice subscriptions: {}", streamId,
                                sr.getLastPriceSubscriptionsList().size());

                        long successCount = sr.getLastPriceSubscriptionsList().stream().filter(
                                s -> s.getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                .count();
                        long errorCount = sr.getLastPriceSubscriptionsList().size() - successCount;

                        log.info("Stream {}: Successful subscriptions: {}, Failed: {}", streamId,
                                successCount, errorCount);

                        if (errorCount > 0) {
                            log.warn("Stream {}: Failed LastPrice subscriptions:", streamId);
                            sr.getLastPriceSubscriptionsList().stream().filter(s -> s
                                    .getSubscriptionStatus() != SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                    .forEach(s -> log.warn("Stream {}:   FIGI {} -> {}", streamId,
                                            s.getFigi(), s.getSubscriptionStatus()));
                        }
                        log.info("Stream {}: =====================================", streamId);
                        return;
                    }

                    if (resp.hasSubscribeTradesResponse()) {
                        SubscribeTradesResponse sr = resp.getSubscribeTradesResponse();
                        StreamConnection streamConn = findStreamConnection(streamId);
                        if (streamConn != null) {
                            streamConn.isConnected.set(true);
                        }
                        isConnected.set(true);
                        log.info("Stream {}: === TRADES SUBSCRIPTION RESPONSE ===", streamId);
                        log.info("Stream {}: Total Trades subscriptions: {}", streamId,
                                sr.getTradeSubscriptionsList().size());

                        long successCount = sr.getTradeSubscriptionsList().stream().filter(s -> s
                                .getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                .count();
                        long errorCount = sr.getTradeSubscriptionsList().size() - successCount;

                        log.info("Stream {}: Successful subscriptions: {}, Failed: {}", streamId,
                                successCount, errorCount);

                        if (errorCount > 0) {
                            log.warn("Stream {}: Failed Trades subscriptions:", streamId);
                            sr.getTradeSubscriptionsList().stream().filter(s -> s
                                    .getSubscriptionStatus() != SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                    .forEach(s -> log.warn("Stream {}:   FIGI {} -> {}", streamId,
                                            s.getFigi(), s.getSubscriptionStatus()));
                        }
                        log.info("Stream {}: ===================================", streamId);
                        return;
                    }

                    if (resp.hasSubscribeOrderBookResponse()) {
                        SubscribeOrderBookResponse sr = resp.getSubscribeOrderBookResponse();
                        StreamConnection streamConn = findStreamConnection(streamId);
                        if (streamConn != null) {
                            streamConn.isConnected.set(true);
                        }
                        isConnected.set(true);
                        log.info("Stream {}: === ORDER BOOK SUBSCRIPTION RESPONSE ===", streamId);
                        log.info("Stream {}: Total OrderBook subscriptions: {}", streamId,
                                sr.getOrderBookSubscriptionsList().size());

                        long successCount = sr.getOrderBookSubscriptionsList().stream().filter(
                                s -> s.getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                .count();
                        long errorCount = sr.getOrderBookSubscriptionsList().size() - successCount;

                        log.info("Stream {}: Successful subscriptions: {}, Failed: {}", streamId,
                                successCount, errorCount);

                        if (errorCount > 0) {
                            log.warn("Stream {}: Failed OrderBook subscriptions:", streamId);
                            sr.getOrderBookSubscriptionsList().stream().filter(s -> s
                                    .getSubscriptionStatus() != SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                    .forEach(s -> log.warn("Stream {}:   FIGI {} -> {}", streamId,
                                            s.getFigi(), s.getSubscriptionStatus()));
                        }
                        log.info("Stream {}: ======================================", streamId);
                        return;
                    }

                    // Обработка данных
                    if (resp.hasLastPrice()) {
                        processLastPrice(resp.getLastPrice());
                        quoteScannerService.processLastPrice(resp.getLastPrice());
                    } else if (resp.hasTrade()) {
                        processTrade(resp.getTrade());
                        quoteScannerService.processTrade(resp.getTrade());
                    } else if (resp.hasOrderbook()) {
                        processOrderBook(resp.getOrderbook());
                        quoteScannerService.processOrderBook(resp.getOrderbook());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    StreamConnection streamConn = findStreamConnection(streamId);
                    if (streamConn != null) {
                        streamConn.isConnected.set(false);
                    }
                    isConnected.set(false);
                    log.error("Stream {}: Market data stream error, attempting reconnection...",
                            streamId, t);
                    scheduleStreamReconnect(streamId, instruments, RECONNECT_DELAY_MS);
                }

                @Override
                public void onCompleted() {
                    StreamConnection streamConn = findStreamConnection(streamId);
                    if (streamConn != null) {
                        streamConn.isConnected.set(false);
                    }
                    isConnected.set(false);
                    log.info("Stream {}: Market data stream completed, restarting subscription...",
                            streamId);
                    scheduleStreamReconnect(streamId, instruments, RECONNECT_DELAY_MS);
                }
            };

            // Создаем stream-соединение
            log.info("Stream {}: Connecting to T-Invest API...", streamId);
            StreamObserver<MarketDataRequest> requestObserver =
                    streamStub.marketDataStream(responseObserver);
            StreamConnection streamConnection = new StreamConnection(requestObserver, streamId);
            streamConnections.add(streamConnection);

            // Подписка на LastPrice для всех инструментов
            log.info("Stream {}: === Subscribing to LastPrice ({} instruments) ===", streamId,
                    allFigis.size());
            subscribeToLastPrice(streamConnection, allFigis);

            // Подписка на Trades для shares и futures
            List<String> tradesFigis = new ArrayList<>(shareFigis);
            if (sessionTimeService.canSubscribeToFutures()) {
                tradesFigis.addAll(futureFigis);
            } else {
                log.info(
                        "Stream {}: Skipping Trades subscription for {} futures (weekend before 8:30 MSK)",
                        streamId, futureFigis.size());
            }
            if (!tradesFigis.isEmpty()) {
                log.info("Stream {}: === Subscribing to Trades ({} instruments) ===", streamId,
                        tradesFigis.size());
                subscribeToTrades(streamConnection, tradesFigis);
            }

            // Подписка на OrderBook для shares и futures (если включено)
            if (config.isEnableOrderBookSubscription()) {
                List<String> orderBookFigis = new ArrayList<>(shareFigis);
                if (sessionTimeService.canSubscribeToFutures()) {
                    orderBookFigis.addAll(futureFigis);
                } else {
                    log.info(
                            "Stream {}: Skipping OrderBook subscription for {} futures (weekend before 8:30 MSK)",
                            streamId, futureFigis.size());
                }
                if (!orderBookFigis.isEmpty()) {
                    log.info("Stream {}: === Subscribing to OrderBook ({} instruments) ===",
                            streamId, orderBookFigis.size());
                    subscribeToOrderBook(streamConnection, orderBookFigis);
                }
            }

            log.info("Stream {}: Successfully created and subscribed", streamId);

        } catch (Exception e) {
            log.error("Stream {}: Error creating stream connection", streamId, e);
        }
    }

    /**
     * Найти stream-соединение по идентификатору
     */
    private StreamConnection findStreamConnection(int streamId) {
        return streamConnections.stream().filter(conn -> conn.streamId == streamId).findFirst()
                .orElse(null);
    }

    /**
     * Подписка на LastPrice для списка инструментов
     */
    private void subscribeToLastPrice(StreamConnection streamConnection, List<String> instruments) {
        if (instruments.isEmpty()) {
            return;
        }
        List<List<String>> batches = splitIntoBatches(instruments, SUBSCRIPTION_BATCH_SIZE);
        log.info("Stream {}: Sending {} batches of LastPrice subscriptions (batch size: {})",
                streamConnection.streamId, batches.size(), SUBSCRIPTION_BATCH_SIZE);
        for (int i = 0; i < batches.size(); i++) {
            waitForRateLimit(); // Глобальный rate limiter для всех stream
            List<String> batch = batches.get(i);
            SubscribeLastPriceRequest batchReq = SubscribeLastPriceRequest.newBuilder()
                    .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                    .addAllInstruments(batch.stream()
                            .map(f -> LastPriceInstrument.newBuilder().setInstrumentId(f).build())
                            .toList())
                    .build();
            MarketDataRequest request =
                    MarketDataRequest.newBuilder().setSubscribeLastPriceRequest(batchReq).build();
            streamConnection.requestObserver.onNext(request);
            log.debug("Stream {}: Sent LastPrice batch {}/{} ({} instruments)",
                    streamConnection.streamId, i + 1, batches.size(), batch.size());
        }
    }

    /**
     * Подписка на Trades для списка инструментов
     */
    private void subscribeToTrades(StreamConnection streamConnection, List<String> instruments) {
        if (instruments.isEmpty()) {
            return;
        }
        List<List<String>> batches = splitIntoBatches(instruments, SUBSCRIPTION_BATCH_SIZE);
        log.info("Stream {}: Sending {} batches of Trades subscriptions (batch size: {})",
                streamConnection.streamId, batches.size(), SUBSCRIPTION_BATCH_SIZE);
        for (int i = 0; i < batches.size(); i++) {
            waitForRateLimit(); // Глобальный rate limiter для всех stream
            List<String> batch = batches.get(i);
            SubscribeTradesRequest batchReq =
                    SubscribeTradesRequest.newBuilder()
                            .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                            .addAllInstruments(batch.stream()
                                    .map(f -> ru.tinkoff.piapi.contract.v1.TradeInstrument
                                            .newBuilder().setInstrumentId(f).build())
                                    .toList())
                            .build();
            MarketDataRequest request =
                    MarketDataRequest.newBuilder().setSubscribeTradesRequest(batchReq).build();
            streamConnection.requestObserver.onNext(request);
            log.debug("Stream {}: Sent Trades batch {}/{} ({} instruments)",
                    streamConnection.streamId, i + 1, batches.size(), batch.size());
        }
    }

    /**
     * Подписка на OrderBook для списка инструментов
     */
    private void subscribeToOrderBook(StreamConnection streamConnection, List<String> instruments) {
        if (instruments.isEmpty()) {
            return;
        }
        List<List<String>> batches = splitIntoBatches(instruments, SUBSCRIPTION_BATCH_SIZE);
        log.info("Stream {}: Sending {} batches of OrderBook subscriptions (batch size: {})",
                streamConnection.streamId, batches.size(), SUBSCRIPTION_BATCH_SIZE);
        for (int i = 0; i < batches.size(); i++) {
            waitForRateLimit(); // Глобальный rate limiter для всех stream
            List<String> batch = batches.get(i);
            SubscribeOrderBookRequest batchReq = SubscribeOrderBookRequest.newBuilder()
                    .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                    .addAllInstruments(batch.stream()
                            .map(f -> OrderBookInstrument.newBuilder().setInstrumentId(f)
                                    .setDepth(config.getOrderBookDepth()).build())
                            .toList())
                    .build();
            MarketDataRequest request =
                    MarketDataRequest.newBuilder().setSubscribeOrderBookRequest(batchReq).build();
            streamConnection.requestObserver.onNext(request);
            log.debug("Stream {}: Sent OrderBook batch {}/{} ({} instruments)",
                    streamConnection.streamId, i + 1, batches.size(), batch.size());
        }
    }

    /**
     * Планирование переподключения для конкретного stream
     */
    private void scheduleStreamReconnect(int streamId, List<InstrumentWithType> instruments,
            long delayMs) {
        if (!isRunning.get()) {
            return;
        }
        reconnectScheduler.schedule(() -> {
            if (isRunning.get()) {
                StreamConnection streamConn = findStreamConnection(streamId);
                if (streamConn == null || !streamConn.isConnected.get()) {
                    log.info("Stream {}: Attempting to reconnect to T-Invest API...", streamId);
                    // Удаляем старое соединение если оно есть
                    streamConnections.removeIf(conn -> conn.streamId == streamId);
                    // Создаем новое соединение
                    createAndSubscribeStream(instruments, streamId);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Запуск высокопроизводительного потока данных о последних ценах с автоматическим
     * переподключением и поддержкой множественных stream-соединений
     */
    public void startLastPriceStream() {
        if (!isRunning.get()) {
            log.warn("Service is not running, skipping stream start");
            return;
        }

        log.info("=== Starting market data stream subscription with multiple streams ===");

        // Получаем все инструменты с типами
        List<InstrumentWithType> allInstruments = getAllInstrumentsWithTypes();

        if (allInstruments.isEmpty()) {
            log.warn("No instruments found for subscription, retrying in 30 seconds...");
            log.warn("Check if shares, futures and indicatives are loaded from database");
            scheduleReconnect(30);
            return;
        }

        // Подсчитываем инструменты по типам
        long sharesCount = allInstruments.stream()
                .filter(inst -> inst.type == InstrumentWithType.InstrumentType.SHARE).count();
        long futuresCount = allInstruments.stream()
                .filter(inst -> inst.type == InstrumentWithType.InstrumentType.FUTURE).count();
        long indicativesCount = allInstruments.stream()
                .filter(inst -> inst.type == InstrumentWithType.InstrumentType.INDICATIVE).count();

        log.info("Preparing subscription requests:");
        log.info("  Shares: {} instruments", sharesCount);
        log.info("  Futures: {} instruments (weekend restriction: from 8:30 MSK)", futuresCount);
        log.info("  Indicatives: {} instruments (LastPrice only)", indicativesCount);
        log.info("  Total: {} instruments", allInstruments.size());
        log.info("Subscription will include: LastPrice (all), Trades (shares + futures){}",
                config.isEnableOrderBookSubscription()
                        ? ", OrderBook (shares + futures, futures restricted on weekends)"
                        : "");
        log.info(
                "Futures subscription: In weekends (Sat/Sun), subscriptions start only from 8:30 MSK");
        log.info("Batch size: {} instruments", SUBSCRIPTION_BATCH_SIZE);
        log.info("Rate limiting: {} ms delay between requests (max {} requests/min)",
                MIN_DELAY_BETWEEN_REQUESTS_MS, MAX_REQUESTS_PER_MINUTE);
        log.info("Max instruments per stream: {} (limit from T-Invest API quotes service)",
                MAX_INSTRUMENTS_PER_STREAM);

        try {
            // Очищаем старые соединения
            streamConnections.clear();
            lastRequestTime = 0; // Сбрасываем глобальный rate limiter

            // Разделяем инструменты на группы для stream-соединений
            // Каждая группа содержит максимум MAX_INSTRUMENTS_PER_STREAM инструментов
            List<List<InstrumentWithType>> streamGroups =
                    splitIntoStreamGroups(allInstruments, MAX_INSTRUMENTS_PER_STREAM);

            int numberOfStreams = streamGroups.size();
            log.info("=== Creating {} stream connection(s) for {} total instruments ===",
                    numberOfStreams, allInstruments.size());
            log.info("Max instruments per stream: {}", MAX_INSTRUMENTS_PER_STREAM);

            // Создаем отдельный stream для каждой группы инструментов синхронно
            // Глобальный rate limiter гарантирует соблюдение лимита 300 запросов/мин
            for (int i = 0; i < streamGroups.size(); i++) {
                List<InstrumentWithType> group = streamGroups.get(i);
                int streamId = i + 1;
                log.info("Stream {}: Will handle {} instruments", streamId, group.size());
                createAndSubscribeStream(group, streamId);
            }

            log.info("=== Successfully created {} stream connection(s) ===", numberOfStreams);
            isConnected.set(true);

        } catch (Exception e) {
            log.error("Error starting market data streams", e);
            scheduleReconnect(RECONNECT_DELAY_MS);
        }
    }

    /**
     * Высокопроизводительная обработка данных о сделке с минимальной задержкой
     */
    private void processTrade(Trade trade) {
        try {
            totalTradeReceived.incrementAndGet();

            Instant eventInstant =
                    Instant.ofEpochSecond(trade.getTime().getSeconds(), trade.getTime().getNanos());
            // Конвертируем время в UTC+3 (московское время)
            LocalDateTime eventTime = LocalDateTime.ofInstant(eventInstant, ZoneOffset.of("+3"));

            BigDecimal priceValue = BigDecimal.valueOf(trade.getPrice().getUnits())
                    .add(BigDecimal.valueOf(trade.getPrice().getNano()).movePointLeft(9));

            // Определяем направление сделки
            String direction =
                    trade.getDirection() == TradeDirection.TRADE_DIRECTION_BUY ? "BUY" : "SELL";

            // Определяем источник сделки (по умолчанию EXCHANGE)
            String tradeSource = "EXCHANGE";

            log.debug("Processing Trade: FIGI={}, Time={}, Price={}, Direction={}, Quantity={}",
                    trade.getFigi(), eventTime, priceValue, direction, trade.getQuantity());

            // Логируем каждую 100-ю запись для мониторинга частоты
            if (totalTradeReceived.get() % 100 == 0) {
                log.info("Received {} trades from T-Invest API", totalTradeReceived.get());
            }

            if (log.isDebugEnabled()) {
                log.debug("Processing trade for {} at {}: {} {} ({} lots)", trade.getFigi(),
                        eventTime, priceValue, direction, trade.getQuantity());
            }

            if (log.isTraceEnabled()) {
                log.trace("Processed trade for {} at {}: {} {} ({} lots) from {}", trade.getFigi(),
                        eventTime, priceValue, direction, trade.getQuantity(), tradeSource);
            }
        } catch (Exception e) {
            log.error("Error processing trade for {}", trade.getFigi(), e);
        }
    }

    /**
     * Высокопроизводительная обработка данных о последней цене
     */
    private void processLastPrice(LastPrice price) {
        try {
            totalReceived.incrementAndGet();

            Instant eventInstant =
                    Instant.ofEpochSecond(price.getTime().getSeconds(), price.getTime().getNanos());
            // Конвертируем время в UTC+3 (московское время)
            LocalDateTime eventTime = LocalDateTime.ofInstant(eventInstant, ZoneOffset.of("+3"));

            BigDecimal priceValue = BigDecimal.valueOf(price.getPrice().getUnits())
                    .add(BigDecimal.valueOf(price.getPrice().getNano()).movePointLeft(9));

            log.debug("Processing LastPrice: FIGI={}, Time={}, Price={}", price.getFigi(),
                    eventTime, priceValue);

            // Логируем каждую 100-ю запись для мониторинга частоты
            if (totalReceived.get() % 100 == 0) {
                log.info("Received {} prices from T-Invest API", totalReceived.get());
            }

            if (log.isDebugEnabled()) {
                log.debug("Processing price for {} at {}: {}", price.getFigi(), eventTime,
                        priceValue);
            }

            if (log.isTraceEnabled()) {
                log.trace("Processed price for {} at {}: {}", price.getFigi(), eventTime,
                        priceValue);
            }
        } catch (Exception e) {
            log.error("Error processing last price for {}", price.getFigi(), e);
        }
    }

    /**
     * Высокопроизводительная обработка данных стакана заявок
     */
    private void processOrderBook(OrderBook orderBook) {
        try {
            totalOrderBookReceived.incrementAndGet();

            Instant eventInstant = Instant.ofEpochSecond(orderBook.getTime().getSeconds(),
                    orderBook.getTime().getNanos());
            // Конвертируем время в UTC+3 (московское время)
            LocalDateTime eventTime = LocalDateTime.ofInstant(eventInstant, ZoneOffset.of("+3"));

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

            log.debug("Processing OrderBook: FIGI={}, Time={}, BestBid={} ({}), BestAsk={} ({})",
                    orderBook.getFigi(), eventTime, bestBid, bestBidQuantity, bestAsk,
                    bestAskQuantity);

            // Логируем каждую 100-ю запись для мониторинга частоты
            if (totalOrderBookReceived.get() % 100 == 0) {
                log.info("Received {} order books from T-Invest API", totalOrderBookReceived.get());
            }

            if (log.isDebugEnabled()) {
                log.debug("Processing order book for {} at {}: BID {} ({}), ASK {} ({})",
                        orderBook.getFigi(), eventTime, bestBid, bestBidQuantity, bestAsk,
                        bestAskQuantity);
            }

            if (log.isTraceEnabled()) {
                log.trace(
                        "Processed order book for {} at {}: BID {} ({}), ASK {} ({}), Total Bids: {}, Total Asks: {}",
                        orderBook.getFigi(), eventTime, bestBid, bestBidQuantity, bestAsk,
                        bestAskQuantity, orderBook.getBidsCount(), orderBook.getAsksCount());
            }
        } catch (Exception e) {
            log.error("Error processing order book for {}", orderBook.getFigi(), e);
        }
    }

    /**
     * Планирование переподключения с экспоненциальной задержкой
     */
    private void scheduleReconnect(long delayMs) {
        if (!isRunning.get()) {
            return;
        }

        reconnectScheduler.schedule(() -> {
            if (isRunning.get() && !isConnected.get()) {
                log.info("Attempting to reconnect to T-Invest API...");
                startLastPriceStream();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Получить статистику производительности сервиса
     */
    public ServiceStats getServiceStats() {
        // Проверяем, что хотя бы одно соединение активно
        boolean anyConnected = isConnected.get()
                || streamConnections.stream().anyMatch(conn -> conn.isConnected.get());
        return new ServiceStats(isRunning.get(), anyConnected, totalReceived.get(),
                totalTradeReceived.get(), totalOrderBookReceived.get());
    }

    /**
     * Принудительное переподключение к T-Invest API
     */
    public void forceReconnect() {
        log.info("Force reconnection requested for all stream connections");
        isConnected.set(false);

        // Закрываем все существующие stream-соединения
        for (StreamConnection conn : streamConnections) {
            try {
                if (conn.requestObserver != null) {
                    conn.requestObserver.onCompleted();
                }
            } catch (Exception e) {
                log.warn("Error completing request observer for stream {}", conn.streamId, e);
            }
        }
        streamConnections.clear();

        scheduleReconnect(100);
    }

    /**
     * Расширенная статистика сервиса с метриками Trade и OrderBook обработки
     */
    public static class ServiceStats {
        private final boolean isRunning;
        private final boolean isConnected;
        private final long totalReceived;
        private final long totalTradeReceived;
        private final long totalOrderBookReceived;

        public ServiceStats(boolean isRunning, boolean isConnected, long totalReceived,
                long totalTradeReceived, long totalOrderBookReceived) {
            this.isRunning = isRunning;
            this.isConnected = isConnected;
            this.totalReceived = totalReceived;
            this.totalTradeReceived = totalTradeReceived;
            this.totalOrderBookReceived = totalOrderBookReceived;
        }

        public boolean isRunning() {
            return isRunning;
        }

        public boolean isConnected() {
            return isConnected;
        }

        public long getTotalReceived() {
            return totalReceived;
        }

        public long getTotalTradeReceived() {
            return totalTradeReceived;
        }

        public long getTotalOrderBookReceived() {
            return totalOrderBookReceived;
        }

        public long getTotalReceivedAll() {
            return totalReceived + totalTradeReceived + totalOrderBookReceived;
        }

    }

}
