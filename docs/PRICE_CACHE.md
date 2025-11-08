# Price Cache System

Система кэширования цен закрытия, открытия, вечерней сессии и последних цен сделок (last_price) для быстрого доступа к историческим данным.

## Обзор

Система кэширования цен обеспечивает:

- Автоматическую загрузку **только последних цен** при запуске приложения (`@PostConstruct`)
- In-memory кэширование для быстрого доступа (ConcurrentHashMap)
- REST API для управления кэшем и получения данных
- Автоматическое обновление по расписанию (каждый день в 6:00 MSK)
- Унифицированную логику обработки выходных дней
- Поддержку цен закрытия, открытия, вечерней сессии и последних цен сделок (last_price)
- Поддержку объемов торгов (исторических и текущих)
- Оптимизированное потребление памяти (только последние цены)

## Компоненты

### 1. PriceCacheService

Основной сервис для работы с кэшем цен. Автоматически инициализируется при запуске приложения через `@PostConstruct`.

**Основные методы:**

**Получение цен:**
- `getLastClosePrice(String figi)` - получение последней цены закрытия
- `getLastEveningSessionPrice(String figi)` - получение последней цены вечерней сессии
- `getLastOpenPrice(String figi)` - получение последней цены открытия
- `getLastPrice(String figi)` - получение последней цены сделки (last_price)
- `getPricesForFigi(String figi)` - получение всех цен для инструмента (закрытие, открытие, вечерняя сессия, last_price)
- `getAllClosePrices()` - получение всех цен закрытия из кэша
- `getAllEveningSessionPrices()` - получение всех цен вечерней сессии из кэша
- `getAllOpenPrices()` - получение всех цен открытия из кэша
- `getAllLastPrices()` - получение всех последних цен сделок (last_price) из кэша

**Управление кэшем:**
- `loadAllClosePrices()` - загрузка последних цен закрытия
- `loadAllEveningSessionPrices()` - загрузка последних цен вечерней сессии
- `loadAllOpenPrices()` - загрузка последних цен открытия
- `loadAllLastPrices()` - загрузка последних цен сделок (last_price)
- `reloadCache()` - перезагрузка кэша (включая last_price)
- `clearCache()` - очистка кэша (включая last_price)
- `forceReloadAllPricesCache()` - принудительная перезагрузка всех типов цен (включая last_price)
- `forceReloadClosePricesCache()` - принудительная перезагрузка только цен закрытия
- `forceReloadLastPricesCache()` - принудительная перезагрузка только последних цен сделок (last_price)

**Метаданные:**
- `getLastClosePriceDate()` - получение последней даты цен закрытия
- `getLastEveningSessionPriceDate()` - получение последней даты цен вечерней сессии
- `getLastOpenPriceDate()` - получение последней даты цен открытия
- `getLastPriceDate()` - получение последней даты последних цен сделок (last_price)
- `getCacheStats()` - получение статистики кэша

**Особенности:**
- Унифицированная логика определения последней торговой даты с учетом выходных дней
- В выходные дни (суббота/воскресенье) используются цены за последнюю пятницу
- В рабочие дни используются цены за сегодня, если данные доступны, иначе за последний рабочий день с данными

### 2. StartupPriceLoader

Сервис для загрузки цен при запуске приложения.

**Функции:**

- Асинхронная загрузка цен при старте (`@Async`)
- Параллельная загрузка всех типов цен (закрытие, открытие, вечерняя сессия, last_price)
- Принудительная перезагрузка через `reloadAllPrices()` (включая last_price)

**Методы:**
- `loadPricesOnStartup()` - загрузка цен при запуске (вызывается автоматически)
- `reloadAllPrices()` - асинхронная перезагрузка всех цен

### 3. ScheduledPriceUpdateService

Сервис для автоматического обновления кэша цен по расписанию.

**Функции:**

- Автоматическое обновление всех типов цен каждый день в 6:00 MSK
- Проверка здоровья кэша каждый час
- Унифицированная логика обработки выходных дней

**Расписание:**
- Обновление цен: `0 0 6 * * ?` (каждый день в 6:00:00 по московскому времени)
- Проверка здоровья: `0 0 * * * ?` (каждый час в 0 минут)

### 4. PriceCacheController

REST API контроллер для управления кэшем и получения данных.

**Endpoints для цен:**

**Статистика и информация:**
- `GET /api/price-cache/stats` - статистика кэша (количество цен, даты)
- `GET /api/price-cache/scheduler-info` - информация о планировщике обновлений

**Получение всех цен:**
- `GET /api/price-cache/close-price` - все цены закрытия из кэша
- `GET /api/price-cache/open-price` - все цены открытия из кэша
- `GET /api/price-cache/evening-session-price` - все цены вечерней сессии из кэша
- `GET /api/price-cache/last-price` - все последние цены сделок (last_price) из кэша

**Получение цены для конкретного инструмента:**
- `GET /api/price-cache/last-close-price?figi={figi}` - последняя цена закрытия
- `GET /api/price-cache/prices/{figi}` - все цены для инструмента (FIGI или тикер)
  - Поддерживает поиск по FIGI или тикеру
  - Возвращает цены закрытия, открытия, вечерней сессии и последнюю цену сделки (last_price)

