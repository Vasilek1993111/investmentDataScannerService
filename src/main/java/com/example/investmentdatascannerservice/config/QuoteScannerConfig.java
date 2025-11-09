package com.example.investmentdatascannerservice.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import lombok.Data;

/**
 * Конфигурация для сканера котировок
 * 
 * Позволяет настраивать параметры производительности и режимы работы через application.properties
 */
@ConfigurationProperties(prefix = "quote-scanner")
@Data
public class QuoteScannerConfig implements ApplicationContextAware {

    private Environment environment;

    /**
     * Максимальное количество котировок в секунду для обработки
     */
    private int maxQuotesPerSecond = 10000;

    /**
     * Включить/выключить сохранение в БД
     */
    private boolean enableDatabaseSaving = false;

    /**
     * Включить/выключить WebSocket трансляцию
     */
    private boolean enableWebSocketBroadcast = true;

    /**
     * Карта имен инструментов (FIGI -> Название)
     */
    private Map<String, String> instrumentNames = Map.of();

    /**
     * Включить/выключить подписку на стаканы
     */
    private boolean enableOrderBookSubscription = false;

    /**
     * Глубина стакана (количество уровней)
     */
    private int orderBookDepth = 10;

    /**
     * Включить/выключить немедленную отправку обновлений стакана
     */
    private boolean enableImmediateOrderBookUpdates = true;

    /**
     * Режим сканирования для утреннего сканера true - сканировать все акции из таблицы
     * invest.shares false - использовать инструменты из конфигурации
     */
    private boolean enableSharesMode = true;

    /**
     * Ключевая ставка ЦБ РФ (в процентах) Используется для расчета справедливого расхождения между
     * фьючерсом и акцией Формула: справедливое расхождение = ключевая ставка / 365 * количество
     * дней до экспирации
     */
    private double keyRate = 16.5;

    /**
     * Проверяет, включен ли тестовый режим для утреннего сканера Определяется автоматически на
     * основе активного Spring профиля (test = true, иначе = false)
     */
    public boolean isTestModeMorning() {
        return isTestProfileActive();
    }

    /**
     * Проверяет, включен ли тестовый режим для сканера выходного дня Определяется автоматически на
     * основе активного Spring профиля (test = true, иначе = false)
     */
    public boolean isTestModeWeekend() {
        return isTestProfileActive();
    }

    /**
     * Проверяет, включен ли тестовый режим для сканера фьючерсов Определяется автоматически на
     * основе активного Spring профиля (test = true, иначе = false)
     */
    public boolean isTestModeFutures() {
        return isTestProfileActive();
    }

    /**
     * Проверяет, активен ли тестовый профиль Spring
     */
    private boolean isTestProfileActive() {
        if (environment == null) {
            return false;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.environment = applicationContext.getEnvironment();
    }
}
