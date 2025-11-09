package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.dto.QuoteData;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import com.example.investmentdatascannerservice.utils.SessionTimeService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.OrderBook;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Trade;

/**
 * Высокопроизводительный процессор рыночных данных
 * 
 * Обрабатывает данные от T-Invest API с минимальными задержками и максимальной производительностью
 */
@Service
public class MarketDataProcessor {

    private static final Logger log = LoggerFactory.getLogger(MarketDataProcessor.class);

    private final InstrumentCacheService cacheService;
    private final SessionTimeService sessionService;
    private final QuoteDataFactory quoteDataFactory;
    private final NotificationService notificationService;
    private final ExecutorService processingExecutor;
    private final MeterRegistry meterRegistry;
    private final PriceCacheService priceCacheService;

    // Метрики
    private final Counter lastPriceProcessed;
    private final Counter tradeProcessed;
    private final Counter orderBookProcessed;
    private final Timer processingTimer;

    // Статистика
    private final AtomicLong totalProcessed = new AtomicLong(0);

    // Дедупликация для предотвращения повторной обработки
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastProcessedTime =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MIN_PROCESSING_INTERVAL_MS = 100; // Минимальный интервал между
                                                                // обработкой одного FIGI
    private static final long CACHE_CLEANUP_INTERVAL_MS = 300000; // 5 минут
    private long lastCacheCleanup = System.currentTimeMillis();

    public MarketDataProcessor(InstrumentCacheService cacheService,
            SessionTimeService sessionService, QuoteDataFactory quoteDataFactory,
            NotificationService notificationService,
            @Qualifier("marketDataExecutor") ExecutorService processingExecutor,
            MeterRegistry meterRegistry, PriceCacheService priceCacheService) {
        this.cacheService = cacheService;
        this.sessionService = sessionService;
        this.quoteDataFactory = quoteDataFactory;
        this.notificationService = notificationService;
        this.processingExecutor = processingExecutor;
        this.meterRegistry = meterRegistry;
        this.priceCacheService = priceCacheService;

        // Инициализация метрик
        this.lastPriceProcessed = Counter.builder("market.data.processed").tag("type", "LastPrice")
                .register(meterRegistry);
        this.tradeProcessed = Counter.builder("market.data.processed").tag("type", "Trade")
                .register(meterRegistry);
        this.orderBookProcessed = Counter.builder("market.data.processed").tag("type", "OrderBook")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("market.data.processing.time").register(meterRegistry);
    }

