package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.investmentdatascannerservice.entity.ClosePriceEntity;
import com.example.investmentdatascannerservice.entity.ClosePriceEveningSessionEntity;
import com.example.investmentdatascannerservice.entity.OpenPriceEntity;
import com.example.investmentdatascannerservice.repository.ClosePriceEveningSessionRepository;
import com.example.investmentdatascannerservice.repository.ClosePriceRepository;
import com.example.investmentdatascannerservice.repository.LastPriceRepository;
import com.example.investmentdatascannerservice.repository.OpenPriceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для кэширования цен закрытия, открытия и вечерней сессии
 * 
 * Предоставляет быстрый доступ к историческим ценам инструментов с использованием in-memory кэша и
 * Spring Cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceCacheService {

    private final ClosePriceRepository closePriceRepository;
    private final ClosePriceEveningSessionRepository closePriceEveningSessionRepository;
    private final OpenPriceRepository openPriceRepository;
    private final LastPriceRepository lastPriceRepository;

    // In-memory кэш для быстрого доступа - только последние цены
    private final Map<String, BigDecimal> lastClosePricesCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastEveningSessionPricesCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastOpenPricesCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastPricesCache = new ConcurrentHashMap<>();

    // Кэш для последних доступных дат
    private LocalDate lastClosePriceDate;
    private LocalDate lastEveningSessionDate;
    private LocalDate lastOpenPriceDate;
    private LocalDate lastPriceDate;

    @PostConstruct
    public void initializeCache() {
        log.info("Initializing price cache...");
        loadAllClosePrices();
        loadAllEveningSessionPrices();
        loadAllOpenPrices();
        loadAllLastPrices();
        log.info("Price cache initialized successfully");
    }

    /**
     * Загрузка последних цен закрытия в кэш с унифицированной логикой определения даты
     */
    @Transactional(readOnly = true)
    public void loadAllClosePrices() {
        try {
            // Используем унифицированную логику определения даты (с учетом выходных)
            LocalDate targetDate = getLastTradingDate();
            log.info("Loading close prices for target date: {} (unified weekend logic)",
                    targetDate);

            // Загружаем цены для целевой даты
            loadClosePricesForDate(targetDate);

        } catch (Exception e) {
            log.error("Error loading latest close prices into cache", e);
        }
    }

    /**
     * Загрузка последних цен вечерней сессии в кэш с унифицированной логикой определения даты
     */
    @Transactional(readOnly = true)
    public void loadAllEveningSessionPrices() {
        try {
            // Используем унифицированную логику определения даты (с учетом выходных)
            LocalDate targetDate = getLastTradingDate();
            log.info("Loading evening session prices for target date: {} (unified weekend logic)",
                    targetDate);

            // Загружаем цены для целевой даты
            loadEveningSessionPricesForDate(targetDate);

        } catch (Exception e) {
            log.error("Error loading latest evening session prices into cache", e);
        }
    }


    /**
     * Получение последней цены закрытия для инструмента
     */
    public BigDecimal getLastClosePrice(String figi) {
        return lastClosePricesCache.get(figi);
    }

    /**
     * Получение последней цены закрытия вечерней сессии для инструмента
     */
    public BigDecimal getLastEveningSessionPrice(String figi) {
        return lastEveningSessionPricesCache.get(figi);
    }

    /**
     * Очистка кэша
     */
    public void clearCache() {
        lastClosePricesCache.clear();
        lastEveningSessionPricesCache.clear();
        lastOpenPricesCache.clear();
        lastPricesCache.clear();
        lastClosePriceDate = null;
        lastEveningSessionDate = null;
        lastOpenPriceDate = null;
        lastPriceDate = null;
        log.info("Price cache cleared");
    }

    /**
     * Перезагрузка кэша с унифицированной логикой
     */
    public void reloadCache() {
        log.info("Reloading price cache with unified weekend logic...");
        clearCache();
        loadAllClosePrices();
        loadAllEveningSessionPrices();
        loadAllOpenPrices();
        loadAllLastPrices();
        log.info("Price cache reloaded successfully with unified weekend logic");
    }

    /**
     * Загрузка последних цен открытия в кэш с унифицированной логикой определения даты
     */
    @Transactional(readOnly = true)
    public void loadAllOpenPrices() {
        try {
            // Используем унифицированную логику определения даты (с учетом выходных)
            LocalDate targetDate = getLastTradingDate();
            log.info("Loading open prices for target date: {} (unified weekend logic)", targetDate);

            // Загружаем цены для целевой даты
            loadOpenPricesForDate(targetDate);

        } catch (Exception e) {
            log.error("Error loading open prices", e);
        }
    }



    /**
     * Получение всех цен закрытия из кэша
     */
    public Map<String, BigDecimal> getAllClosePrices() {
        return Map.copyOf(lastClosePricesCache);
    }

    /**
     * Получение всех цен вечерней сессии из кэша
     */
    public Map<String, BigDecimal> getAllEveningSessionPrices() {
        return Map.copyOf(lastEveningSessionPricesCache);
    }

    /**
     * Получение всех цен открытия из кэша
     */
    public Map<String, BigDecimal> getAllOpenPrices() {
        return Map.copyOf(lastOpenPricesCache);
    }

    /**
     * Получение цены открытия для инструмента из кэша
     */
    public BigDecimal getLastOpenPrice(String figi) {
        return lastOpenPricesCache.get(figi);
    }

    /**
     * Получение всех цен для конкретного инструмента
     */
    public Map<String, BigDecimal> getPricesForFigi(String figi) {
        Map<String, BigDecimal> prices = new java.util.HashMap<>();
        prices.put("closePrice", getLastClosePrice(figi));
        prices.put("eveningSessionPrice", getLastEveningSessionPrice(figi));
        prices.put("openPrice", getLastOpenPrice(figi));
        prices.put("lastPrice", getLastPrice(figi));
        return prices;
    }

    /**
     * Получение последней даты цен закрытия
     */
    public String getLastClosePriceDate() {
        return lastClosePriceDate != null ? lastClosePriceDate.toString() : "N/A";
    }

    /**
     * Получение последней даты цен вечерней сессии
     */
    public String getLastEveningSessionPriceDate() {
        return lastEveningSessionDate != null ? lastEveningSessionDate.toString() : "N/A";
    }

    /**
     * Получение последней даты цен открытия
     */
    public String getLastOpenPriceDate() {
        return lastOpenPriceDate != null ? lastOpenPriceDate.toString() : "N/A";
    }

    public String getLastEveningSessionDate() {
        return lastEveningSessionDate != null ? lastEveningSessionDate.toString() : "N/A";
    }

    /**
     * Получение статистики кэша
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("closePricesCount", lastClosePricesCache.size());
        stats.put("eveningSessionPricesCount", lastEveningSessionPricesCache.size());
        stats.put("openPricesCount", lastOpenPricesCache.size());
        stats.put("lastPricesCount", lastPricesCache.size());
        stats.put("instrumentsWithClosePrices", lastClosePricesCache.size());
        stats.put("instrumentsWithEveningSessionPrices", lastEveningSessionPricesCache.size());
        stats.put("instrumentsWithOpenPrices", lastOpenPricesCache.size());
        stats.put("instrumentsWithLastPrices", lastPricesCache.size());
        stats.put("lastClosePriceDate",
                lastClosePriceDate != null ? lastClosePriceDate.toString() : "N/A");
        stats.put("lastEveningSessionDate",
                lastEveningSessionDate != null ? lastEveningSessionDate.toString() : "N/A");
        stats.put("lastOpenPriceDate",
                lastOpenPriceDate != null ? lastOpenPriceDate.toString() : "N/A");
        stats.put("lastPriceDate", lastPriceDate != null ? lastPriceDate.toString() : "N/A");
        return stats;
    }

    /**
     * Принудительная перезагрузка кэша цен закрытия
     */
    public void forceReloadClosePricesCache() {
        log.info("Force reloading close prices cache...");
        lastClosePricesCache.clear();
        lastClosePriceDate = null;
        loadAllClosePrices();
        log.info("Close prices cache force reload completed. Cache size: {}, Last date: {}",
                lastClosePricesCache.size(), lastClosePriceDate);
    }

    /**
     * Получение последней торговой даты с учетом выходных дней и доступности данных в базе
     */
    private LocalDate getLastTradingDate() {
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        // Если сегодня выходные (суббота или воскресенье), возвращаем пятницу
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            // Находим последнюю пятницу
            int daysToSubtract = dayOfWeek == DayOfWeek.SATURDAY ? 1 : 2;
            LocalDate targetDate = today.minusDays(daysToSubtract);
            log.debug("Weekend detected ({}), using last Friday: {}", dayOfWeek, targetDate);
            return targetDate;
        }

        // Если сегодня рабочий день, проверяем есть ли данные за сегодня
        // Если данных за сегодня нет, ищем последний рабочий день с данными
        if (hasDataForDate(today)) {
            log.debug("Using today's date: {}", today);
            return today;
        } else {
            // Ищем последний рабочий день с данными
            LocalDate lastWorkingDay = findLastWorkingDayWithData(today);
            log.debug("No data for today ({}), using last working day with data: {}", today,
                    lastWorkingDay);
            return lastWorkingDay;
        }
    }

    /**
     * Проверяет, есть ли данные в базе для указанной даты
     */
    private boolean hasDataForDate(LocalDate date) {
        try {
            // Проверяем наличие данных в любой из таблиц цен
            Optional<LocalDate> latestCloseDate = closePriceRepository.findLatestPriceDate();
            if (latestCloseDate.isPresent() && !latestCloseDate.get().isBefore(date)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Error checking data availability for date: {}", date, e);
            return false;
        }
    }

    /**
     * Находит последний рабочий день с данными в базе
     */
    private LocalDate findLastWorkingDayWithData(LocalDate fromDate) {
        try {
            // Получаем максимальную дату из базы данных
            Optional<LocalDate> latestDate = closePriceRepository.findLatestPriceDate();
            if (latestDate.isPresent()) {
                LocalDate maxDate = latestDate.get();
                log.debug("Found latest date in database: {}", maxDate);
                return maxDate;
            }

            // Если данных нет, возвращаем вчерашний день
            LocalDate yesterday = fromDate.minusDays(1);
            log.warn("No data found in database, using yesterday: {}", yesterday);
            return yesterday;
        } catch (Exception e) {
            log.error("Error finding last working day with data", e);
            return fromDate.minusDays(1);
        }
    }

    /**
     * Принудительная перезагрузка всех типов цен с учетом выходных дней
     */
    public void forceReloadAllPricesCache() {
        log.info("Force reloading all prices cache with weekend logic...");

        LocalDate targetDate = getLastTradingDate();
        log.info("Target trading date for price reload: {} (weekend logic applied)", targetDate);

        // Очищаем все кэши
        lastClosePricesCache.clear();
        lastEveningSessionPricesCache.clear();
        lastOpenPricesCache.clear();
        lastPricesCache.clear();
        lastClosePriceDate = null;
        lastEveningSessionDate = null;
        lastOpenPriceDate = null;
        lastPriceDate = null;

        // Загружаем цены закрытия
        loadClosePricesForDate(targetDate);

        // Загружаем цены вечерней сессии
        loadEveningSessionPricesForDate(targetDate);

        // Загружаем цены открытия
        loadOpenPricesForDate(targetDate);

        // Загружаем последние цены
        loadAllLastPrices();

        log.info(
                "All prices cache force reload completed. Close prices: {}, Evening session: {}, Open prices: {}, Last prices: {}",
                lastClosePricesCache.size(), lastEveningSessionPricesCache.size(),
                lastOpenPricesCache.size(), lastPricesCache.size());
    }

    /**
     * Загрузка цен закрытия для конкретной даты
     */
    private void loadClosePricesForDate(LocalDate targetDate) {
        try {
            // Получаем цены закрытия для целевой даты
            List<ClosePriceEntity> closePrices = closePriceRepository.findByIdPriceDate(targetDate);

            for (ClosePriceEntity entity : closePrices) {
                String figi = entity.getId().getFigi();
                BigDecimal price = entity.getClosePrice();
                lastClosePricesCache.put(figi, price);
            }

            lastClosePriceDate = targetDate;
            log.info("Loaded {} close prices for date: {}", closePrices.size(), targetDate);

        } catch (Exception e) {
            log.error("Error loading close prices for date: {}", targetDate, e);
        }
    }

    /**
     * Загрузка цен вечерней сессии для конкретной даты
     */
    private void loadEveningSessionPricesForDate(LocalDate targetDate) {
        try {
            List<ClosePriceEveningSessionEntity> eveningSessionPrices =
                    closePriceEveningSessionRepository.findByPriceDate(targetDate);

            for (ClosePriceEveningSessionEntity entity : eveningSessionPrices) {
                String figi = entity.getFigi();
                BigDecimal price = entity.getClosePrice();
                lastEveningSessionPricesCache.put(figi, price);
            }

            lastEveningSessionDate = targetDate;
            log.info("Loaded {} evening session prices for date: {}", eveningSessionPrices.size(),
                    targetDate);

        } catch (Exception e) {
            log.error("Error loading evening session prices for date: {}", targetDate, e);
        }
    }

    /**
     * Загрузка цен открытия для конкретной даты
     */
    private void loadOpenPricesForDate(LocalDate targetDate) {
        try {
            List<OpenPriceEntity> openPrices = openPriceRepository.findByIdPriceDate(targetDate);

            for (OpenPriceEntity entity : openPrices) {
                String figi = entity.getId().getFigi();
                BigDecimal price = entity.getOpenPrice();
                lastOpenPricesCache.put(figi, price);
            }

            lastOpenPriceDate = targetDate;
            log.info("Loaded {} open prices for date: {}", openPrices.size(), targetDate);

        } catch (Exception e) {
            log.error("Error loading open prices for date: {}", targetDate, e);
        }
    }

    /**
     * Загрузка последних цен сделок (last_price) в кэш Загружает самую последнюю котировку по
     * каждому figi. Если котировки нет в текущем дне, смотрит в прошлые дни.
     */
    @Transactional(readOnly = true)
    public void loadAllLastPrices() {
        try {
            log.info("Loading last prices (latest quotes) for all instruments...");

            // Получаем последние цены для всех инструментов используя DISTINCT ON
            List<Object[]> results = lastPriceRepository.findLatestPricesForAllFigis();

            int loadedCount = 0;
            LocalDate latestDate = null;

            for (Object[] row : results) {
                try {
                    String figi = (String) row[0];
                    LocalDateTime time = (LocalDateTime) row[1];
                    BigDecimal price = (BigDecimal) row[2];

                    if (figi != null && price != null) {
                        lastPricesCache.put(figi, price);
                        loadedCount++;

                        // Обновляем последнюю дату
                        if (time != null) {
                            LocalDate priceDate = time.toLocalDate();
                            if (latestDate == null || priceDate.isAfter(latestDate)) {
                                latestDate = priceDate;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing last price row: {}", java.util.Arrays.toString(row),
                            e);
                }
            }

            lastPriceDate = latestDate;
            log.info("Loaded {} last prices. Latest date: {}", loadedCount,
                    latestDate != null ? latestDate.toString() : "N/A");

        } catch (Exception e) {
            log.error("Error loading last prices into cache", e);
        }
    }

    /**
     * Получение последней цены сделки для инструмента
     */
    public BigDecimal getLastPrice(String figi) {
        return lastPricesCache.get(figi);
    }

    /**
     * Получение всех последних цен сделок из кэша
     */
    public Map<String, BigDecimal> getAllLastPrices() {
        return Map.copyOf(lastPricesCache);
    }

    /**
     * Получение последней даты последних цен
     */
    public String getLastPriceDate() {
        return lastPriceDate != null ? lastPriceDate.toString() : "N/A";
    }

    /**
     * Принудительная перезагрузка кэша последних цен
     */
    public void forceReloadLastPricesCache() {
        log.info("Force reloading last prices cache...");
        lastPricesCache.clear();
        lastPriceDate = null;
        loadAllLastPrices();
        log.info("Last prices cache force reload completed. Cache size: {}, Last date: {}",
                lastPricesCache.size(), lastPriceDate);
    }
}
