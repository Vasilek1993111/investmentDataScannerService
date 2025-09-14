package com.example.investmentdatascannerservice.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Конфигурация для сканера котировок
 * 
 * Позволяет настраивать параметры производительности и режимы работы через application.properties
 */
@ConfigurationProperties(prefix = "quote-scanner")
@Data
public class QuoteScannerConfig {

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
     * Включить/выключить тестовый режим (работа вне времени утренней сессии)
     */
    private boolean enableTestMode = false;

    /**
     * Режим сканирования для утреннего сканера true - сканировать все акции из таблицы
     * invest.shares false - использовать инструменты из конфигурации
     */
    private boolean enableSharesMode = true;
}