    /**
     * Обработка данных о последней цене
     */
    public void processLastPrice(LastPrice price) {
        if (!sessionService.isAnySessionActive()) {
            log.debug("Session not active, skipping LastPrice for {}", price.getFigi());
            return;
        }

        // Проверяем дедупликацию
        if (!shouldProcess(price.getFigi())) {
            log.debug("Skipping LastPrice for {} - too frequent processing", price.getFigi());
            return;
        }

        processingExecutor.submit(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                processLastPriceInternal(price);
                lastPriceProcessed.increment();
                totalProcessed.incrementAndGet();
            } catch (Exception e) {
                log.error("Error processing LastPrice for {}", price.getFigi(), e);
            } finally {
                sample.stop(processingTimer);
            }
        });
    }

    /**
     * Обработка данных о сделке
     */
    public void processTrade(Trade trade) {
        if (!sessionService.isAnySessionActive()) {
            log.debug("Session not active, skipping Trade for {}", trade.getFigi());
            return;
        }

        // Проверяем дедупликацию
        if (!shouldProcess(trade.getFigi())) {
            log.debug("Skipping Trade for {} - too frequent processing", trade.getFigi());
            return;
        }

        processingExecutor.submit(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                processTradeInternal(trade);
                tradeProcessed.increment();
                totalProcessed.incrementAndGet();
            } catch (Exception e) {
                log.error("Error processing Trade for {}", trade.getFigi(), e);
            } finally {
                sample.stop(processingTimer);
            }
        });
    }

    /**
     * Обработка данных стакана заявок
     */
    public void processOrderBook(OrderBook orderBook) {
        if (!sessionService.isAnySessionActive()) {
            log.debug("Session not active, skipping OrderBook for {}", orderBook.getFigi());
            return;
        }

        // Проверяем дедупликацию
        if (!shouldProcess(orderBook.getFigi())) {
            log.debug("Skipping OrderBook for {} - too frequent processing", orderBook.getFigi());
            return;
        }

        processingExecutor.submit(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                processOrderBookInternal(orderBook);
                orderBookProcessed.increment();
                totalProcessed.incrementAndGet();
            } catch (Exception e) {
                log.error("Error processing OrderBook for {}", orderBook.getFigi(), e);
            } finally {
                sample.stop(processingTimer);
            }
        });
    }

    /**
     * Внутренняя обработка LastPrice
     */
    private void processLastPriceInternal(LastPrice price) {
        String figi = price.getFigi();
        BigDecimal currentPrice = convertPrice(price.getPrice());


        log.debug("Processing LastPrice for FIGI: {}, price: {}", figi, currentPrice);

        // Обновляем кэш
        cacheService.setLastPrice(figi, currentPrice);
        // Обновляем кэш цен для доступа через PriceCacheService
        priceCacheService.updateLastPrice(figi, currentPrice);

        // Если это первая цена за день, сохраняем как цену открытия
        if (cacheService.getOpenPrice(figi) == null) {
            cacheService.setOpenPrice(figi, currentPrice);
        }

        // Специальная логика для OTC инструментов: принудительно обновляем цену
        // поскольку Trade события могут не работать для OTC
        if (figi.equals("TCS00A105NV2")) {
            // Принудительное обновление цены для OTC инструментов
        }

        // Создаем QuoteData
        QuoteData quoteData = quoteDataFactory.createFromLastPrice(price, currentPrice);

        // Уведомляем подписчиков
        notificationService.notifySubscribers(quoteData);


        log.debug("Processed LastPrice for {}: {}", figi, quoteData);
    }

    /**
     * Внутренняя обработка Trade
     */
    private void processTradeInternal(Trade trade) {
        String figi = trade.getFigi();
        BigDecimal currentPrice = convertPrice(trade.getPrice());


        log.debug("Processing Trade for FIGI: {}, price: {}, quantity: {}", figi, currentPrice,
                trade.getQuantity());

        // Обновляем кэш
        cacheService.setLastPrice(figi, currentPrice);
        // Обновляем кэш цен для доступа через PriceCacheService
        priceCacheService.updateLastPrice(figi, currentPrice);

        // Накопляем объем только во время сессий выходного дня
        if (sessionService.isWeekendSessionTime()) {
            cacheService.addToAccumulatedVolume(figi, trade.getQuantity());
        }

        // Создаем QuoteData
        QuoteData quoteData = quoteDataFactory.createFromTrade(trade, currentPrice);

        // Уведомляем подписчиков
        notificationService.notifySubscribers(quoteData);


        log.debug("Processed Trade for {}: {}", figi, quoteData);
    }

    /**
     * Внутренняя обработка OrderBook
     */
    private void processOrderBookInternal(OrderBook orderBook) {
        String figi = orderBook.getFigi();

        log.debug("Processing OrderBook for FIGI: {}", figi);

        // Получаем лучший BID
        BigDecimal bestBid = BigDecimal.ZERO;
        long bestBidQuantity = 0;
        if (orderBook.getBidsCount() > 0) {
            var bestBidOrder = orderBook.getBids(0);
            bestBid = convertPrice(bestBidOrder.getPrice());
            bestBidQuantity = bestBidOrder.getQuantity();
        }

        // Получаем лучший ASK
        BigDecimal bestAsk = BigDecimal.ZERO;
        long bestAskQuantity = 0;
        if (orderBook.getAsksCount() > 0) {
            var bestAskOrder = orderBook.getAsks(0);
            bestAsk = convertPrice(bestAskOrder.getPrice());
            bestAskQuantity = bestAskOrder.getQuantity();
        }

        // Обновляем данные стакана в кэше
        cacheService.setBestBid(figi, bestBid);
        cacheService.setBestAsk(figi, bestAsk);
        cacheService.setBestBidQuantity(figi, bestBidQuantity);
        cacheService.setBestAskQuantity(figi, bestAskQuantity);

        // Создаем QuoteData для уведомления подписчиков об обновлении стакана
        // Отправляем уведомление независимо от наличия currentPrice, так как стакан может быть
        // доступен даже без цены
        QuoteData quoteData = quoteDataFactory.createFromOrderBook(figi, bestBid, bestAsk,
                bestBidQuantity, bestAskQuantity);

        // Уведомляем подписчиков об обновлении стакана
        notificationService.notifySubscribers(quoteData);

        log.debug("Processed OrderBook for {}: BID {} ({}), ASK {} ({}), notified subscribers",
                figi, bestBid, bestBidQuantity, bestAsk, bestAskQuantity);
    }

    /**
     * Конвертация цены из protobuf в BigDecimal
     */
    private BigDecimal convertPrice(Quotation quotation) {
        return BigDecimal.valueOf(quotation.getUnits())
                .add(BigDecimal.valueOf(quotation.getNano()).movePointLeft(9));
    }


    /**
     * Проверка, следует ли обрабатывать данные для данного FIGI Предотвращает слишком частую
     * обработку одного и того же инструмента
     */
    private boolean shouldProcess(String figi) {
        long currentTime = System.currentTimeMillis();

        // Периодическая очистка кэша для предотвращения утечек памяти
        if (currentTime - lastCacheCleanup > CACHE_CLEANUP_INTERVAL_MS) {
            cleanupCache(currentTime);
            lastCacheCleanup = currentTime;
        }

        Long lastTime = lastProcessedTime.get(figi);

        if (lastTime == null || (currentTime - lastTime) >= MIN_PROCESSING_INTERVAL_MS) {
            lastProcessedTime.put(figi, currentTime);
            return true;
        }

        return false;
    }

    /**
     * Очистка устаревших записей из кэша дедупликации
     */
    private void cleanupCache(long currentTime) {
        long cutoffTime = currentTime - CACHE_CLEANUP_INTERVAL_MS;
        lastProcessedTime.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
        log.debug("Cleaned up deduplication cache, remaining entries: {}",
                lastProcessedTime.size());
    }

    /**
     * Получение статистики процессора
     */
    public Map<String, Object> getStats() {
        return Map.of("totalProcessed", totalProcessed.get(), "lastPriceProcessed",
                lastPriceProcessed.count(), "tradeProcessed", tradeProcessed.count(),
                "orderBookProcessed", orderBookProcessed.count(), "processingTime",
                processingTimer.totalTime(TimeUnit.MILLISECONDS), "uniqueInstruments",
                lastProcessedTime.size());
    }
}
