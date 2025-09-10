package com.example.investmentdatascannerservice.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация для сканера котировок
 * 
 * Позволяет настраивать список инструментов для сканирования и параметры производительности через
 * application.properties
 */
@ConfigurationProperties(prefix = "quote-scanner")
public class QuoteScannerConfig {

    /**
     * Список FIGI инструментов для сканирования Если пустой, сканируются все доступные инструменты
     */
    private List<String> instruments = List.of();

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
     * Максимальная частота обновлений стакана в секунду (0 = без ограничений)
     */
    private int maxOrderBookUpdatesPerSecond = 0;

    // Геттеры и сеттеры
    public List<String> getInstruments() {
        return instruments;
    }

    public void setInstruments(List<String> instruments) {
        this.instruments = instruments;
    }

    public int getMaxQuotesPerSecond() {
        return maxQuotesPerSecond;
    }

    public void setMaxQuotesPerSecond(int maxQuotesPerSecond) {
        this.maxQuotesPerSecond = maxQuotesPerSecond;
    }

    public boolean isEnableDatabaseSaving() {
        return enableDatabaseSaving;
    }

    public void setEnableDatabaseSaving(boolean enableDatabaseSaving) {
        this.enableDatabaseSaving = enableDatabaseSaving;
    }

    public boolean isEnableWebSocketBroadcast() {
        return enableWebSocketBroadcast;
    }

    public void setEnableWebSocketBroadcast(boolean enableWebSocketBroadcast) {
        this.enableWebSocketBroadcast = enableWebSocketBroadcast;
    }

    public Map<String, String> getInstrumentNames() {
        return instrumentNames;
    }

    public void setInstrumentNames(Map<String, String> instrumentNames) {
        this.instrumentNames = instrumentNames;
    }

    public boolean isEnableOrderBookSubscription() {
        return enableOrderBookSubscription;
    }

    public void setEnableOrderBookSubscription(boolean enableOrderBookSubscription) {
        this.enableOrderBookSubscription = enableOrderBookSubscription;
    }

    public int getOrderBookDepth() {
        return orderBookDepth;
    }

    public void setOrderBookDepth(int orderBookDepth) {
        this.orderBookDepth = orderBookDepth;
    }

    public boolean isEnableImmediateOrderBookUpdates() {
        return enableImmediateOrderBookUpdates;
    }

    public void setEnableImmediateOrderBookUpdates(boolean enableImmediateOrderBookUpdates) {
        this.enableImmediateOrderBookUpdates = enableImmediateOrderBookUpdates;
    }

    public int getMaxOrderBookUpdatesPerSecond() {
        return maxOrderBookUpdatesPerSecond;
    }

    public void setMaxOrderBookUpdatesPerSecond(int maxOrderBookUpdatesPerSecond) {
        this.maxOrderBookUpdatesPerSecond = maxOrderBookUpdatesPerSecond;
    }
}
