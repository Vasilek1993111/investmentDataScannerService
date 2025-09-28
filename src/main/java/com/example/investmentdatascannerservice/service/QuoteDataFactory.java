package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import com.example.investmentdatascannerservice.dto.QuoteData;
import com.example.investmentdatascannerservice.utils.ClosePriceEveningSessionService;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Trade;
import ru.tinkoff.piapi.contract.v1.TradeDirection;

/**
 * Фабрика для создания QuoteData объектов
 * 
 * Оптимизированная фабрика для создания DTO с минимальными накладными расходами
 */
@Component
public class QuoteDataFactory {

    private final InstrumentCacheService cacheService;
    private final ClosePriceEveningSessionService eveningSessionService;

    public QuoteDataFactory(InstrumentCacheService cacheService,
            ClosePriceEveningSessionService eveningSessionService) {
        this.cacheService = cacheService;
        this.eveningSessionService = eveningSessionService;
    }

    /**
     * Создание QuoteData из LastPrice
     */
    public QuoteData createFromLastPrice(LastPrice price, BigDecimal currentPrice) {
        String figi = price.getFigi();

        // Получаем данные из кэша
        String ticker = cacheService.getInstrumentTicker(figi, figi);
        String instrumentName = cacheService.getInstrumentName(figi, figi);
        BigDecimal previousPrice = cacheService.getLastPrice(figi);
        BigDecimal closePrice = cacheService.getClosePrice(figi);
        BigDecimal openPrice = cacheService.getOpenPrice(figi);
        BigDecimal closePriceVS = eveningSessionService.getEveningClosePrice(figi);

        // Получаем данные стакана
        BigDecimal bestBid = cacheService.getBestBid(figi);
        BigDecimal bestAsk = cacheService.getBestAsk(figi);
        long bestBidQuantity = cacheService.getBestBidQuantity(figi);
        long bestAskQuantity = cacheService.getBestAskQuantity(figi);

        // Получаем агрегированные данные
        BigDecimal avgVolumeMorning = cacheService.getAvgVolumeMorning(figi);
        BigDecimal avgVolumeWeekend = cacheService.getAvgVolumeWeekend(figi);

        // Получаем накопленный объем
        long accumulatedVolume = cacheService.getAccumulatedVolume(figi);

        // Определяем направление
        String direction = calculateDirection(previousPrice, currentPrice);

        // Создаем QuoteData
        return new QuoteData(figi, ticker, instrumentName, currentPrice, previousPrice, closePrice,
                openPrice, closePrice, closePriceVS, // closePriceOS = closePrice
                bestBid, bestAsk, bestBidQuantity, bestAskQuantity,
                convertTimestamp(price.getTime()), 0L, // volume = 0 для LastPrice
                accumulatedVolume, // totalVolume
                direction, avgVolumeMorning, avgVolumeWeekend);
    }