**Управление кэшем:**
- `POST /api/price-cache/clear` - очистка кэша (включая last_price)
- `POST /api/price-cache/reload` - асинхронная перезагрузка всех цен (включая last_price)
- `POST /api/price-cache/force-reload-all` - принудительная перезагрузка всех типов цен (включая last_price)
- `POST /api/price-cache/reload-close-prices` - перезагрузка только цен закрытия
- `POST /api/price-cache/reload-last-prices` - перезагрузка только последних цен сделок (last_price)
- `POST /api/price-cache/reload-instruments` - перезагрузка инструментов (без цен)

**Endpoints для объемов:**

- `GET /api/price-cache/volumes` - все данные объемов (исторические и текущие)
- `GET /api/price-cache/volumes/{figi}` - данные объемов для конкретного инструмента
- `POST /api/price-cache/load-weekend-volumes` - загрузка объемов выходного дня
- `POST /api/price-cache/reload-volumes` - перезагрузка данных объемов

### 5. CacheConfig

Конфигурация Spring Cache для приложения (хотя `PriceCacheService` использует собственные ConcurrentHashMap, Spring Cache доступен для других компонентов).

**Настройки:**
- `ConcurrentMapCacheManager` для in-memory кэша
- Названия кэшей: `closePrices`, `eveningSessionPrices`, `lastClosePrices`, `lastEveningSessionPrices`
- Включено асинхронное выполнение (`@EnableAsync`)

**Примечание:** `PriceCacheService` использует собственные `ConcurrentHashMap` для кэширования, а не Spring Cache аннотации, что обеспечивает более прямой контроль над данными.

### 6. Логика определения последней торговой даты

Система использует унифицированную логику определения последней торговой даты:

**Выходные дни (суббота/воскресенье):**
- Используются цены за последнюю пятницу

**Рабочие дни:**
- Если есть данные за сегодня → используются цены за сегодня
- Если данных за сегодня нет → используются цены за последний рабочий день с данными из базы

**Преимущества:**
- Автоматическая обработка выходных дней
- Использование актуальных данных в рабочие дни
- Отказоустойчивость при отсутствии данных за текущий день

## Конфигурация

### Автоматическая инициализация

Кэш автоматически инициализируется при запуске приложения через `@PostConstruct` в `PriceCacheService`:
- Загружаются последние цены закрытия
- Загружаются последние цены вечерней сессии
- Загружаются последние цены открытия
- Загружаются последние цены сделок (last_price)

### Автоматическое обновление

`ScheduledPriceUpdateService` автоматически обновляет кэш:
- Каждый день в 6:00 MSK - обновление всех типов цен
- Каждый час - проверка здоровья кэша

### Timezone

Все операции выполняются в часовом поясе `Europe/Moscow` (MSK).

## Использование

### 1. Автоматическая загрузка при запуске

При запуске приложения автоматически загружаются последние цены из базы данных в кэш через `@PostConstruct`.

### 2. Программное использование

```java
@Autowired
private PriceCacheService priceCacheService;

// Получение последней цены закрытия
BigDecimal closePrice = priceCacheService.getLastClosePrice("BBG004730N88");

// Получение последней цены вечерней сессии
BigDecimal eveningPrice = priceCacheService.getLastEveningSessionPrice("BBG004730N88");

// Получение последней цены открытия
BigDecimal openPrice = priceCacheService.getLastOpenPrice("BBG004730N88");

// Получение последней цены сделки (last_price)
BigDecimal lastPrice = priceCacheService.getLastPrice("BBG004730N88");

// Получение всех цен для инструмента
Map<String, BigDecimal> prices = priceCacheService.getPricesForFigi("BBG004730N88");
// Результат: {closePrice=250.50, eveningSessionPrice=250.45, openPrice=249.80, lastPrice=250.55}

// Получение всех цен закрытия
Map<String, BigDecimal> allClosePrices = priceCacheService.getAllClosePrices();

// Получение статистики
Map<String, Object> stats = priceCacheService.getCacheStats();
```

### 3. REST API

**Статистика:**
```bash
# Получение статистики кэша
curl http://localhost:8085/api/price-cache/stats

# Получение информации о планировщике
curl http://localhost:8085/api/price-cache/scheduler-info
```

**Получение цен:**
```bash
# Все цены закрытия
curl http://localhost:8085/api/price-cache/close-price

# Все цены открытия
curl http://localhost:8085/api/price-cache/open-price

# Все цены вечерней сессии
curl http://localhost:8085/api/price-cache/evening-session-price

# Все последние цены сделок (last_price)
curl http://localhost:8085/api/price-cache/last-price

# Последняя цена закрытия для инструмента
curl "http://localhost:8085/api/price-cache/last-close-price?figi=BBG004730N88"

# Все цены для инструмента (по FIGI или тикеру)
curl http://localhost:8085/api/price-cache/prices/BBG004730N88
curl http://localhost:8085/api/price-cache/prices/SBER
```

