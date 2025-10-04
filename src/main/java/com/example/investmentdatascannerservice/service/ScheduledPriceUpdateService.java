package com.example.investmentdatascannerservice.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для автоматического обновления кэша цен по расписанию
 * 
 * Обновляет кэш цен закрытия каждый день в 6:00 по московскому времени
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPriceUpdateService {

    private final PriceCacheService priceCacheService;

    /**
     * Автоматическое обновление всех типов цен каждый день в 6:00 по московскому времени с
     * унифицированной логикой выходных дней (выходные используют пятницу, рабочие дни используют
     * сегодня или последние доступные данные)
     * 
     * Cron выражение: 0 0 6 * * ? - каждый день в 6:00:00 Timezone: Europe/Moscow
     */
    @Scheduled(cron = "0 0 6 * * ?", zone = "Europe/Moscow")
    public void updateAllPricesCacheWithWeekendLogic() {
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
            log.info("Starting scheduled all prices cache update with unified weekend logic at {}",
                    now);

            // Обновляем все типы цен с унифицированной логикой выходных дней
            priceCacheService.forceReloadAllPricesCache();

            log.info(
                    "Scheduled all prices cache update with unified weekend logic completed successfully at {}",
                    LocalDateTime.now(ZoneId.of("Europe/Moscow")));

        } catch (Exception e) {
            log.error("Error during scheduled all prices cache update with unified weekend logic",
                    e);
        }
    }


    /**
     * Проверка состояния кэша каждый час
     * 
     * Cron выражение: 0 0 * * * ? - каждый час в 0 минут Timezone: Europe/Moscow
     */
    @Scheduled(cron = "0 0 * * * ?", zone = "Europe/Moscow")
    public void checkCacheHealth() {
        try {
            String lastClosePriceDate = priceCacheService.getLastClosePriceDate();
            int closePricesCount = priceCacheService.getAllClosePrices().size();

            log.debug("Cache health check - Close prices: {} items, Last date: {}",
                    closePricesCount, lastClosePriceDate);

            // Если кэш пустой или данные очень старые, логируем предупреждение
            if (closePricesCount == 0) {
                log.warn("Close prices cache is empty!");
            }

        } catch (Exception e) {
            log.error("Error during cache health check", e);
        }
    }
}
