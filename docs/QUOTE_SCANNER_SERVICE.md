# QuoteScannerService - Детальная документация

## Обзор

`QuoteScannerService` — центральный сервис системы сканирования котировок, который координирует получение, обработку и распространение рыночных данных в реальном времени. Сервис реализует паттерн "Facade" и делегирует специализированную обработку другим сервисам для обеспечения высокой производительности и поддерживаемости.

### Ключевые особенности архитектуры

- **Разделение ответственности** — использование специализированных сервисов (`MarketDataProcessor`, `NotificationService`, `InstrumentCacheService`)
- **Управление индексами** — делегировано `IndexBarManager` (не является Spring-бином)
- **Централизованный кэш цен** — использование `PriceCacheService` для всех типов цен
- **Динамическое управление** — добавление и удаление индексов во время работы

## Назначение

Сервис выполняет следующие основные функции:

1. **Координация обработки данных** — управляет потоком данных от T-Invest API до клиентов
2. **Управление индексами** — динамическое управление списком отслеживаемых индексов
3. **Управление сессиями** — контроль активности сканера в зависимости от торговых сессий
4. **Агрегация статистики** — сбор и предоставление статистики работы системы
5. **Инициализация системы** — загрузка данных при старте приложения

## Архитектура

### Принцип работы

`QuoteScannerService` следует принципу **разделения ответственности (Separation of Concerns)**:

- **Не обрабатывает данные напрямую** — делегирует обработку `MarketDataProcessor`
- **Не управляет подписками** — делегирует управление `NotificationService`
- **Не хранит данные** — делегирует хранение `InstrumentCacheService`
- **Не определяет сессии** — делегирует определение `SessionTimeService`
- **Не управляет индексами напрямую** — делегирует управление `IndexBarManager`
- **Не кэширует цены** — использует `PriceCacheService` для получения цен
- **Не обновляет цены в реальном времени** — `MarketDataProcessor` синхронизирует оба кэша (`InstrumentCacheService` и `PriceCacheService`)

### Архитектурная диаграмма

```
┌─────────────────────────────────────────────────────────────┐
│                    QuoteScannerService                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Фасад (Facade Pattern)                  │  │
│  └──────────────────────────────────────────────────────┘  │
│                            │                                 │
│        ┌───────────────────┼───────────────────┐            │
│        │                   │                   │            │
│        ▼                   ▼                   ▼            │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐         │
│  │ MarketData│      │Notification│    │Instrument│         │
│  │ Processor │      │  Service  │    │  Cache   │         │
│  └──────────┘      └──────────┘    └──────────┘         │
│        │                   │                   │            │
│        └───────────────────┼───────────────────┘            │
│                            │                                 │
│        ┌───────────────────┼───────────────────┐            │
│        │                   │                   │            │
│        ▼                   ▼                   ▼            │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐         │
│  │IndexBar  │      │PriceCache│      │SessionTime│         │
│  │ Manager  │      │ Service  │      │ Service   │         │
│  └──────────┘      └──────────┘      └──────────┘         │
│        │                   │                   │            │
│        └───────────────────┼───────────────────┘            │
│                            │                                 │
│                            ▼                                 │
│                    ┌──────────┐                             │
│                    │  Clients │                             │
│                    └──────────┘                             │
└─────────────────────────────────────────────────────────────┘
```

## Основные компоненты

### 1. Зависимости

#### Основные сервисы

- **`QuoteScannerConfig`** — конфигурация сканера (режимы работы, лимиты)
- **`MarketDataProcessor`** — обработка рыночных данных (LastPrice, Trade, OrderBook)
- **`NotificationService`** — управление подписками и уведомлениями
- **`InstrumentCacheService`** — кэширование данных об инструментах
- **`SessionTimeService`** — определение торговых сессий
- **`InstrumentPairService`** — обработка пар инструментов
- **`MeterRegistry`** — метрики производительности

#### Вспомогательные сервисы

