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

    private final MarketDataStreamServiceGrpc.MarketDataStreamServiceStub streamStub;
    private final QuoteScannerService quoteScannerService;
    private final QuoteScannerConfig config;

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
            QuoteScannerService quoteScannerService, QuoteScannerConfig config) {
        this.streamStub = streamStub;
        this.quoteScannerService = quoteScannerService;
        this.config = config;
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
     * Получение списка FIGI всех акций и фьючерсов для подписки
     * 
     * @return список FIGI всех акций и фьючерсов разных типов
     */
    private List<String> getAllInstruments() {
        log.info("Using configured instruments for subscription...");

        // Используем только настроенные инструменты из конфигурации
        List<String> configuredInstruments = quoteScannerService.getInstruments().stream().toList();

        log.info("=== SUBSCRIPTION INSTRUMENTS SUMMARY ===");
        log.info("Configured instruments: {}", configuredInstruments.size());
        log.info("Instruments: {}", configuredInstruments);
        log.info("=========================================");

        return configuredInstruments;
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
        List<String> allFigis = getAllInstruments();

        if (allFigis.isEmpty()) {
            log.warn("No FIGIs found for subscription, retrying in 30 seconds...");
            scheduleReconnect(30);
            return;
        }

        log.info("Preparing subscription requests for {} instruments", allFigis.size());

        try {
            // Подписываемся на цены последних сделок
            log.info("Creating LastPrice subscription request for {} instruments", allFigis.size());
            SubscribeLastPriceRequest lastPriceReq = SubscribeLastPriceRequest.newBuilder()
                    .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                    .addAllInstruments(allFigis.stream()
                            .map(f -> LastPriceInstrument.newBuilder().setInstrumentId(f).build())
                            .toList())
                    .build();

            // Подписываемся на поток обезличенных сделок для максимального потока данных
            log.info("Creating Trades subscription request for {} instruments", allFigis.size());
            SubscribeTradesRequest tradesReq =
                    SubscribeTradesRequest.newBuilder()
                            .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                            .addAllInstruments(allFigis.stream()
                                    .map(f -> ru.tinkoff.piapi.contract.v1.TradeInstrument
                                            .newBuilder().setInstrumentId(f).build())
                                    .toList())
                            .build();

            // Отправляем подписку на цены последних сделок
            log.info("Building LastPrice subscription request");
            MarketDataRequest lastPriceSubscribeReq = MarketDataRequest.newBuilder()
                    .setSubscribeLastPriceRequest(lastPriceReq).build();

            // Отправляем подписку на поток сделок
            log.info("Building Trades subscription request");
            MarketDataRequest tradesSubscribeReq =
                    MarketDataRequest.newBuilder().setSubscribeTradesRequest(tradesReq).build();

            // Подписываемся на стаканы (если включено в конфигурации)
            MarketDataRequest orderBookSubscribeReq = null;
            if (config.isEnableOrderBookSubscription()) {
                log.info("Creating OrderBook subscription request for {} instruments with depth {}",
                        allFigis.size(), config.getOrderBookDepth());
                SubscribeOrderBookRequest orderBookReq = SubscribeOrderBookRequest.newBuilder()
                        .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                        .addAllInstruments(allFigis.stream()
                                .map(f -> OrderBookInstrument.newBuilder().setInstrumentId(f)
                                        .setDepth(config.getOrderBookDepth()).build())
                                .toList())
                        .build();

                orderBookSubscribeReq = MarketDataRequest.newBuilder()
                        .setSubscribeOrderBookRequest(orderBookReq).build();
            }

            StreamObserver<MarketDataResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(MarketDataResponse resp) {
                    if (resp.hasSubscribeLastPriceResponse()) {
                        SubscribeLastPriceResponse sr = resp.getSubscribeLastPriceResponse();
                        isConnected.set(true);
                        log.info("=== LastPrice SUBSCRIPTION RESPONSE ===");
                        log.info("Total LastPrice subscriptions: {}",
                                sr.getLastPriceSubscriptionsList().size());
                        sr.getLastPriceSubscriptionsList().forEach(s -> log.info("  FIGI {} -> {}",
                                s.getFigi(), s.getSubscriptionStatus()));
                        log.info("=====================================");
                        return;
                    }

                    if (resp.hasSubscribeTradesResponse()) {
                        SubscribeTradesResponse sr = resp.getSubscribeTradesResponse();
                        isConnected.set(true);
                        log.info("=== TRADES SUBSCRIPTION RESPONSE ===");
                        log.info("Total Trades subscriptions: {}",
                                sr.getTradeSubscriptionsList().size());
                        sr.getTradeSubscriptionsList().forEach(s -> log.info("  FIGI {} -> {}",
                                s.getFigi(), s.getSubscriptionStatus()));
                        log.info("===================================");
                        return;
                    }

                    if (resp.hasSubscribeOrderBookResponse()) {
                        SubscribeOrderBookResponse sr = resp.getSubscribeOrderBookResponse();
                        isConnected.set(true);
                        log.info("=== ORDER BOOK SUBSCRIPTION RESPONSE ===");
                        log.info("Total OrderBook subscriptions: {}",
                                sr.getOrderBookSubscriptionsList().size());
                        sr.getOrderBookSubscriptionsList().forEach(s -> log.info("  FIGI {} -> {}",
                                s.getFigi(), s.getSubscriptionStatus()));
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
                        log.info("Received order book data from T-Invest API for FIGI: {}",
                                resp.getOrderbook().getFigi());
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

            log.info("Connecting to T-Invest API with {} instruments...", allFigis.size());
            requestObserver = streamStub.marketDataStream(responseObserver);

            // Отправляем подписку на цены последних сделок
            log.info("Sending LastPrice subscription request to T-Invest API");
            requestObserver.onNext(lastPriceSubscribeReq);

            // Отправляем подписку на поток сделок
            log.info("Sending Trades subscription request to T-Invest API");
            requestObserver.onNext(tradesSubscribeReq);

            // Отправляем подписку на стаканы (если включено)
            if (orderBookSubscribeReq != null) {
                log.info("Sending OrderBook subscription request to T-Invest API");
                requestObserver.onNext(orderBookSubscribeReq);
            }

            log.info("Successfully sent subscription requests to T-Invest API");

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