    /**
     * Создание QuoteData из Trade
     */
    public QuoteData createFromTrade(Trade trade, BigDecimal currentPrice) {
        String figi = trade.getFigi();

        // Получаем данные из кэша
        String ticker = cacheService.getInstrumentTicker(figi, figi);
        String instrumentName = cacheService.getInstrumentName(figi, figi);
        BigDecimal previousPrice = cacheService.getLastPrice(figi);
        BigDecimal closePrice = cacheService.getClosePrice(figi);
        BigDecimal openPrice = cacheService.getOpenPrice(figi);
        BigDecimal closePriceVS = eveningSessionService.getEveningClosePrice(figi);

        // Получаем данные стакана
        BigDecimal bestBid = cacheService.getBestBid(figi);
        BigDecimal bestAsk = cacheService.getBestAsk(figi);
        long bestBidQuantity = cacheService.getBestBidQuantity(figi);
        long bestAskQuantity = cacheService.getBestAskQuantity(figi);

        // Получаем агрегированные данные
        BigDecimal avgVolumeMorning = cacheService.getAvgVolumeMorning(figi);
        BigDecimal avgVolumeWeekend = cacheService.getAvgVolumeWeekend(figi);

        // Получаем накопленный объем
        long accumulatedVolume = cacheService.getAccumulatedVolume(figi);

        // Определяем направление сделки
        String direction = "NEUTRAL";
        if (trade.getDirection() == TradeDirection.TRADE_DIRECTION_BUY) {
            direction = "UP";
        } else if (trade.getDirection() == TradeDirection.TRADE_DIRECTION_SELL) {
            direction = "DOWN";
        }

        // Создаем QuoteData
        return new QuoteData(figi, ticker, instrumentName, currentPrice, previousPrice, closePrice,
                openPrice, closePrice, closePriceVS, // closePriceOS = closePrice
                bestBid, bestAsk, bestBidQuantity, bestAskQuantity,
                convertTimestamp(trade.getTime()), trade.getQuantity(), // volume из сделки
                accumulatedVolume, // totalVolume
                direction, avgVolumeMorning, avgVolumeWeekend);
    }

    /**
     * Создание QuoteData из OrderBook (для немедленных обновлений стакана)
     */
    public QuoteData createFromOrderBook(String figi, BigDecimal bestBid, BigDecimal bestAsk,
            long bestBidQuantity, long bestAskQuantity) {
        // Получаем данные из кэша
        String ticker = cacheService.getInstrumentTicker(figi, figi);
        String instrumentName = cacheService.getInstrumentName(figi, figi);
        BigDecimal currentPrice = cacheService.getLastPrice(figi);
        BigDecimal previousPrice = cacheService.getLastPrice(figi);
        BigDecimal closePrice = cacheService.getClosePrice(figi);
        BigDecimal openPrice = cacheService.getOpenPrice(figi);
        BigDecimal closePriceVS = eveningSessionService.getEveningClosePrice(figi);

        // Если нет текущей цены, используем цену закрытия или 0
        if (currentPrice == null) {
            currentPrice = closePrice != null ? closePrice : BigDecimal.ZERO;
        }
        if (previousPrice == null) {
            previousPrice = currentPrice;
        }

        // Получаем агрегированные данные
        BigDecimal avgVolumeMorning = cacheService.getAvgVolumeMorning(figi);
        BigDecimal avgVolumeWeekend = cacheService.getAvgVolumeWeekend(figi);

        // Получаем накопленный объем
        long accumulatedVolume = cacheService.getAccumulatedVolume(figi);

        // Определяем направление
        String direction = calculateDirection(previousPrice, currentPrice);

        // Создаем QuoteData
        return new QuoteData(figi, ticker, instrumentName, currentPrice, previousPrice, closePrice,
                openPrice, closePrice, closePriceVS, // closePriceOS = closePrice
                bestBid, bestAsk, bestBidQuantity, bestAskQuantity, LocalDateTime.now(), 0L, // текущее
                                                                                             // время,
                                                                                             // volume
                                                                                             // = 0
                accumulatedVolume, // totalVolume
                direction, avgVolumeMorning, avgVolumeWeekend);
    }

    /**
     * Расчет направления изменения цены
     */
    private String calculateDirection(BigDecimal previousPrice, BigDecimal currentPrice) {
        if (previousPrice == null || currentPrice == null
                || previousPrice.compareTo(BigDecimal.ZERO) <= 0
                || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "NEUTRAL";
        }

        int comparison = currentPrice.compareTo(previousPrice);
        if (comparison > 0) {
            return "UP";
        } else if (comparison < 0) {
            return "DOWN";
        } else {
            return "NEUTRAL";
        }
    }

    /**
     * Конвертация времени из protobuf в LocalDateTime
     */
    private LocalDateTime convertTimestamp(com.google.protobuf.Timestamp timestamp) {
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return LocalDateTime.ofInstant(instant, ZoneOffset.of("+3"));
    }
}