- **`ClosePriceService`** — загрузка цен закрытия основной сессии
- **`ClosePriceEveningSessionService`** — загрузка цен закрытия вечерней сессии
- **`ShareService`** — работа с акциями
- **`PriceCacheService`** — централизованный кэш всех цен (основная сессия, вечерняя сессия, last_price)
  - Загружает исторические цены из БД при инициализации
  - Обновляется в реальном времени через `MarketDataProcessor` при обработке `LastPrice` и `Trade` событий
  - Обеспечивает синхронизацию данных между `InstrumentCacheService` и REST API endpoints

### 2. Внутренние компоненты

#### Менеджер индексов (IndexBarManager)

```java
private final IndexBarManager indexBarManager = new IndexBarManager();
```

- Универсальный менеджер для управления строкой индексов
- Использует `CopyOnWriteArrayList` внутри для thread-safe операций
- Каждый экземпляр `QuoteScannerService` имеет свою копию менеджера (не является Spring-бином)
- Поддерживает добавление и удаление индексов во время работы
- Позволяет резолвить FIGI по тикеру через `InstrumentCacheService`

#### Планировщик задач

```java
private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
```

- Выполняет периодические задачи (очистка подписчиков, проверка сессий)
- Использует отдельный поток для предотвращения блокировки основного потока

#### Статистика

```java
private final AtomicLong totalQuotesProcessed = new AtomicLong(0);
private final AtomicLong totalQuotesSent = new AtomicLong(0);
```

- Счетчики обработанных котировок
- Используют `AtomicLong` для thread-safe операций

## Жизненный цикл сервиса

### 1. Инициализация (`@PostConstruct`)

При запуске приложения выполняется метод `init()`:

```java
@PostConstruct
public void init() {
    // 1. Логирование конфигурации
    // 2. Получение списка инструментов для сканирования
    // 3. Инициализация кэша инструментов
    // 4. Загрузка объемов выходного дня
    // 5. Загрузка всех цен из PriceCacheService
    // 6. Инициализация индексов по умолчанию
    // 7. Запуск периодических задач
}
```

#### Шаги инициализации

1. **Логирование конфигурации**
   - Максимальное количество котировок в секунду
   - Режим сохранения в БД
   - Режим WebSocket broadcast

2. **Получение инструментов**
   ```java
   List<String> instrumentsForScanning = instrumentCacheService.getInstrumentsForScanning();
   ```
   - Получает список FIGI всех инструментов для сканирования
   - Включает акции, фьючерсы, индикативы и динамические индексы

3. **Инициализация кэша**
   ```java
   instrumentCacheService.initializeCache();
   ```
   - Загружает имена и тикеры инструментов
   - Загружает флаги шорта и дивидендные события
   - Инициализирует кэши цен и объемов

4. **Загрузка объемов выходного дня**
   ```java
   instrumentCacheService.loadWeekendExchangeVolumes();
   ```
   - Загружает уже проторгованные объемы из `today_volume_view`
   - Сохраняет объемы для корректного расчета накопленных объемов

5. **Загрузка всех цен из PriceCacheService**
   ```java
   loadAllPricesFromCache();
   ```
   - Загружает все цены (основной сессии, вечерней сессии, last_price) из `PriceCacheService`
   - Выполняется **ДО** инициализации индексов, так как в строке индексов могут быть разные типы инструментов
   - `PriceCacheService` уже загружает все цены при инициализации через `@PostConstruct`
   - Загружает цены закрытия в `InstrumentCacheService` для обратной совместимости
   - Поддерживает все типы инструментов: акции, фьючерсы, индексы
   - **Примечание:** Загрузка средних объемов отключена (таблица `shares_aggregated_data` больше не существует)

6. **Инициализация индексов по умолчанию**
   ```java
   initializeDefaultIndices();
   ```
   - Добавляет индексы: IMOEX2, IMOEX, RTSI, XAG, XAU, XPD, XPT
   - Индексы можно добавлять и удалять динамически

