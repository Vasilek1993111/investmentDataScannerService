package com.example.investmentdatascannerservice.utils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import com.example.investmentdatascannerservice.repository.DividendRepository;
import com.example.investmentdatascannerservice.service.TodayVolumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для кэширования данных об инструментах
 * 
 * Централизует кэширование цен, имен, тикеров и объемов инструментов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentCacheService {

    private final QuoteScannerConfig config;
    private final ShareService shareService;
    private final FutureService futureService;
    private final IndicativeService indicativeService;
    private final SharesAggregatedDataService sharesAggregatedDataService;
    private final TodayVolumeService todayVolumeService;
    private final DividendRepository dividendRepository;

    // Кэш последних цен инструментов
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();

    // Кэш имен инструментов (FIGI -> Название)
    private final Map<String, String> instrumentNames = new ConcurrentHashMap<>();

    // Кэш тикеров инструментов (FIGI -> Тикер)
    private final Map<String, String> instrumentTickers = new ConcurrentHashMap<>();

    // Кэш средних утренних объемов
    private final Map<String, BigDecimal> avgVolumeMorningMap = new ConcurrentHashMap<>();

    // Кэш средних объемов выходного дня
    private final Map<String, BigDecimal> avgVolumeWeekendMap = new ConcurrentHashMap<>();

    // Кэш цен закрытия основной сессии
    private final Map<String, BigDecimal> closePrices = new ConcurrentHashMap<>();

    // Кэш цен открытия
    private final Map<String, BigDecimal> openPrices = new ConcurrentHashMap<>();

    // Кэш накопленных объемов
    private final Map<String, Long> accumulatedVolumes = new ConcurrentHashMap<>();

    // Кэш данных стакана заявок
    private final Map<String, BigDecimal> bestBids = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> bestAsks = new ConcurrentHashMap<>();
    private final Map<String, Long> bestBidQuantities = new ConcurrentHashMap<>();
    private final Map<String, Long> bestAskQuantities = new ConcurrentHashMap<>();

    // Кэш short-флагов (FIGI -> доступен ли шорт)
    private final Map<String, Boolean> shortFlagsByFigi = new ConcurrentHashMap<>();

    // Кэш дивидендных событий (FIGI -> true если есть событие с declared_date >= NOW-1)
    private final Map<String, Boolean> dividendFlagsByFigi = new ConcurrentHashMap<>();

    /**
     * Инициализация кэша инструментов
     */
    public void initializeCache() {
        log.info("Initializing instrument cache...");

        // Загружаем имена инструментов
        Map<String, String> namesToLoad = getInstrumentNamesForScanning();
        instrumentNames.putAll(namesToLoad);
        log.info("Loaded {} instrument names into cache", instrumentNames.size());

        // Загружаем тикеры инструментов
        Map<String, String> tickersToLoad = getInstrumentTickersForScanning();
        instrumentTickers.putAll(tickersToLoad);
        log.info("Loaded {} tickers into cache", instrumentTickers.size());

        // Загружаем уже проторгованные объемы из today_volume_view
        loadWeekendExchangeVolumes();

        if (!instrumentTickers.isEmpty()) {
            log.info("First 5 tickers in cache: {}",
                    instrumentTickers.entrySet().stream().limit(5)
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(java.util.stream.Collectors.toList()));
        }

        // Загружаем short-флаги акций и фьючерсов
        try {
            Map<String, Boolean> shareShortFlags = shareService.getShareShortFlags();
            shortFlagsByFigi.putAll(shareShortFlags);
            log.info("Loaded {} share short flags", shareShortFlags.size());
        } catch (Exception e) {
            log.warn("Failed to load share short flags: {}", e.getMessage());
        }
        try {
            Map<String, Boolean> futureShortFlags = futureService.getFutureShortFlags();
            shortFlagsByFigi.putAll(futureShortFlags);
            log.info("Loaded {} future short flags", futureShortFlags.size());
        } catch (Exception e) {
            log.warn("Failed to load future short flags: {}", e.getMessage());
        }

        // Загружаем дивидендные события по общей логике:
        // показываем значок D только если declared_date == today или today-1;
        // если сегодня воскресенье, также учитываем today-2 (для вечернего сканера)
        try {
            java.time.LocalDate todayMsk =
                    java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
            java.time.DayOfWeek dow = todayMsk.getDayOfWeek();
            int daysBack = (dow == java.time.DayOfWeek.SUNDAY) ? 2 : 1;
            java.time.LocalDate fromDate = todayMsk.minusDays(daysBack); // включительно
            java.time.LocalDate toDate = todayMsk.plusDays(1); // эксклюзивно (<= today)

            java.util.List<String> figis =
                    dividendRepository.findFigiWithDeclaredBetween(fromDate, toDate);
            figis.forEach(f -> dividendFlagsByFigi.put(f, true));
            log.info(
                    "Loaded {} dividend events for window [{}; {}) (MSK) for D-badge logic (daysBack={})",
                    figis.size(), fromDate, toDate, daysBack);
        } catch (Exception e) {
            log.warn("Failed to load dividend events: {}", e.getMessage());
        }

        log.info("Instrument cache initialized successfully");
    }

    /**
     * Получить последнюю цену инструмента
     */
    public BigDecimal getLastPrice(String figi) {
        return lastPrices.get(figi);
    }

    /**
     * Получить последнюю цену инструмента с значением по умолчанию
     */
    public BigDecimal getLastPrice(String figi, BigDecimal defaultValue) {
        return lastPrices.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить последнюю цену инструмента
     */
    public void setLastPrice(String figi, BigDecimal price) {
        lastPrices.put(figi, price);
    }

    /**
     * Получить имя инструмента
     */
    public String getInstrumentName(String figi) {
        String name = instrumentNames.get(figi);
        if (name == null) {
            // Если имя не найдено, пытаемся загрузить его из базы данных
            log.debug("Name not found in cache for FIGI: {}, attempting to load from database",
                    figi);
            Map<String, String> namesToLoad = getInstrumentNamesForScanning();
            instrumentNames.putAll(namesToLoad);
            name = instrumentNames.get(figi);
        }
        return name != null ? name : figi;
    }

    /**
     * Получить имя инструмента с значением по умолчанию
     */
    public String getInstrumentName(String figi, String defaultValue) {
        String name = instrumentNames.get(figi);
        if (name == null) {
            // Если имя не найдено, пытаемся загрузить его из базы данных
            log.debug("Name not found in cache for FIGI: {}, attempting to load from database",
                    figi);
            Map<String, String> namesToLoad = getInstrumentNamesForScanning();
            instrumentNames.putAll(namesToLoad);
            name = instrumentNames.get(figi);
        }
        return name != null ? name : defaultValue;
    }

    /**
     * Получить тикер инструмента
     */
    public String getInstrumentTicker(String figi) {
        String ticker = instrumentTickers.get(figi);
        if (ticker == null) {
            // Если тикер не найден, пытаемся загрузить его из базы данных
            log.debug("Ticker not found in cache for FIGI: {}, attempting to load from database",
                    figi);
            Map<String, String> tickersToLoad = getInstrumentTickersForScanning();
            instrumentTickers.putAll(tickersToLoad);
            ticker = instrumentTickers.get(figi);
        }
        return ticker != null ? ticker : figi;
    }

    /**
     * Получить тикер инструмента с значением по умолчанию
     */
    public String getInstrumentTicker(String figi, String defaultValue) {
        String ticker = instrumentTickers.get(figi);
        if (ticker == null) {
            // Если тикер не найден, пытаемся загрузить его из базы данных
            log.debug("Ticker not found in cache for FIGI: {}, attempting to load from database",
                    figi);
            Map<String, String> tickersToLoad = getInstrumentTickersForScanning();
            instrumentTickers.putAll(tickersToLoad);
            ticker = instrumentTickers.get(figi);
        }
        return ticker != null ? ticker : defaultValue;
    }

    /**
     * Получить FIGI по тикеру
     * 
     * Ищет в кэше, при необходимости загружает данные из базы
     */
    public String getFigiByTicker(String ticker) {
        if (ticker == null || ticker.trim().isEmpty()) {
            return null;
        }

        // Ищем в кэше
        for (Map.Entry<String, String> entry : instrumentTickers.entrySet()) {
            if (ticker.equals(entry.getValue()) || ticker.equalsIgnoreCase(entry.getValue())) {
                return entry.getKey();
            }
        }

        // Если не найдено, пытаемся загрузить из базы
        log.debug("FIGI not found in cache for ticker: {}, attempting to load from database",
                ticker);
        Map<String, String> tickersToLoad = getInstrumentTickersForScanning();
        instrumentTickers.putAll(tickersToLoad);

        // Повторяем поиск
        for (Map.Entry<String, String> entry : instrumentTickers.entrySet()) {
            if (ticker.equals(entry.getValue()) || ticker.equalsIgnoreCase(entry.getValue())) {
                log.debug("Found FIGI {} for ticker {}", entry.getKey(), ticker);
                return entry.getKey();
            }
        }

        log.warn("FIGI not found for ticker: {}", ticker);
        return null;
    }

    /**
     * Получить средний утренний объем
     */
    public BigDecimal getAvgVolumeMorning(String figi) {
        return avgVolumeMorningMap.get(figi);
    }

    /**
     * Получить средний утренний объем с значением по умолчанию
     */
    public BigDecimal getAvgVolumeMorning(String figi, BigDecimal defaultValue) {
        return avgVolumeMorningMap.getOrDefault(figi, defaultValue);
    }

    /**
     * Получить средний объем выходного дня
     */
    public BigDecimal getAvgVolumeWeekend(String figi) {
        return avgVolumeWeekendMap.get(figi);
    }

    /**
     * Получить средний объем выходного дня с значением по умолчанию
     */
    public BigDecimal getAvgVolumeWeekend(String figi, BigDecimal defaultValue) {
        return avgVolumeWeekendMap.getOrDefault(figi, defaultValue);
    }

    /**
     * Получить цену закрытия основной сессии
     */
    public BigDecimal getClosePrice(String figi) {
        return closePrices.get(figi);
    }

    /**
     * Получить цену закрытия основной сессии с значением по умолчанию
     */
    public BigDecimal getClosePrice(String figi, BigDecimal defaultValue) {
        return closePrices.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить цену закрытия основной сессии
     */
    public void setClosePrice(String figi, BigDecimal price) {
        closePrices.put(figi, price);
    }

    /**
     * Получить цену открытия
     */
    public BigDecimal getOpenPrice(String figi) {
        return openPrices.get(figi);
    }

    /**
     * Получить цену открытия с значением по умолчанию
     */
    public BigDecimal getOpenPrice(String figi, BigDecimal defaultValue) {
        return openPrices.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить цену открытия
     */
    public void setOpenPrice(String figi, BigDecimal price) {
        openPrices.put(figi, price);
    }

    /**
     * Получить накопленный объем
     */
    public Long getAccumulatedVolume(String figi) {
        return accumulatedVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить накопленный объем с значением по умолчанию
     */
    public Long getAccumulatedVolume(String figi, Long defaultValue) {
        return accumulatedVolumes.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить накопленный объем
     */
    public void setAccumulatedVolume(String figi, Long volume) {
        accumulatedVolumes.put(figi, volume);
    }

    /**
     * Добавить к накопленному объему
     */
    public void addToAccumulatedVolume(String figi, Long additionalVolume) {
        accumulatedVolumes.merge(figi, additionalVolume, Long::sum);
    }

    /**
     * Получить лучший BID
     */
    public BigDecimal getBestBid(String figi) {
        return bestBids.get(figi);
    }

    /**
     * Получить лучший BID с значением по умолчанию
     */
    public BigDecimal getBestBid(String figi, BigDecimal defaultValue) {
        return bestBids.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить лучший BID
     */
    public void setBestBid(String figi, BigDecimal price) {
        bestBids.put(figi, price);
    }

    /**
     * Получить лучший ASK
     */
    public BigDecimal getBestAsk(String figi) {
        return bestAsks.get(figi);
    }

    /**
     * Получить лучший ASK с значением по умолчанию
     */
    public BigDecimal getBestAsk(String figi, BigDecimal defaultValue) {
        return bestAsks.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить лучший ASK
     */
    public void setBestAsk(String figi, BigDecimal price) {
        bestAsks.put(figi, price);
    }

    /**
     * Получить количество лотов лучшего BID
     */
    public Long getBestBidQuantity(String figi) {
        return bestBidQuantities.getOrDefault(figi, 0L);
    }

    /**
     * Получить количество лотов лучшего BID с значением по умолчанию
     */
    public Long getBestBidQuantity(String figi, Long defaultValue) {
        return bestBidQuantities.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить количество лотов лучшего BID
     */
    public void setBestBidQuantity(String figi, Long quantity) {
        bestBidQuantities.put(figi, quantity);
    }

    /**
     * Получить количество лотов лучшего ASK
     */
    public Long getBestAskQuantity(String figi) {
        return bestAskQuantities.getOrDefault(figi, 0L);
    }

    /**
     * Получить количество лотов лучшего ASK с значением по умолчанию
     */
    public Long getBestAskQuantity(String figi, Long defaultValue) {
        return bestAskQuantities.getOrDefault(figi, defaultValue);
    }

    /**
     * Установить количество лотов лучшего ASK
     */
    public void setBestAskQuantity(String figi, Long quantity) {
        bestAskQuantities.put(figi, quantity);
    }

    /**
     * Получить все отслеживаемые инструменты
     */
    public Set<String> getTrackedInstruments() {
        return Set.copyOf(lastPrices.keySet());
    }

    /**
     * Получить все доступные инструменты с ценами
     */
    public Map<String, BigDecimal> getAvailableInstruments() {
        return new HashMap<>(lastPrices);
    }

    /**
     * Получить все доступные имена инструментов
     */
    public Map<String, String> getAvailableInstrumentNames() {
        return new HashMap<>(instrumentNames);
    }

    /**
     * Получить статистику кэша
     */
    public Map<String, Object> getCacheStats() {
        return Map.of("trackedInstruments", lastPrices.size(), "instrumentNames",
                instrumentNames.size(), "instrumentTickers", instrumentTickers.size(),
                "avgVolumeMorning", avgVolumeMorningMap.size(), "avgVolumeWeekend",
                avgVolumeWeekendMap.size(), "closePrices", closePrices.size(), "openPrices",
                openPrices.size(), "accumulatedVolumes", accumulatedVolumes.size(), "bestBids",
                bestBids.size(), "bestAsks", bestAsks.size());
    }

    /**
     * Очистить кэш
     */
    public void clearCache() {
        lastPrices.clear();
        instrumentNames.clear();
        instrumentTickers.clear();
        avgVolumeMorningMap.clear();
        avgVolumeWeekendMap.clear();
        closePrices.clear();
        openPrices.clear();
        // НЕ очищаем accumulatedVolumes - они содержат уже проторгованные объемы
        bestBids.clear();
        bestAsks.clear();
        bestBidQuantities.clear();
        bestAskQuantities.clear();
        log.info("Instrument cache cleared (preserving accumulated volumes)");
    }

    /**
     * Перезагрузить кэш инструментов
     */
    public void reloadCache() {
        log.info("Reloading instrument cache...");
        clearCache();
        initializeCache();
        log.info("Instrument cache reloaded successfully");
    }



    /**
     * Загрузить средние утренние объемы
     */
    public void loadAvgVolumeMorning(List<String> figis) {
        Map<String, BigDecimal> loadedAvgVolumes =
                sharesAggregatedDataService.getAvgVolumeMorningMap(figis);
        avgVolumeMorningMap.putAll(loadedAvgVolumes);
        log.info("Loaded {} average morning volumes into cache", loadedAvgVolumes.size());
    }

    /**
     * Загрузить средние объемы выходного дня
     */
    public void loadAvgVolumeWeekend(List<String> figis) {
        Map<String, BigDecimal> loadedAvgVolumes =
                sharesAggregatedDataService.getAvgVolumeWeekendMap(figis);
        avgVolumeWeekendMap.putAll(loadedAvgVolumes);
        log.info("Loaded {} average weekend volumes into cache", loadedAvgVolumes.size());
    }

    /**
     * Загрузить цены закрытия основной сессии
     */
    public void loadClosePrices(Map<String, BigDecimal> prices) {
        closePrices.putAll(prices);
        log.info("Loaded {} close prices into cache", prices.size());
    }

    /**
     * Загрузить цены открытия
     */
    public void loadOpenPrices(Map<String, BigDecimal> prices) {
        openPrices.putAll(prices);
        log.info("Loaded {} open prices into cache", prices.size());
    }

    /**
     * Загрузить имена инструментов
     */
    public void loadInstrumentNames(Map<String, String> names) {
        instrumentNames.putAll(names);
        log.info("Loaded {} instrument names into cache", names.size());
    }

    /**
     * Загрузка уже проторгованных объемов из today_volume_view
     */
    public void loadWeekendExchangeVolumes() {
        try {
            Map<String, Long> todayVolumes = todayVolumeService.getAllTotalVolumes();

            // Очищаем накопленные объемы перед загрузкой новых
            accumulatedVolumes.clear();

            // Загружаем проторгованные объемы (перезаписываем, а не суммируем)
            accumulatedVolumes.putAll(todayVolumes);

            log.info("Cleared and loaded {} today volumes into accumulated volumes cache",
                    todayVolumes.size());

            // Логируем статистику
            long totalVolume = todayVolumes.values().stream().mapToLong(Long::longValue).sum();
            long instrumentsWithVolume =
                    todayVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum();

            log.info("Today volume statistics: {} instruments with volume, total volume: {}",
                    instrumentsWithVolume, totalVolume);

        } catch (Exception e) {
            log.error("Error loading today volumes into accumulated volumes", e);
        }
    }

    /**
     * Получить инструменты для сканирования
     * 
     * Загружает все акции из таблицы invest.shares, фьючерсы из таблицы invest.futures и индексы из
     * таблицы invest.indicatives. Режим shares-mode определяет только дополнительные настройки
     * отображения.
     */
    public List<String> getInstrumentsForScanning() {
        // Загружаем все акции из базы данных
        List<String> shareFigis = shareService.getAllShareFigis();

        // Загружаем все фьючерсы из базы данных
        List<String> futureFigis = futureService.getAllFutureFigis();

        // Загружаем все индексы из базы данных
        List<String> indicativeFigis = indicativeService.getAllIndicativeFigis();

        // Объединяем списки
        List<String> allFigis = new java.util.ArrayList<>();
        allFigis.addAll(shareFigis);
        allFigis.addAll(futureFigis);
        allFigis.addAll(indicativeFigis);

        if (config.isEnableSharesMode()) {
            log.info(
                    "Using shares mode: {} shares + {} futures + {} indicatives = {} total instruments from database",
                    shareFigis.size(), futureFigis.size(), indicativeFigis.size(), allFigis.size());
        } else {
            log.info(
                    "Using config mode: {} shares + {} futures + {} indicatives = {} total instruments from database",
                    shareFigis.size(), futureFigis.size(), indicativeFigis.size(), allFigis.size());
        }

        if (allFigis.isEmpty()) {
            log.warn(
                    "No instruments found in database! Check if tables invest.shares, invest.futures and invest.indicatives have data");
        } else {
            log.info("First 5 instruments: {}", allFigis.subList(0, Math.min(5, allFigis.size())));
        }

        return allFigis;
    }

    /**
     * Получить инструменты для сканирования включая динамические индексы
     * 
     * Этот метод должен использоваться QuoteScannerService для получения полного списка
     * инструментов
     */
    public List<String> getInstrumentsForScanningWithDynamic() {
        List<String> baseInstruments = getInstrumentsForScanning();
        // Динамические индексы добавляются в QuoteScannerService
        return baseInstruments;
    }

    /**
     * Получить имена инструментов для сканирования
     * 
     * Загружает имена из таблиц invest.shares, invest.futures и invest.indicatives.
     */
    public Map<String, String> getInstrumentNamesForScanning() {
        // Загружаем имена акций из базы данных
        Map<String, String> shareNames = shareService.getShareNames();

        // Загружаем имена фьючерсов из базы данных
        Map<String, String> futureNames = futureService.getFutureNames();

        // Загружаем имена индексов из базы данных
        Map<String, String> indicativeNames = indicativeService.getIndicativeNames();

        // Объединяем карты
        Map<String, String> allNames = new java.util.HashMap<>();
        allNames.putAll(shareNames);
        allNames.putAll(futureNames);
        allNames.putAll(indicativeNames);

        if (config.isEnableSharesMode()) {
            log.info(
                    "Using shares names: {} shares + {} futures + {} indicatives = {} total names from database",
                    shareNames.size(), futureNames.size(), indicativeNames.size(), allNames.size());
        } else {
            log.info(
                    "Using config mode names: {} shares + {} futures + {} indicatives = {} total names from database",
                    shareNames.size(), futureNames.size(), indicativeNames.size(), allNames.size());
        }

        return allNames;
    }

    /**
     * Получить тикеры инструментов для сканирования
     * 
     * Загружает тикеры из таблиц invest.shares, invest.futures и invest.indicatives.
     */
    public Map<String, String> getInstrumentTickersForScanning() {
        // Загружаем тикеры акций из базы данных
        Map<String, String> shareTickers = shareService.getShareTickers();

        // Загружаем тикеры фьючерсов из базы данных
        Map<String, String> futureTickers = futureService.getFutureTickers();

        // Загружаем тикеры индексов из базы данных
        Map<String, String> indicativeTickers = indicativeService.getIndicativeTickers();

        // Объединяем карты
        Map<String, String> allTickers = new java.util.HashMap<>();
        allTickers.putAll(shareTickers);
        allTickers.putAll(futureTickers);
        allTickers.putAll(indicativeTickers);

        if (config.isEnableSharesMode()) {
            log.info(
                    "Using shares tickers: {} shares + {} futures + {} indicatives = {} total tickers from database",
                    shareTickers.size(), futureTickers.size(), indicativeTickers.size(),
                    allTickers.size());
        } else {
            log.info(
                    "Using config mode tickers: {} shares + {} futures + {} indicatives = {} total tickers from database",
                    shareTickers.size(), futureTickers.size(), indicativeTickers.size(),
                    allTickers.size());
        }

        return allTickers;
    }

    /**
     * Проверить, является ли инструмент индикативом
     * 
     * @param figi FIGI инструмента
     * @return true, если инструмент является индикативом
     */
    public boolean isIndicative(String figi) {
        if (figi == null) {
            return false;
        }
        // Проверяем по тикеру индикативов (если FIGI есть в списке тикеров индикативов)
        Map<String, String> indicativeTickers = indicativeService.getIndicativeTickers();
        return indicativeTickers.containsKey(figi);
    }

    /**
     * Получить ShareService для доступа к акциям
     */
    public ShareService getShareService() {
        return shareService;
    }

    /**
     * Получить FutureService для доступа к фьючерсам
     */
    public FutureService getFutureService() {
        return futureService;
    }

    /**
     * Получить IndicativeService для доступа к индикативам
     */
    public IndicativeService getIndicativeService() {
        return indicativeService;
    }

    /**
     * Получить short-флаг по FIGI (true если шорт доступен)
     */
    public boolean isShortEnabled(String figi) {
        return Boolean.TRUE.equals(shortFlagsByFigi.get(figi));
    }

    /**
     * Есть ли дивидендное событие (declared_date >= now-1d)
     */
    public boolean hasRecentDividend(String figi) {
        return Boolean.TRUE.equals(dividendFlagsByFigi.get(figi));
    }
}