**Управление кэшем:**
```bash
# Очистка кэша
curl -X POST http://localhost:8085/api/price-cache/clear

# Асинхронная перезагрузка всех цен
curl -X POST http://localhost:8085/api/price-cache/reload

# Принудительная перезагрузка всех цен
curl -X POST http://localhost:8085/api/price-cache/force-reload-all

# Перезагрузка только цен закрытия
curl -X POST http://localhost:8085/api/price-cache/reload-close-prices

# Перезагрузка только последних цен сделок (last_price)
curl -X POST http://localhost:8085/api/price-cache/reload-last-prices

# Перезагрузка инструментов
curl -X POST http://localhost:8085/api/price-cache/reload-instruments
```

**Работа с объемами:**
```bash
# Все данные объемов
curl http://localhost:8085/api/price-cache/volumes

# Данные объемов для инструмента
curl http://localhost:8085/api/price-cache/volumes/BBG004730N88

# Загрузка объемов выходного дня
curl -X POST http://localhost:8085/api/price-cache/load-weekend-volumes

# Перезагрузка данных объемов
curl -X POST http://localhost:8085/api/price-cache/reload-volumes
```

## Производительность

- **In-memory кэш** - обеспечивает быстрый доступ к данным (O(1) для поиска)
- **ConcurrentHashMap** - thread-safe операции без блокировок
- **Автоматическая инициализация** - кэш загружается при старте приложения через `@PostConstruct`
- **Асинхронная загрузка** - через `StartupPriceLoader` не блокирует запуск приложения
- **Оптимизированное потребление памяти** - загружаются только последние цены (не вся история)
- **Быстрая загрузка** - значительно меньше данных для обработки
- **Автоматическое обновление** - кэш обновляется по расписанию без вмешательства
- **Унифицированная логика** - автоматическая обработка выходных дней

## Мониторинг

**REST API:**
- `GET /api/price-cache/stats` - статистика кэша (количество цен, даты)
- `GET /api/price-cache/scheduler-info` - информация о планировщике обновлений

**Логирование:**
- Все операции загрузки и обновления логируются
- Проверка здоровья кэша каждый час
- Предупреждения при пустом кэше или старых данных

**Статистика включает:**
- Количество цен закрытия в кэше
- Количество цен вечерней сессии в кэше
- Количество цен открытия в кэше
- Количество последних цен сделок (last_price) в кэше
- Последние даты для каждого типа цен

## Ограничения

- **In-memory кэш** - данные хранятся в памяти, при перезапуске приложения требуется повторная загрузка
- **Размер кэша** - ограничен доступной памятью сервера
- **Распределенность** - данные не синхронизируются между экземплярами приложения (для распределенного кэша нужен Redis)
- **Только последние цены** - исторические данные не кэшируются, доступны только последние цены
- **Зависимость от БД** - кэш зависит от данных в базе данных
- **Время обновления** - автоматическое обновление происходит в 6:00 MSK, между обновлениями данные могут быть устаревшими

## Структура данных

### Кэш цен

**Типы цен:**
- `lastClosePricesCache` - цены закрытия (Map<FIGI, BigDecimal>)
- `lastEveningSessionPricesCache` - цены вечерней сессии (Map<FIGI, BigDecimal>)
- `lastOpenPricesCache` - цены открытия (Map<FIGI, BigDecimal>)
- `lastPricesCache` - последние цены сделок (last_price) (Map<FIGI, BigDecimal>)

**Метаданные:**
- `lastClosePriceDate` - последняя дата цен закрытия
- `lastEveningSessionDate` - последняя дата цен вечерней сессии
- `lastOpenPriceDate` - последняя дата цен открытия
- `lastPriceDate` - последняя дата последних цен сделок (last_price)

### Формат ответов API

**Статистика кэша:**
```json
{
  "closePricesCount": 150,
  "eveningSessionPricesCount": 145,
  "openPricesCount": 148,
  "lastPricesCount": 150,
  "lastClosePriceDate": "2024-01-15",
  "lastEveningSessionDate": "2024-01-15",
  "lastOpenPriceDate": "2024-01-15",
  "lastPriceDate": "2024-01-15"
}
```

**Цены для инструмента:**
```json
{
  "figi": "BBG004730N88",
  "ticker": "SBER",
  "prices": {
    "openPrice": 249.80,
    "closePrice": 250.50,
    "eveningSessionPrice": 250.45,
    "lastPrice": 250.55
  },
  "dates": {
    "closePriceDate": "2024-01-15",
    "eveningSessionPriceDate": "2024-01-15",
    "openPriceDate": "2024-01-15",
    "lastPriceDate": "2024-01-15"
  }
}
```

## Развитие

Возможные улучшения:

- **Redis интеграция** - для распределенного кэша между экземплярами приложения
- **TTL (Time To Live)** - автоматическое истечение срока действия данных
- **Метрики производительности** - через Spring Actuator или Prometheus
- **Кэширование исторических данных** - с ограничением по времени (например, последние 30 дней)
- **WebSocket уведомления** - о обновлении кэша в реальном времени
- **Кэширование объемов** - оптимизация работы с объемами торгов