7. **Запуск периодических задач**
   ```java
   scheduler.scheduleAtFixedRate(this::cleanupInactiveSubscribers, 30, 30, TimeUnit.SECONDS);
   scheduler.scheduleAtFixedRate(this::startScannerIfSessionTime, 0, 10, TimeUnit.SECONDS);
   ```
   - Очистка неактивных подписчиков каждые 30 секунд
   - Проверка времени сессии каждые 10 секунд

### 2. Обработка данных

Сервис получает данные от `MarketDataStreamingService` и делегирует их обработку:

#### Обработка LastPrice

```java
public void processLastPrice(LastPrice price) {
    marketDataProcessor.processLastPrice(price);
    totalQuotesProcessed.incrementAndGet();
}
```

**Поток обработки:**
1. `QuoteScannerService.processLastPrice()` — получает данные
2. `MarketDataProcessor.processLastPrice()` — обрабатывает данные
3. `InstrumentCacheService` — обновляет кэш цен
4. `PriceCacheService` — обновляет кэш последних цен в реальном времени (через `updateLastPrice()`)
5. `QuoteDataFactory` — создает `QuoteData` объект
6. `NotificationService` — уведомляет подписчиков
7. Счетчик обработанных котировок увеличивается

**Важно:** Обновление `PriceCacheService` синхронизирует данные между `InstrumentCacheService` и `PriceCacheService`, что позволяет API endpoints (например, `/api/scanner/weekend-scanner/indices/prices`) возвращать актуальные `lastPrice` в реальном времени.

#### Обработка Trade

```java
public void processTrade(Trade trade) {
    marketDataProcessor.processTrade(trade);
    totalQuotesProcessed.incrementAndGet();
}
```

**Поток обработки:**
1. `QuoteScannerService.processTrade()` — получает данные
2. `MarketDataProcessor.processTrade()` — обрабатывает данные
3. `InstrumentCacheService` — обновляет кэш цен и объемов
4. `PriceCacheService` — обновляет кэш последних цен в реальном времени (через `updateLastPrice()`)
5. Накопление объемов (только во время сессии выходного дня)
6. `QuoteDataFactory` — создает `QuoteData` объект
7. `NotificationService` — уведомляет подписчиков

**Важно:** Обновление `PriceCacheService` обеспечивает синхронизацию данных и доступность актуальных `lastPrice` через REST API endpoints.

#### Обработка OrderBook

```java
public void processOrderBook(OrderBook orderBook) {
    marketDataProcessor.processOrderBook(orderBook);
}
```

**Поток обработки:**
1. `QuoteScannerService.processOrderBook()` — получает данные
2. `MarketDataProcessor.processOrderBook()` — обрабатывает данные
3. `InstrumentCacheService` — обновляет кэш стакана заявок
4. `QuoteDataFactory` — создает `QuoteData` объект с обновленным стаканом
5. `NotificationService` — уведомляет подписчиков

### 3. Управление подписками

#### Подписка на котировки

```java
public void subscribeToQuotes(Consumer<QuoteData> subscriber) {
    notificationService.subscribe(subscriber);
}
```

- Делегирует подписку `NotificationService`
- Подписчики получают уведомления о всех обновлениях котировок
- Используется для WebSocket контроллеров

#### Отписка от котировок

```java
public void unsubscribeFromQuotes(Consumer<QuoteData> subscriber) {
    notificationService.unsubscribe(subscriber);
}
```

- Делегирует отписку `NotificationService`
- Автоматически вызывается при закрытии WebSocket соединения

#### Очистка неактивных подписчиков

```java
private void cleanupInactiveSubscribers() {
    int subscriberCount = notificationService.getSubscriberCount();
    if (subscriberCount > 0) {
        log.debug("Current active subscribers: {}", subscriberCount);
    } else {
        log.debug("No active subscribers");
    }
}
```

- Проверка активных подписчиков выполняется каждые 30 секунд
- Метод логирует текущее количество подписчиков (уровень DEBUG)
- Реальная очистка происходит автоматически в `QuoteWebSocketController`:
  - При закрытии соединения (`afterConnectionClosed`)
  - При ошибках отправки (`broadcastQuote` удаляет закрытые сессии)

