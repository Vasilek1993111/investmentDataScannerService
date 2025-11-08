# QuoteScannerService - Детальная документация

## Обзор

`QuoteScannerService` — центральный сервис системы сканирования котировок, который координирует получение, обработку и распространение рыночных данных в реальном времени. Сервис реализует паттерн "Facade" и делегирует специализированную обработку другим сервисам для обеспечения высокой производительности и поддерживаемости.

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

### 2. Внутренние компоненты

#### Динамические индексы

```java
private final List<IndexConfig> dynamicIndices = new CopyOnWriteArrayList<>();
```

- Хранит список индексов для отслеживания
- Использует `CopyOnWriteArrayList` для thread-safe операций
- Поддерживает добавление и удаление индексов во время работы

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
    // 5. Загрузка цен закрытия
    // 6. Загрузка цен вечерней сессии
    // 7. Инициализация индексов по умолчанию
    // 8. Запуск периодических задач
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

5. **Загрузка цен закрытия**
   ```java
   loadClosePrices();
   ```
   - Загружает цены закрытия за предыдущий торговый день
   - Использует `ClosePriceService` для получения данных из БД

6. **Загрузка цен вечерней сессии**
   ```java
   loadEveningClosePrices();
   ```
   - Загружает цены закрытия вечерней сессии за предыдущий торговый день
   - Использует `ClosePriceEveningSessionService`

7. **Инициализация индексов по умолчанию**
   ```java
   initializeDefaultIndices();
   ```
   - Добавляет индексы: IMOEX2, IMOEX, RTSI, XAG, XAU, XPD, XPT
   - Индексы можно добавлять и удалять динамически

8. **Запуск периодических задач**
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
4. `QuoteDataFactory` — создает `QuoteData` объект
5. `NotificationService` — уведомляет подписчиков
6. Счетчик обработанных котировок увеличивается

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
4. Накопление объемов (только во время сессии выходного дня)
5. `QuoteDataFactory` — создает `QuoteData` объект
6. `NotificationService` — уведомляет подписчиков

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

### Структура индекса

```java
public static class IndexConfig {
    public final String figi;        // FIGI инструмента
    public final String ticker;      // Тикер инструмента
    public final String displayName; // Отображаемое имя
}
```

### Методы управления

#### Получение текущих индексов

```java
public List<Map<String, String>> getCurrentIndices() {
    return dynamicIndices.stream()
        .map(config -> {
            Map<String, String> index = new HashMap<>();
            index.put("name", config.ticker);
            index.put("displayName", config.displayName);
            return index;
        })
        .collect(Collectors.toList());
}
```

#### Добавление индекса

```java
public boolean addIndex(String name, String displayName) {
    // Проверка на дубликаты
    // Добавление нового индекса
    // Уведомление о необходимости обновить подписку
}
```

**Особенности:**
- Проверяет, не существует ли уже индекс с таким тикером
- Использует `name` как FIGI для совместимости
- Отправляет уведомление о необходимости обновить подписку

#### Удаление индекса

```java
public boolean removeIndex(String ticker) {
    // Удаление индекса по тикеру
    // Уведомление о необходимости обновить подписку
}
```

**Особенности:**
- Удаляет индекс по тикеру
- Отправляет уведомление о необходимости обновить подписку
- Возвращает `true` при успешном удалении

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
    List<String> dynamicIndicesFigis = dynamicIndices.stream()
        .map(config -> config.figi)
        .collect(Collectors.toList());
    
    List<String> allInstruments = new ArrayList<>();
    allInstruments.addAll(baseInstruments);
    allInstruments.addAll(dynamicIndicesFigis);
    
    return allInstruments;
}
```

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

#### `addIndex(String name, String displayName)`
- Добавляет новый индекс
- Возвращает `true` при успехе, `false` при ошибке

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
            @RequestParam String name,
            @RequestParam String displayName) {
        boolean added = quoteScannerService.addIndex(name, displayName);
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

## Заключение

`QuoteScannerService` — центральный компонент системы сканирования котировок, который обеспечивает:

- **Высокую производительность** — через делегирование и асинхронную обработку
- **Масштабируемость** — через разделение ответственности
- **Поддерживаемость** — через четкую архитектуру
- **Надежность** — через обработку ошибок и graceful degradation

Сервис следует принципам SOLID и обеспечивает чистую архитектуру приложения.

