package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final int SUBSCRIPTION_BATCH_SIZE = 200;

    // Задержка между отправкой батчей подписки (мс)
    private static final int BATCH_DELAY_MS = 100;

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
    private volatile StreamObserver<MarketDataRequest> requestObserver;

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
     * Запуск высокопроизводительного потока данных о последних ценах с автоматическим
     * переподключением
     */
    public void startLastPriceStream() {
        if (!isRunning.get()) {
            log.warn("Service is not running, skipping stream start");
            return;
        }

        log.info("Starting market data stream subscription...");

        // Получаем инструменты по типам отдельно
        List<String> shares = getShares();
        List<String> futures = getFutures();
        List<String> indicatives = getIndicatives();

        int totalInstruments = shares.size() + futures.size() + indicatives.size();

        if (totalInstruments == 0) {
            log.warn("No instruments found for subscription, retrying in 30 seconds...");
            log.warn("Check if shares, futures and indicatives are loaded from database");
            scheduleReconnect(30);
            return;
        }

        log.info("Preparing subscription requests:");
        log.info("  Shares: {} instruments", shares.size());
        log.info("  Futures: {} instruments (weekend restriction: from 8:30 MSK)", futures.size());
        log.info("  Indicatives: {} instruments (LastPrice only)", indicatives.size());
        log.info("  Total: {} instruments", totalInstruments);
        log.info("Subscription will include: LastPrice (all), Trades (shares + futures){}",
                config.isEnableOrderBookSubscription()
                        ? ", OrderBook (shares + futures, futures restricted on weekends)"
                        : "");
        log.info(
                "Futures subscription: In weekends (Sat/Sun), subscriptions start only from 8:30 MSK");
        log.info("Batch size: {} instruments, delay between batches: {}ms", SUBSCRIPTION_BATCH_SIZE,
                BATCH_DELAY_MS);

        try {
            // Создаем StreamObserver один раз для всех батчей

            StreamObserver<MarketDataResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(MarketDataResponse resp) {
                    if (resp.hasSubscribeLastPriceResponse()) {
                        SubscribeLastPriceResponse sr = resp.getSubscribeLastPriceResponse();
                        isConnected.set(true);
                        log.info("=== LastPrice SUBSCRIPTION RESPONSE ===");
                        log.info("Total LastPrice subscriptions: {}",
                                sr.getLastPriceSubscriptionsList().size());

                        // Логируем только ошибки подписки
                        long successCount = sr.getLastPriceSubscriptionsList().stream().filter(
                                s -> s.getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                .count();
                        long errorCount = sr.getLastPriceSubscriptionsList().size() - successCount;

                        log.info("Successful subscriptions: {}, Failed: {}", successCount,
                                errorCount);

                        if (errorCount > 0) {
                            log.warn("Failed LastPrice subscriptions:");
                            sr.getLastPriceSubscriptionsList().stream().filter(s -> s
                                    .getSubscriptionStatus() != SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                    .forEach(s -> log.warn("  FIGI {} -> {}", s.getFigi(),
                                            s.getSubscriptionStatus()));
                        }
                        log.info("=====================================");
                        return;
                    }

                    if (resp.hasSubscribeTradesResponse()) {
                        SubscribeTradesResponse sr = resp.getSubscribeTradesResponse();
                        isConnected.set(true);
                        log.info("=== TRADES SUBSCRIPTION RESPONSE ===");
                        log.info("Total Trades subscriptions: {}",
                                sr.getTradeSubscriptionsList().size());

                        // Логируем только ошибки подписки
                        long successCount = sr.getTradeSubscriptionsList().stream().filter(s -> s
                                .getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                .count();
                        long errorCount = sr.getTradeSubscriptionsList().size() - successCount;

                        log.info("Successful subscriptions: {}, Failed: {}", successCount,
                                errorCount);

                        if (errorCount > 0) {
                            log.warn("Failed Trades subscriptions:");
                            sr.getTradeSubscriptionsList().stream().filter(s -> s
                                    .getSubscriptionStatus() != SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                    .forEach(s -> log.warn("  FIGI {} -> {}", s.getFigi(),
                                            s.getSubscriptionStatus()));
                        }
                        log.info("===================================");
                        return;
                    }

                    if (resp.hasSubscribeOrderBookResponse()) {
                        SubscribeOrderBookResponse sr = resp.getSubscribeOrderBookResponse();
                        isConnected.set(true);
                        log.info("=== ORDER BOOK SUBSCRIPTION RESPONSE ===");
                        log.info("Total OrderBook subscriptions: {}",
                                sr.getOrderBookSubscriptionsList().size());

                        // Логируем только ошибки подписки
                        long successCount = sr.getOrderBookSubscriptionsList().stream().filter(
                                s -> s.getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                .count();
                        long errorCount = sr.getOrderBookSubscriptionsList().size() - successCount;

                        log.info("Successful subscriptions: {}, Failed: {}", successCount,
                                errorCount);

                        if (errorCount > 0) {
                            log.warn("Failed OrderBook subscriptions:");
                            sr.getOrderBookSubscriptionsList().stream().filter(s -> s
                                    .getSubscriptionStatus() != SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS)
                                    .forEach(s -> log.warn("  FIGI {} -> {}", s.getFigi(),
                                            s.getSubscriptionStatus()));
                        }
                        log.info("======================================");
                        return;
                    }

                    if (resp.hasLastPrice()) {
                        log.info("Received last price data from T-Invest API for FIGI: {}",
                                resp.getLastPrice().getFigi());
                        processLastPrice(resp.getLastPrice());
                        // Отправляем данные в сканер котировок
                        quoteScannerService.processLastPrice(resp.getLastPrice());
                    } else if (resp.hasTrade()) {
                        log.info("Received trade data from T-Invest API for FIGI: {}",
                                resp.getTrade().getFigi());
                        processTrade(resp.getTrade());
                        // Отправляем данные в сканер котировок
                        quoteScannerService.processTrade(resp.getTrade());
                    } else if (resp.hasOrderbook()) {
                        log.debug("Received order book data from T-Invest API for FIGI: {}",
                                resp.getOrderbook().getFigi());
                        // Обрабатываем стакан синхронно для минимальной задержки
                        processOrderBook(resp.getOrderbook());
                        // Отправляем данные в сканер котировок
                        quoteScannerService.processOrderBook(resp.getOrderbook());
                    } else {
                        log.info("Received unknown response type from T-Invest API: {}", resp);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    isConnected.set(false);
                    log.error("Market data stream error, attempting reconnection...", t);
                    scheduleReconnect(RECONNECT_DELAY_MS);
                }

                @Override
                public void onCompleted() {
                    isConnected.set(false);
                    log.info("Market data stream completed, restarting subscription...");
                    scheduleReconnect(RECONNECT_DELAY_MS);
                }
            };

            log.info("Connecting to T-Invest API...");
            requestObserver = streamStub.marketDataStream(responseObserver);

            // Вспомогательный метод для отправки подписок LastPrice батчами с задержкой между
            // батчами
            java.util.function.Consumer<List<String>> sendLastPriceBatches = (instrumentList) -> {
                if (instrumentList.isEmpty())
                    return;
                List<List<String>> batches =
                        splitIntoBatches(instrumentList, SUBSCRIPTION_BATCH_SIZE);
                log.info("Sending {} batches of LastPrice subscriptions (batch size: {})",
                        batches.size(), SUBSCRIPTION_BATCH_SIZE);
                for (int i = 0; i < batches.size(); i++) {
                    List<String> batch = batches.get(i);
                    SubscribeLastPriceRequest batchReq =
                            SubscribeLastPriceRequest.newBuilder()
                                    .setSubscriptionAction(
                                            SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                                    .addAllInstruments(
                                            batch.stream()
                                                    .map(f -> LastPriceInstrument.newBuilder()
                                                            .setInstrumentId(f).build())
                                                    .toList())
                                    .build();
                    MarketDataRequest request = MarketDataRequest.newBuilder()
                            .setSubscribeLastPriceRequest(batchReq).build();
                    requestObserver.onNext(request);
                    log.debug("Sent LastPrice batch {}/{} ({} instruments)", i + 1, batches.size(),
                            batch.size());
                    // Задержка между батчами (кроме последнего батча)
                    if (i < batches.size() - 1) {
                        try {
                            Thread.sleep(BATCH_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Interrupted while waiting between batches", e);
                            return;
                        }
                    }
                }
            };

            // Вспомогательный метод для отправки подписок Trades батчами с задержкой между батчами
            java.util.function.Consumer<List<String>> sendTradesBatches = (instrumentList) -> {
                if (instrumentList.isEmpty())
                    return;
                List<List<String>> batches =
                        splitIntoBatches(instrumentList, SUBSCRIPTION_BATCH_SIZE);
                log.info("Sending {} batches of Trades subscriptions (batch size: {})",
                        batches.size(), SUBSCRIPTION_BATCH_SIZE);
                for (int i = 0; i < batches.size(); i++) {
                    List<String> batch = batches.get(i);
                    SubscribeTradesRequest batchReq = SubscribeTradesRequest.newBuilder()
                            .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                            .addAllInstruments(batch.stream()
                                    .map(f -> ru.tinkoff.piapi.contract.v1.TradeInstrument
                                            .newBuilder().setInstrumentId(f).build())
                                    .toList())
                            .build();
                    MarketDataRequest request = MarketDataRequest.newBuilder()
                            .setSubscribeTradesRequest(batchReq).build();
                    requestObserver.onNext(request);
                    log.debug("Sent Trades batch {}/{} ({} instruments)", i + 1, batches.size(),
                            batch.size());
                    // Задержка между батчами (кроме последнего батча)
                    if (i < batches.size() - 1) {
                        try {
                            Thread.sleep(BATCH_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Interrupted while waiting between batches", e);
                            return;
                        }
                    }
                }
            };

            // Подписка на LastPrice: сначала акции, потом фьючерсы, потом индикативы
            // Индикативы подписываются только на LastPrice (не на Trades и OrderBook)
            // Каждый тип инструментов разбивается на батчи по 200 штук с задержкой 100 мс между
            // батчами
            log.info("=== Subscribing to LastPrice (all instrument types) ===");
            if (!shares.isEmpty()) {
                List<List<String>> shareBatches = splitIntoBatches(shares, SUBSCRIPTION_BATCH_SIZE);
                log.info("Subscribing to {} shares in {} batches", shares.size(),
                        shareBatches.size());
                sendLastPriceBatches.accept(shares);
                // Задержка между типами инструментов
                try {
                    Thread.sleep(BATCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting between instrument types", e);
                }
            }

            // Подписка на LastPrice для фьючерсов
            // В выходные дни (суббота/воскресенье) подписка разрешена только с 8:30 утра
            if (!futures.isEmpty()) {
                if (sessionTimeService.canSubscribeToFutures()) {
                    List<List<String>> futureBatches =
                            splitIntoBatches(futures, SUBSCRIPTION_BATCH_SIZE);
                    log.info("Subscribing to {} futures in {} batches", futures.size(),
                            futureBatches.size());
                    sendLastPriceBatches.accept(futures);
                } else {
                    log.info(
                            "Skipping LastPrice subscription for {} futures (weekend before 8:30 MSK)",
                            futures.size());
                }
                // Задержка между типами инструментов
                try {
                    Thread.sleep(BATCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting between instrument types", e);
                }
            }

            if (!indicatives.isEmpty()) {
                List<List<String>> indicativeBatches =
                        splitIntoBatches(indicatives, SUBSCRIPTION_BATCH_SIZE);
                log.info("Subscribing to {} indicatives in {} batches", indicatives.size(),
                        indicativeBatches.size());
                sendLastPriceBatches.accept(indicatives);
            }

            log.info("=== LastPrice subscription completed ===");

            // Подписка на Trades: только акции и фьючерсы (индикативы исключены)
            // Каждый тип инструментов разбивается на батчи по 200 штук с задержкой 100 мс между
            // батчами
            log.info(
                    "=== Subscribing to Trades (shares and futures only, indicatives excluded) ===");
            if (!shares.isEmpty()) {
                List<List<String>> shareBatches = splitIntoBatches(shares, SUBSCRIPTION_BATCH_SIZE);
                log.info("Subscribing to {} shares in {} batches", shares.size(),
                        shareBatches.size());
                sendTradesBatches.accept(shares);
                // Задержка между типами инструментов
                try {
                    Thread.sleep(BATCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting between instrument types", e);
                }
            }

            // Подписка на Trades для фьючерсов
            // В выходные дни (суббота/воскресенье) подписка разрешена только с 8:30 утра
            if (!futures.isEmpty()) {
                if (sessionTimeService.canSubscribeToFutures()) {
                    List<List<String>> futureBatches =
                            splitIntoBatches(futures, SUBSCRIPTION_BATCH_SIZE);
                    log.info("Subscribing to {} futures in {} batches", futures.size(),
                            futureBatches.size());
                    sendTradesBatches.accept(futures);
                } else {
                    log.info(
                            "Skipping Trades subscription for {} futures (weekend before 8:30 MSK)",
                            futures.size());
                }
                // Задержка между типами инструментов
                try {
                    Thread.sleep(BATCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting between instrument types", e);
                }
            }

            // Индикативы не подписываются на Trades - только на LastPrice
            if (!indicatives.isEmpty()) {
                log.info("Skipping Trades subscription for {} indicatives (LastPrice only)",
                        indicatives.size());
            }

            log.info("=== Trades subscription completed ===");

            // Подписка на OrderBook (акции и фьючерсы, если включено)
            // Для фьючерсов в выходные дни подписка разрешена только с 8:30 утра
            // Акции и фьючерсы разбиваются на батчи по 200 штук с задержкой 100 мс между батчами
            if (config.isEnableOrderBookSubscription()) {
                log.info("=== Subscribing to OrderBook (shares and futures) ===");

                // Подписка на OrderBook для акций
                if (!shares.isEmpty()) {
                    List<List<String>> orderBookBatches =
                            splitIntoBatches(shares, SUBSCRIPTION_BATCH_SIZE);
                    log.info("Subscribing to {} shares in {} batches (batch size: {})",
                            shares.size(), orderBookBatches.size(), SUBSCRIPTION_BATCH_SIZE);

                    for (int i = 0; i < orderBookBatches.size(); i++) {
                        List<String> batch = orderBookBatches.get(i);
                        SubscribeOrderBookRequest batchReq = SubscribeOrderBookRequest.newBuilder()
                                .setSubscriptionAction(
                                        SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                                .addAllInstruments(batch.stream()
                                        .map(f -> OrderBookInstrument.newBuilder()
                                                .setInstrumentId(f)
                                                .setDepth(config.getOrderBookDepth()).build())
                                        .toList())
                                .build();
                        MarketDataRequest request = MarketDataRequest.newBuilder()
                                .setSubscribeOrderBookRequest(batchReq).build();
                        requestObserver.onNext(request);
                        log.debug("Sent OrderBook batch {}/{} ({} instruments)", i + 1,
                                orderBookBatches.size(), batch.size());
                        // Задержка между батчами (кроме последнего батча)
                        if (i < orderBookBatches.size() - 1) {
                            try {
                                Thread.sleep(BATCH_DELAY_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                log.warn("Interrupted while waiting between batches", e);
                                break;
                            }
                        }
                    }
                } else {
                    log.warn("No shares available for OrderBook subscription");
                }

                // Подписка на OrderBook для фьючерсов
                // В выходные дни (суббота/воскресенье) подписка разрешена только с 8:30 утра
                if (!futures.isEmpty()) {
                    if (sessionTimeService.canSubscribeToFutures()) {
                        List<List<String>> futureOrderBookBatches =
                                splitIntoBatches(futures, SUBSCRIPTION_BATCH_SIZE);
                        log.info("Subscribing to {} futures in {} batches (batch size: {})",
                                futures.size(), futureOrderBookBatches.size(),
                                SUBSCRIPTION_BATCH_SIZE);

                        for (int i = 0; i < futureOrderBookBatches.size(); i++) {
                            List<String> batch = futureOrderBookBatches.get(i);
                            SubscribeOrderBookRequest batchReq = SubscribeOrderBookRequest
                                    .newBuilder()
                                    .setSubscriptionAction(
                                            SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                                    .addAllInstruments(batch.stream()
                                            .map(f -> OrderBookInstrument.newBuilder()
                                                    .setInstrumentId(f)
                                                    .setDepth(config.getOrderBookDepth()).build())
                                            .toList())
                                    .build();
                            MarketDataRequest request = MarketDataRequest.newBuilder()
                                    .setSubscribeOrderBookRequest(batchReq).build();
                            requestObserver.onNext(request);
                            log.debug("Sent Futures OrderBook batch {}/{} ({} instruments)", i + 1,
                                    futureOrderBookBatches.size(), batch.size());
                            // Задержка между батчами (кроме последнего батча)
                            if (i < futureOrderBookBatches.size() - 1) {
                                try {
                                    Thread.sleep(BATCH_DELAY_MS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    log.warn("Interrupted while waiting between batches", e);
                                    break;
                                }
                            }
                        }
                    } else {
                        log.info(
                                "Skipping OrderBook subscription for {} futures (weekend before 8:30 MSK)",
                                futures.size());
                    }
                }

                log.info("=== OrderBook subscription completed ===");
            }

            log.info("Successfully sent all subscription batches to T-Invest API");

        } catch (Exception e) {
            log.error("Error starting market data stream", e);
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
        return new ServiceStats(isRunning.get(), isConnected.get(), totalReceived.get(),
                totalTradeReceived.get(), totalOrderBookReceived.get());
    }

    /**
     * Принудительное переподключение к T-Invest API
     */
    public void forceReconnect() {
        log.info("Force reconnection requested");
        isConnected.set(false);
        if (requestObserver != null) {
            try {
                requestObserver.onCompleted();
            } catch (Exception e) {
                log.warn("Error completing request observer", e);
            }
        }
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