#### Проверка количества активных подписчиков

Существует несколько способов проверить количество активных подписчиков:

**1. Через REST API endpoint:**

```bash
GET /api/scanner/stats
```

Ответ содержит информацию о подписчиках в объекте `notificationService`:

```json
{
  "notificationService": {
    "subscriberCount": 5,
    "notificationsSent": 75000,
    "notificationsFailed": 0,
    "hasSubscribers": true
  }
}
```

**2. Через упрощенный endpoint:**

```bash
GET /api/scanner/subscribers/count
```

Ответ:

```json
{
  "subscriberCount": 5,
  "hasSubscribers": true
}
```

**3. Программно в коде:**

```java
int count = notificationService.getSubscriberCount();
boolean hasSubscribers = notificationService.hasSubscribers();
```

**4. Через логи:**

Метод `cleanupInactiveSubscribers()` логирует количество подписчиков каждые 30 секунд на уровне DEBUG:

```
DEBUG - Current active subscribers: 5
```

**5. Через Micrometer метрики:**

Метрика `notifications.subscribers` содержит текущее количество подписчиков и доступна через Prometheus или другие системы мониторинга.

### 4. Завершение работы (`@PreDestroy`)

```java
@PreDestroy
public void shutdown() {
    scheduler.shutdown();
    // Логирование статистики
}
```

- Останавливает планировщик задач
- Логирует итоговую статистику обработанных котировок

## Управление индексами

### Архитектура управления индексами

Управление индексами делегировано классу `IndexBarManager`, который:
- Не является Spring-бином (каждый сервис создает свою копию)
- Использует `CopyOnWriteArrayList` для thread-safe операций
- Хранит конфигурацию индексов с FIGI, тикером и отображаемым именем

### Структура индекса

```java
public static class IndexConfig {
    public final String figi;        // FIGI инструмента
    public final String ticker;      // Тикер инструмента
    public final String displayName; // Отображаемое имя
}
```

Класс `IndexConfig` находится внутри `IndexBarManager`, а не в `QuoteScannerService`.

### Методы управления

#### Получение текущих индексов

```java
public List<Map<String, String>> getCurrentIndices() {
    return indexBarManager.getCurrentIndices();
}
```

**Формат возвращаемых данных:**
```json
[
  {
    "figi": "BBG004730N9",
    "name": "IMOEX",
    "displayName": "IMOEX"
  }
]
```

**Особенности:**
- Возвращает полную информацию об индексах (FIGI, тикер, отображаемое имя)
- Используется для отображения строки индексов в UI

#### Добавление индекса

Метод `addIndex` имеет две перегрузки:

**1. Добавление с указанием FIGI:**
```java
public boolean addIndex(String figi, String ticker, String displayName) {
    boolean added = indexBarManager.addIndex(figi, ticker, displayName);
    if (added) {
        notifySubscriptionUpdate();
    }
    return added;
}
```

**2. Добавление по тикеру (с автоматическим резолвингом FIGI):**
```java
public boolean addIndex(String name, String displayName) {
    boolean added = indexBarManager.addIndex(name, displayName, instrumentCacheService);
    if (added) {
        notifySubscriptionUpdate();
    }
    return added;
}
```

**Особенности:**
- Первый метод используется при известном FIGI
- Второй метод резолвит FIGI по тикеру через `InstrumentCacheService`
- Если FIGI не найден, используется тикер как FIGI
- Проверяет, не существует ли уже индекс с таким тикером
- Отправляет уведомление о необходимости обновить подписку при успешном добавлении
- Возвращает `true` при успехе, `false` если индекс уже существует

#### Удаление индекса

```java
public boolean removeIndex(String ticker) {
    boolean removed = indexBarManager.removeIndex(ticker);
    if (removed) {
        notifySubscriptionUpdate();
    }
    return removed;
}
```

**Особенности:**
- Удаляет индекс по тикеру
- Отправляет уведомление о необходимости обновить подписку при успешном удалении
- Возвращает `true` при успешном удалении, `false` если индекс не найден

