# 📊 Документация по утреннему сканеру

## 🎯 Обзор

Утренний сканер - это система для мониторинга и анализа рыночных данных в режиме реального времени в период утренней торговой сессии (06:50:00 - 09:49:59 MSK). Сканер автоматически загружает полный список акций из базы данных и отображает их в двух категориях: растущие и падающие относительно основной сессии.

## 🏗️ Архитектура системы

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────────┐
│                    УТРЕННИЙ СКАНЕР                              │
├─────────────────────────────────────────────────────────────────┤
│  Frontend (morning-session-scanner.html)                       │
│  ├── WebSocket подключение к /ws/quotes                        │
│  ├── Двухсекционный интерфейс (растущие/падающие)              │
│  └── API вызовы к /api/morning-scanner/*                       │
├─────────────────────────────────────────────────────────────────┤
│  Backend Services                                               │
│  ├── MorningScannerController                                  │
│  ├── QuoteScannerService (режим shares)                        │
│  ├── ShareService (загрузка из invest.shares)                  │
│  └── MarketDataStreamingService (T-Bank API)                   │
├─────────────────────────────────────────────────────────────────┤
│  Data Sources                                                   │
│  ├── invest.shares (все акции)                                 │
│  ├── invest.close_prices_evening_session (цены ВС)             │
│  └── T-Bank API (реальные котировки)                           │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 Последовательность действий при загрузке

### 1. Инициализация приложения

#### 1.1 Загрузка конфигурации

```properties
# application.properties
quote-scanner.enable-shares-mode=true          # Режим загрузки из БД
quote-scanner.enable-test-mode=false           # Тестовый режим
quote-scanner.enable-immediate-order-book-updates=true
```

#### 1.2 Создание Spring Beans

```java
@PostConstruct
public void init() {
    // 1. Инициализация QuoteScannerService
    // 2. Загрузка ShareService
    // 3. Настройка WebSocket
    // 4. Подключение к T-Bank API
}
```

### 2. Загрузка данных из базы данных

#### 2.1 ShareService.getAllShares()

```java
// Загрузка всех акций из таблицы invest.shares:
1. shareRepository.findAllShares()              // ORDER BY ticker
2. Логирование результатов
3. Кэширование в памяти
```

#### 2.2 Загрузка тикеров и имен

```java
// ShareService.getShareTickers() - из всех акций
Map<String, String> tickers = getAllShares().stream()
    .collect(Collectors.toMap(
        ShareEntity::getFigi,
        ShareEntity::getTicker,
        (existing, replacement) -> existing
    ));

// ShareService.getShareNames() - из всех акций
Map<String, String> names = getAllShares().stream()
    .collect(Collectors.toMap(
        ShareEntity::getFigi,
        share -> share.getName() != null ? share.getName() : share.getTicker(),
        (existing, replacement) -> existing
    ));
```

#### 2.3 Кэширование данных

```java
// QuoteScannerService.init()
instrumentNames.putAll(getInstrumentNamesForScanning());
instrumentTickers.putAll(getInstrumentTickersForScanning());
// Логирование: "Loaded X tickers into cache"
```

### 3. Подключение к T-Bank API

#### 3.1 Инициализация gRPC соединения

```java
// MarketDataStreamingService
1. Создание InvestApiClient
2. Получение списка инструментов для подписки
3. Настройка обработчиков данных
```

#### 3.2 Подписка на потоки данных

```java
// Подписки на:
- LastPrice (цены)
- Trade (сделки)
- OrderBook (стаканы)
```

### 4. Обработка рыночных данных

#### 4.1 Фильтрация по инструментам

```java
// QuoteScannerService.processLastPrice()
List<String> instrumentsToScan = getInstrumentsForScanning();
if (!instrumentsToScan.contains(figi)) {
    return; // Пропускаем инструмент
}
```

#### 4.2 Создание QuoteData

```java
// Получение данных для инструмента
String ticker = instrumentTickers.getOrDefault(figi, figi);
String instrumentName = instrumentNames.getOrDefault(figi, figi);
BigDecimal closePriceVS = closePriceEveningSessionService.getEveningClosePrice(figi);

// Создание объекта данных
QuoteData quoteData = new QuoteData(
    figi, ticker, instrumentName,
    currentPrice, previousPrice, closePrice,
    openPrice, closePriceOS, closePriceVS,
    bestBid, bestAsk, bestBidQuantity, bestAskQuantity,
    timestamp, volume, totalVolume, direction
);
```

#### 4.3 Отправка через WebSocket

```java
// Уведомление всех подписчиков
notifySubscribers(quoteData);
```

### 5. Frontend обработка

#### 5.1 WebSocket подключение

```javascript
// morning-session-scanner.html
websocket = new WebSocket("ws://localhost:8085/ws/quotes");
websocket.onmessage = function (event) {
  const quoteData = JSON.parse(event.data);
  updateQuote(quoteData);
};
```

#### 5.2 Обновление интерфейса

```javascript
// Сортировка инструментов
const gainers = quotes
  .filter((q) => q.closePriceOSChangePercent > 0)
  .sort((a, b) => b.closePriceOSChangePercent - a.closePriceOSChangePercent);

const losers = quotes
  .filter((q) => q.closePriceOSChangePercent < 0)
  .sort((a, b) => a.closePriceOSChangePercent - b.closePriceOSChangePercent);

// Обновление таблиц
updateGainersTable(gainers);
updateLosersTable(losers);
```

## 📋 Структура данных

### Изменения в структуре таблицы invest.shares

**ВАЖНО:** В новой версии системы удалена концепция "активных акций". Теперь система загружает **все акции** из таблицы `invest.shares` без фильтрации.

#### Удаленные поля:

- `is_active` - поле активности акции
- `sector` - поле сектора

#### Обновленная структура:

```sql
CREATE TABLE invest.shares (
    figi VARCHAR(50) NOT NULL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    exchange VARCHAR(50) NOT NULL
);
```

#### Удаленные методы:

- `ShareRepository.findActiveShares()`
- `ShareRepository.findActiveSharesByExchange()`
- `ShareRepository.findActiveSharesBySector()`
- `ShareRepository.countActiveShares()`
- `ShareService.getActiveShares()`
- `ShareService.getSharesByExchange()`
- `ShareService.getSharesBySector()`
- `ShareService.getActiveSharesCount()`

#### Новые методы:

- `ShareService.getAllShares()` - загрузка всех акций
- `ShareService.getAllShareFigis()` - получение FIGI всех акций
- `ShareService.getAllSharesCount()` - подсчет всех акций

### QuoteData (DTO)

```java
public class QuoteData {
    private final String figi;                    // FIGI инструмента
    private final String ticker;                  // Тикер (SBER, GAZP)
    private final String instrumentName;          // Полное название
    private final BigDecimal currentPrice;        // Текущая цена
    private final BigDecimal previousPrice;       // Предыдущая цена
    private final BigDecimal closePrice;          // Цена закрытия
    private final BigDecimal openPrice;           // Цена открытия
    private final BigDecimal closePriceOS;        // Цена ОС
    private final BigDecimal closePriceVS;        // Цена ВС
    private final BigDecimal closePriceOSChangePercent; // Изменение от ОС %
    private final BigDecimal bestBid;             // Лучший BID
    private final BigDecimal bestAsk;             // Лучший ASK
    private final long bestBidQuantity;           // Количество лотов BID
    private final long bestAskQuantity;           // Количество лотов ASK
    private final long totalVolume;               // Накопленный объем
    private final LocalDateTime timestamp;        // Время обновления
    private final String direction;               // Направление сделки
}
```

### ShareEntity (База данных)

```java
@Entity
@Table(name = "shares", schema = "invest")
public class ShareEntity {
    @Id
    @Column(name = "figi", nullable = false, length = 50)
    private String figi;           // FIGI инструмента (PK)

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;         // Тикер (SBER, GAZP) - UNIQUE

    @Column(name = "name", nullable = false, length = 100)
    private String name;           // Полное название

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;       // Валюта

    @Column(name = "exchange", nullable = false, length = 50)
    private String exchange;       // Биржа
}
```

## 🔧 Конфигурация

### Основные настройки

```properties
# Режим работы
quote-scanner.enable-shares-mode=true           # Загрузка из БД
quote-scanner.enable-test-mode=false            # Тестовый режим

# Производительность
quote-scanner.enable-immediate-order-book-updates=true
quote-scanner.max-order-book-updates-per-second=0

# База данных
spring.datasource.url=jdbc:postgresql://localhost:5432/invest
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Временные рамки

```java
// Константы времени сессии
MORNING_SESSION_START_HOUR = 6
MORNING_SESSION_START_MINUTE = 50
MORNING_SESSION_END_HOUR = 9
MORNING_SESSION_END_MINUTE = 49
MORNING_SESSION_END_SECOND = 59
```

## 📊 Интерфейс пользователя

### Структура интерфейса

```
┌─────────────────────────────────────────────────────────────────┐
│  📈 СТАТИСТИКА                                                   │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐      │
│  │ АКТИВНЫХ    │ ОБЪЕМ       │ СКОРОСТЬ    │ ПОСЛЕДНЕЕ   │      │
│  │ ИНСТРУМЕНТОВ│ ТОРГОВ      │ ОБНОВЛЕНИЯ  │ ОБНОВЛЕНИЕ  │      │
│  └─────────────┴─────────────┴─────────────┴─────────────┘      │
├─────────────────────────────────────────────────────────────────┤
│  🎛️ УПРАВЛЕНИЕ                                                  │
│  [Подключиться] [Отключиться] [← На главную] [Подключено]       │
├─────────────────────────────────────────────────────────────────┤
│  📈 РАСТУЩИЕ ОТНОСИТЕЛЬНО ОС                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ ИНСТРУМЕНТ │ ТЕКУЩАЯ │ ЦЕНА ОТКР │ ЦЕНА ОС │ ЦЕНА ВС │ ... │ │
│  └─────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│  📉 ПАДАЮЩИЕ ОТНОСИТЕЛЬНО ОС                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ ИНСТРУМЕНТ │ ТЕКУЩАЯ │ ЦЕНА ОТКР │ ЦЕНА ОС │ ЦЕНА ВС │ ... │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Колонки таблицы

1. **ИНСТРУМЕНТ** - Тикер (SBER, GAZP, LKOH)
2. **ТЕКУЩАЯ** - Текущая цена
3. **ЦЕНА ОТКРЫТИЯ** - Цена открытия с % изменения
4. **ЦЕНА ОС** - Цена основной сессии с % изменения
5. **ЦЕНА ВС** - Цена вечерней сессии с % изменения
6. **ИЗМЕНЕНИЕ ОТ ОС %** - Процент изменения от ОС (жирный)
7. **BID (ЛОТЫ)** - Лучший BID и количество лотов
8. **ASK (ЛОТЫ)** - Лучший ASK и количество лотов
9. **ОБЪЕМ** - Накопленный объем за сессию
10. **ВРЕМЯ** - Время последнего обновления

## 🎯 Точность отображения цен

### Адаптивное форматирование

Система использует адаптивное форматирование для отображения цен и процентов с максимальной точностью:

#### JavaScript функции форматирования

```javascript
// formatPrice() - отображение цен
function formatPrice(price) {
  if (!price) return "0";
  const num = parseFloat(price);

  // Для очень маленьких чисел (< 0.01) показываем до 6 знаков
  if (num < 0.01 && num > 0) {
    return num.toFixed(6);
  }

  // Для обычных чисел показываем до 4 знаков
  return num.toFixed(4);
}

// formatPercent() - отображение процентов
function formatPercent(percent) {
  if (!percent) return "0%";
  const num = parseFloat(percent);

  // Для очень маленьких изменений (< 0.01%) показываем до 4 знаков
  if (Math.abs(num) < 0.01 && num !== 0) {
    return num.toFixed(4) + "%";
  }

  // Для обычных изменений показываем до 2 знаков
  return num.toFixed(2) + "%";
}
```

#### Java обработка цен

```java
// QuoteScannerService.sendOrderBookUpdate()
// При расчете средней цены между BID и ASK используется 9 знаков после запятой
currentPrice = bestBid.add(bestAsk).divide(BigDecimal.valueOf(2), 9,
    java.math.RoundingMode.HALF_UP);

// Все BigDecimal операции выполняются без округления до передачи в WebSocket
BigDecimal currentPrice = BigDecimal.valueOf(price.getPrice().getUnits())
    .add(BigDecimal.valueOf(price.getPrice().getNano()).movePointLeft(9));
```

### Примеры точного отображения

| Тикер | Реальная цена | Отображается как | Примечание          |
| ----- | ------------- | ---------------- | ------------------- |
| TGKN  | 0.006360      | 0.006360         | 6 знаков для < 0.01 |
| SBER  | 123.4567      | 123.4567         | 4 знака для ≥ 0.01  |
| GAZP  | 0.000123      | 0.000123         | 6 знаков для < 0.01 |
| LKOH  | 5678.90       | 5678.9000        | 4 знака для ≥ 0.01  |

### Процентные изменения

| Изменение | Отображается как | Примечание          |
| --------- | ---------------- | ------------------- |
| 0.0001%   | 0.0001%          | 4 знака для < 0.01% |
| 0.5%      | 0.50%            | 2 знака для ≥ 0.01% |
| 15.67%    | 15.67%           | 2 знака для ≥ 0.01% |

## 🚀 Запуск и тестирование

### 1. Подготовка базы данных

```sql
-- Создание таблицы акций
CREATE TABLE invest.shares (
    figi VARCHAR(50) NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    exchange VARCHAR(50) NOT NULL,
    CONSTRAINT stocks_pkey PRIMARY KEY (figi),
    CONSTRAINT unique_ticker UNIQUE (ticker)
);

-- Вставка тестовых данных
INSERT INTO invest.shares (figi, ticker, name, currency, exchange) VALUES
('BBG004730N88', 'SBER', 'Сбербанк', 'RUB', 'MOEX'),
('BBG0047315Y7', 'GAZP', 'Газпром', 'RUB', 'MOEX'),
('BBG000MZLOY6', 'LKOH', 'Лукойл', 'RUB', 'MOEX'),
-- ... другие акции
```

### 2. Запуск приложения

```bash
# Компиляция
mvn compile

# Запуск
mvn spring-boot:run

# Или через IDE
# Запустить InvestmentDataScannerService.main()
```

### 3. Доступ к интерфейсу

```
URL: http://localhost:8085/morning-session-scanner.html
```

### 4. Проверка работы

1. **Проверить логи** на предмет загрузки акций
2. **Проверить WebSocket** подключение
3. **Проверить отображение** тикеров вместо FIGI
4. **Проверить количество** загруженных инструментов

## 🔍 Диагностика проблем

### Логи для проверки

```
# Загрузка акций
Loaded X shares from database
Loaded X tickers from database
First 5 tickers: [FIGI1=TICKER1, FIGI2=TICKER2, ...]

# Обработка данных
Creating QuoteData for FIGI: BBG000MZLOY6, ticker: SBER, name: Сбербанк
Processing LastPrice for instrument BBG000MZLOY6

# WebSocket
WebSocket connection established
Quote data sent to subscribers
```

### Частые проблемы

#### 1. Не загружаются акции

```sql
-- Проверить данные в БД
SELECT COUNT(*) FROM invest.shares;
SELECT figi, ticker, name FROM invest.shares LIMIT 5;
```

#### 2. Показываются FIGI вместо тикеров

- Проверить логи: "Creating QuoteData for FIGI: X, ticker: Y"
- Проверить загрузку тикеров: "Loaded X tickers into cache"

#### 3. Мало загруженных инструментов

- Проверить настройку: `quote-scanner.enable-shares-mode=true`
- Проверить данные в таблице `invest.shares`
- Проверить логи: "Loaded X shares from database"

#### 4. Сканер не работает вне времени сессии

- Включить тестовый режим: `quote-scanner.enable-test-mode=true`

#### 5. Цены отображаются с округлением (например, 0.01 вместо 0.006360)

- **Проблема:** В методе `sendOrderBookUpdate()` было округление до 2 знаков после запятой
- **Решение:** Обновлено до 9 знаков после запятой в `QuoteScannerService.java`
- **Проверка:** Убедиться, что тикер TGKN отображает точную цену `0.006360`

## 📈 Производительность

### Оптимизации

- **Кэширование** тикеров и имен в памяти
- **Синхронная обработка** стаканов для минимальной задержки
- **Потоковая обработка** цен и сделок
- **WebSocket** для реального времени

### Мониторинг

- **Счетчики** обработанных котировок
- **Скорость обновления** (котировок/сек)
- **Количество загруженных** инструментов
- **Время последнего** обновления

## 🔒 Безопасность

### Настройки

- **Токен T-Bank API** в переменных окружения
- **Пароли БД** в конфигурации
- **WebSocket** только для локального доступа

### Рекомендации

- Использовать HTTPS в продакшене
- Ограничить доступ к API
- Мониторить логи на предмет ошибок

---

## 📞 Поддержка

При возникновении проблем:

1. Проверить логи приложения
2. Проверить подключение к БД
3. Проверить настройки T-Bank API
4. Обратиться к разработчику

**Версия документации:** 2.1  
**Дата обновления:** 2024  
**Автор:** Investment Data Scanner Service

### История изменений

#### Версия 2.1 (2024)

- **Исправлено округление цен** - теперь все цены отображаются с максимальной точностью
- **Улучшена точность вычислений** - при расчете средней цены между BID и ASK используется 9 знаков после запятой
- **Обновлена функция форматирования** - JavaScript функции `formatPrice()` и `formatPercent()` адаптивно показывают все значимые цифры
- **Исправлена проблема с TGKN** - тикер теперь отображает точную цену `0.006360` вместо округленной `0.01`

#### Версия 2.0 (2024)

- Удалена концепция "активных акций"
- Обновлена структура таблицы `invest.shares`
- Удалены поля `is_active` и `sector`
- Упрощены методы загрузки данных
- Система теперь загружает все акции из базы данных
