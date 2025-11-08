package com.example.investmentdatascannerservice.utils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для работы с временными сессиями торгов
 * 
 * Управляет логикой определения времени утренних сессий и сессий выходного дня
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionTimeService {

    private final QuoteScannerConfig config;

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

    /**
     * Проверяет, находится ли текущее время в рамках утренней сессии
     * 
     * @return true если сейчас время утренней сессии
     */
    public boolean isMorningSessionTime() {
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
     * 
     * @return true если сейчас время сессии выходного дня
     */
    public boolean isWeekendSessionTime() {
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
            return true; // с 02:00
        }

        if (currentHour == WEEKEND_SESSION_END_HOUR) {
            return currentMinute <= WEEKEND_SESSION_END_MINUTE; // до 23:50
        }

        return false;
    }

    /**
     * Проверяет, является ли текущее время сессией выходного дня (публичный метод)
     * 
     * @return true если сейчас время сессии выходного дня
     */
    public boolean checkWeekendSessionTime() {
        return isWeekendSessionTime();
    }

    /**
     * Получает текущее московское время
     * 
     * @return LocalDateTime в московском часовом поясе
     */
    public LocalDateTime getCurrentMoscowTime() {
        return LocalDateTime.now(ZoneOffset.of("+3"));
    }

    /**
     * Получает информацию о текущей сессии
     * 
     * @return строка с описанием текущей сессии
     */
    public String getCurrentSessionInfo() {
        if (config.isEnableTestMode()) {
            return "TEST_MODE (все сессии активны)";
        }

        if (isMorningSessionTime()) {
            return "MORNING_SESSION (утренняя сессия)";
        }

        if (isWeekendSessionTime()) {
            return "WEEKEND_SESSION (сессия выходного дня)";
        }

        return "NO_SESSION (вне торговых сессий)";
    }

    /**
     * Проверяет, активна ли какая-либо торговая сессия
     * 
     * @return true если активна утренняя или выходная сессия
     */
    public boolean isAnySessionActive() {
        return isMorningSessionTime() || isWeekendSessionTime();
    }

    /**
     * Проверяет, можно ли подписываться на фьючерсы в выходные дни
     * 
     * В субботу и воскресенье подписка на фьючерсы (LastPrice, Trades, OrderBook) разрешена только
     * с 8:30 утра и позднее
     * 
     * @return true если сейчас рабочий день или выходной день после 8:30
     */
    public boolean canSubscribeToFutures() {
        // Если включен тестовый режим, всегда разрешаем подписку
        if (config.isEnableTestMode()) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.of("+3")); // Московское время
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        // Если это не выходной день (суббота или воскресенье), разрешаем подписку
        if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
            return true;
        }

        // Если это выходной день, проверяем время: должно быть 8:30 или позже
        if (currentHour > 8) {
            return true; // После 9:00
        }

        if (currentHour == 8) {
            return currentMinute >= 30; // С 8:30
        }

        // До 8:30 в выходные дни - подписка запрещена
        return false;
    }
}