### Индексы по умолчанию

При инициализации добавляются следующие индексы:

| FIGI | Тикер | Отображаемое имя |
|------|-------|------------------|
| BBG00KDWPPW3 | IMOEX2 | IMOEX2 |
| BBG004730N9 | IMOEX | IMOEX |
| BBG004730Z0 | RTSI | RTSI |
| BBG0013HGFT4 | XAG | XAG |
| BBG0013HJJ31 | XAU | XAU |
| BBG0013HGJ36 | XPD | XPD |
| BBG0013HGJ44 | XPT | XPT |

### Интеграция с подписками

Динамические индексы автоматически включаются в список инструментов для сканирования:

```java
public List<String> getInstrumentsForScanning() {
    List<String> baseInstruments = instrumentCacheService.getInstrumentsForScanning();
    
    // Получаем FIGI всех динамических индексов из IndexBarManager
    List<String> dynamicIndicesFigis = indexBarManager.getIndexFigis();
    
    List<String> allInstruments = new ArrayList<>();
    allInstruments.addAll(baseInstruments);
    allInstruments.addAll(dynamicIndicesFigis);
    
    return allInstruments;
}
```

**Особенности:**
- Базовые инструменты получаются из `InstrumentCacheService`
- Динамические индексы получаются из `IndexBarManager` через метод `getIndexFigis()`
- Метод логирует общее количество инструментов (базовых и динамических)

## Управление сессиями

### Определение сессий

Сервис использует `SessionTimeService` для определения торговых сессий:

#### Проверка утренней сессии

```java
public void startScannerIfSessionTime() {
    if (sessionTimeService.isMorningSessionTime()) {
        // Логирование активности сканера
    }
}
```

#### Проверка сессии выходного дня

```java
public void startScannerIfWeekendSessionTime() {
    if (sessionTimeService.isWeekendSessionTime()) {
        // Логирование активности сканера
    }
}
```

#### Проверка активности сканера

```java
public boolean isScannerActive() {
    return sessionTimeService.isAnySessionActive();
}
```

### Периодическая проверка

Планировщик проверяет время сессии каждые 10 секунд:

```java
scheduler.scheduleAtFixedRate(this::startScannerIfSessionTime, 0, 10, TimeUnit.SECONDS);
```

## API методы

### Обработка данных

#### `processLastPrice(LastPrice price)`
- Обрабатывает данные о последней цене
- Делегирует `MarketDataProcessor`
- Увеличивает счетчик обработанных котировок

#### `processTrade(Trade trade)`
- Обрабатывает данные о сделке
- Делегирует `MarketDataProcessor`
- Увеличивает счетчик обработанных котировок

#### `processOrderBook(OrderBook orderBook)`
- Обрабатывает данные стакана заявок
- Делегирует `MarketDataProcessor`

### Управление подписками

#### `subscribeToQuotes(Consumer<QuoteData> subscriber)`
- Подписывает на обновления котировок
- Делегирует `NotificationService`

#### `unsubscribeFromQuotes(Consumer<QuoteData> subscriber)`
- Отписывает от обновлений котировок
- Делегирует `NotificationService`

### Управление индексами

#### `getCurrentIndices()`
- Возвращает список текущих индексов
- Формат: `List<Map<String, String>>`

#### `addIndex(String figi, String ticker, String displayName)`
- Добавляет новый индекс с указанием FIGI
- Возвращает `true` при успехе, `false` если индекс уже существует

#### `addIndex(String name, String displayName)`
- Добавляет новый индекс по тикеру (с автоматическим резолвингом FIGI)
- Резолвит FIGI через `InstrumentCacheService`
- Возвращает `true` при успехе, `false` если индекс уже существует

#### `removeIndex(String ticker)`
- Удаляет индекс по тикеру
- Возвращает `true` при успехе, `false` при ошибке

### Получение данных

#### `getAvailableInstruments()`
- Возвращает все доступные инструменты с ценами
- Формат: `Map<String, BigDecimal>`

#### `getAvailableInstrumentNames()`
- Возвращает все доступные инструменты с именами
- Формат: `Map<String, String>`

#### `getCurrentPrices()`
- Возвращает текущие цены для REST API
- Формат: `Map<String, Object>`

#### `getInstruments()`
- Возвращает список отслеживаемых инструментов
- Формат: `Set<String>`

#### `getInstrumentsForScanning()`
- Возвращает список инструментов для сканирования
- Включает базовые инструменты и динамические индексы
- Формат: `List<String>`

### Статистика

#### `getStats()`
- Возвращает агрегированную статистику сервиса
- Включает:
  - Общее количество обработанных котировок
  - Статистику `MarketDataProcessor`
  - Статистику `NotificationService`
  - Статистику кэша
  - Информацию о сессии
  - Статус активности сканера

### Управление сессиями

#### `isScannerActive()`
- Проверяет, активен ли сканер
- Делегирует `SessionTimeService.isAnySessionActive()`

#### `checkWeekendSessionTime()`
- Проверяет, является ли текущее время сессией выходного дня
- Делегирует `SessionTimeService.checkWeekendSessionTime()`

#### `stopScanner()`
- Останавливает сканер принудительно
- В рефакторированной версии не используется (сессии управляются автоматически)

### Вспомогательные методы

#### `getShareService()`
- Возвращает `ShareService` для доступа к акциям

#### `getInstrumentCacheService()`
- Возвращает `InstrumentCacheService` для доступа к кэшу

#### `setInstrumentNames(Map<String, String> names)`
- Устанавливает имена инструментов для отображения

#### `loadAllPricesFromCache()`
- Приватный метод, вызываемый при инициализации
- Загружает все цены из `PriceCacheService` (основная сессия, вечерняя сессия, last_price)
- Загружает цены закрытия в `InstrumentCacheService` для обратной совместимости
- Поддерживает все типы инструментов: акции, фьючерсы, индексы
- Выполняется до инициализации индексов

## Интеграция с PriceCacheService

### Обновление цен в реальном времени

`MarketDataProcessor` обновляет `PriceCacheService` в реальном времени при обработке рыночных данных:

#### Обновление при обработке LastPrice

```java
private void processLastPriceInternal(LastPrice price) {
    // Обновляем InstrumentCacheService
    cacheService.setLastPrice(figi, currentPrice);
    // Обновляем PriceCacheService для доступа через REST API
    priceCacheService.updateLastPrice(figi, currentPrice);
}
```

#### Обновление при обработке Trade

```java
private void processTradeInternal(Trade trade) {
    // Обновляем InstrumentCacheService
    cacheService.setLastPrice(figi, currentPrice);
    // Обновляем PriceCacheService для доступа через REST API
    priceCacheService.updateLastPrice(figi, currentPrice);
}
```

### Метод updateLastPrice() в PriceCacheService

Метод `updateLastPrice()` обеспечивает обновление кэша последних цен в реальном времени:

```java
public void updateLastPrice(String figi, BigDecimal price) {
    if (figi != null && price != null) {
        lastPricesCache.put(figi, price);
        // Обновляем дату на сегодня, так как это актуальная цена в реальном времени
        lastPriceDate = LocalDate.now();
    }
}
```

**Особенности:**
- Обновляет кэш `lastPricesCache` при получении новых данных от T-Invest API
- Обновляет `lastPriceDate` на текущую дату для индикации актуальности данных
- Синхронизирует данные между `InstrumentCacheService` и `PriceCacheService`
- Обеспечивает доступность актуальных `lastPrice` через REST API endpoints

### Преимущества синхронизации

1. **Единый источник данных** — REST API endpoints возвращают те же данные, что и WebSocket
2. **Актуальность данных** — цены обновляются в реальном времени в обоих кэшах
3. **Обратная совместимость** — `InstrumentCacheService` продолжает работать как раньше
4. **Производительность** — оба кэша обновляются синхронно без дополнительных запросов к БД

## Конфигурация

### Параметры конфигурации

Сервис использует `QuoteScannerConfig` для настройки:

- **`maxQuotesPerSecond`** — максимальное количество котировок в секунду
- **`enableDatabaseSaving`** — включение сохранения в БД
- **`enableWebSocketBroadcast`** — включение WebSocket broadcast
- **`enableSharesMode`** — режим работы с акциями
- **`enableTestMode`** — тестовый режим (игнорирует сессии)

### Пример конфигурации

```properties
# Максимальное количество котировок в секунду
quote.scanner.max-quotes-per-second=1000

# Сохранение в БД
quote.scanner.enable-database-saving=true

# WebSocket broadcast
quote.scanner.enable-websocket-broadcast=true

# Режим работы с акциями
quote.scanner.enable-shares-mode=true

# Тестовый режим
quote.scanner.enable-test-mode=false
```

## Статистика и мониторинг

### Метрики

Сервис предоставляет следующие метрики:

1. **`totalQuotesProcessed`** — общее количество обработанных котировок
2. **`totalQuotesSent`** — общее количество отправленных котировок (не используется)
3. **Статистика компонентов** — агрегированная статистика всех компонентов

### Получение статистики

```java
Map<String, Object> stats = quoteScannerService.getStats();
```

**Формат ответа:**

```json
{
  "totalQuotesProcessed": 15000,
  "sharesMode": true,
  "trackedInstruments": 150,
  "marketDataProcessor": {
    "totalProcessed": 15000,
    "lastPriceProcessed": 8000,
    "tradeProcessed": 7000,
    "orderBookProcessed": 5000
  },
  "notificationService": {
    "subscriberCount": 5,
    "notificationsSent": 75000,
    "notificationsFailed": 0
  },
  "cacheStats": {
    "trackedInstruments": 150,
    "instrumentNames": 150,
    "instrumentTickers": 150
  },
  "sessionInfo": "MORNING_SESSION (утренняя сессия)",
  "isScannerActive": true
}
```

## Поток обработки данных

### Полный цикл обработки

```
1. MarketDataStreamingService получает данные от T-Invest API
   │
   ▼
2. QuoteScannerService.processLastPrice/Trade/OrderBook()
   │
   ▼
3. MarketDataProcessor.processLastPrice/Trade/OrderBook()
   │
   ├─► Обновление кэша (InstrumentCacheService)
   │
   ├─► Обновление кэша цен (PriceCacheService) ← Синхронизация для REST API
   │
   ├─► Создание QuoteData (QuoteDataFactory)
   │
   └─► Уведомление подписчиков (NotificationService)
       │
       ├─► WebSocket клиенты
       ├─► REST API клиенты
       └─► Другие подписчики
```

### Детальный поток обработки LastPrice

```
1. MarketDataStreamingService.onNext(MarketDataResponse)
   │
   ▼
2. QuoteScannerService.processLastPrice(LastPrice)
   │
   ▼
3. MarketDataProcessor.processLastPrice(LastPrice)
   │
   ├─► Инструмент в кэше? ──НЕТ──► Пропуск обработки
   │   │
   │   ДА
   │   │
   ├─► Дедупликация (проверка времени)
   │   │
   ├─► Обновление кэша цен
   │   │   InstrumentCacheService.setLastPrice(figi, price)
   │   │   PriceCacheService.updateLastPrice(figi, price)  ← Обновление в реальном времени
   │   │
   ├─► Создание QuoteData
   │   │   QuoteDataFactory.createFromLastPrice(price)
   │   │   ├─► Получение данных из кэша
   │   │   ├─► Расчет изменений
   │   │   └─► Создание объекта
   │   │
   └─► Уведомление подписчиков
       │   NotificationService.notifySubscribers(quoteData)
       │   ├─► Параллельная отправка
       │   ├─► Обработка ошибок
       │   └─► Обновление метрик
```

**Примечание:** Обновление `PriceCacheService` в реальном времени обеспечивает синхронизацию данных между `InstrumentCacheService` (используется для WebSocket) и `PriceCacheService` (используется для REST API endpoints). Это гарантирует, что REST API endpoints возвращают актуальные `lastPrice` без задержек.

## Обработка ошибок

### Стратегии обработки

1. **Логирование ошибок** — все ошибки логируются с уровнем ERROR
2. **Продолжение работы** — ошибки не останавливают обработку других данных
3. **Метрики ошибок** — ошибки отслеживаются через метрики
4. **Graceful degradation** — система продолжает работать при частичных сбоях

### Типичные ошибки

1. **Ошибки загрузки данных** — при инициализации (цены, объемы)
2. **Ошибки обработки данных** — при обработке котировок
3. **Ошибки уведомлений** — при отправке подписчикам
4. **Ошибки кэша** — при обновлении кэша

## Производительность

### Оптимизации

1. **Делегирование обработки** — разделение ответственности снижает нагрузку
2. **Асинхронная обработка** — неблокирующая обработка данных
3. **Кэширование** — быстрое получение данных из кэша
4. **Дедупликация** — предотвращение повторной обработки
5. **Параллельные уведомления** — параллельная отправка подписчикам
6. **Синхронизация кэшей** — одновременное обновление `InstrumentCacheService` и `PriceCacheService` без дополнительных запросов к БД

### Метрики производительности

- **Скорость обработки** — количество котировок в секунду
- **Задержка обработки** — время от получения до уведомления
- **Использование памяти** — размер кэшей
- **Нагрузка на CPU** — использование потоков

## Использование

### Пример использования в контроллере

```java
@RestController
@RequestMapping("/api/scanner")
public class ScannerController {
    
    private final QuoteScannerService quoteScannerService;
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(quoteScannerService.getStats());
    }
    
    @PostMapping("/indices/add")
    public ResponseEntity<String> addIndex(
            @RequestParam(required = false) String figi,
            @RequestParam String name,
            @RequestParam String displayName) {
        boolean added;
        if (figi != null) {
            // Используем перегрузку с FIGI
            added = quoteScannerService.addIndex(figi, name, displayName);
        } else {
            // Используем перегрузку с автоматическим резолвингом FIGI
            added = quoteScannerService.addIndex(name, displayName);
        }
        return added 
            ? ResponseEntity.ok("Index added")
            : ResponseEntity.badRequest().body("Index already exists");
    }
}
```

### Пример использования в WebSocket контроллере

```java
@Controller
public class QuoteWebSocketController {
    
    private final QuoteScannerService quoteScannerService;
    
    @OnOpen
    public void onOpen(Session session) {
        Consumer<QuoteData> subscriber = quoteData -> {
            try {
                session.getBasicRemote().sendText(
                    objectMapper.writeValueAsString(quoteData)
                );
            } catch (Exception e) {
                log.error("Error sending quote data", e);
            }
        };
        quoteScannerService.subscribeToQuotes(subscriber);
    }
    
    @OnClose
    public void onClose(Session session) {
        // Отписка обрабатывается автоматически
    }
}
```

## Расширение функциональности

### Добавление новых типов данных

1. Добавить метод обработки в `QuoteScannerService`
2. Реализовать обработку в `MarketDataProcessor`
3. Добавить создание DTO в `QuoteDataFactory`
4. Обновить подписчиков через `NotificationService`

### Добавление новых метрик

1. Добавить счетчики в сервис
2. Обновить метод `getStats()`
3. Интегрировать с `MeterRegistry`

### Управление индексами

Для работы с индексами используется `IndexBarManager`:
- Добавление индексов через `addIndex()` (с FIGI или по тикеру)
- Удаление индексов через `removeIndex()`
- Получение списка через `getCurrentIndices()`
- Автоматическое включение в список инструментов для сканирования

## Заключение

`QuoteScannerService` — центральный компонент системы сканирования котировок, который обеспечивает:

- **Высокую производительность** — через делегирование и асинхронную обработку
- **Масштабируемость** — через разделение ответственности
- **Поддерживаемость** — через четкую архитектуру
- **Надежность** — через обработку ошибок и graceful degradation

Сервис следует принципам SOLID и обеспечивает чистую архитектуру приложения.


