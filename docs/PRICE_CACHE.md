# Price Cache System

Система кэширования цен закрытия, открытия и вечерней сессии для быстрого доступа к историческим данным.

## Обзор

Система кэширования цен обеспечивает:

- Автоматическую загрузку **только последних цен** при запуске приложения
- In-memory кэширование для быстрого доступа
- Spring Cache интеграцию
- REST API для управления кэшем
- Веб-интерфейс для тестирования
- Оптимизированное потребление памяти (только последние цены)

## Компоненты

### 1. PriceCacheService

Основной сервис для работы с кэшем цен.

**Основные методы:**

- `getClosePrice(String figi, LocalDate date)` - получение цены закрытия (только для последней даты)
- `getEveningSessionPrice(String figi, LocalDate date)` - получение цены вечерней сессии (только для последней даты)
- `getLastClosePrice(String figi)` - получение последней цены закрытия
- `getLastEveningSessionPrice(String figi)` - получение последней цены вечерней сессии
- `getClosePricesForDate(List<String> figis, LocalDate date)` - получение цен для списка инструментов (только для последней даты)
- `reloadCache()` - перезагрузка кэша
- `clearCache()` - очистка кэша

### 2. StartupPriceLoader

Сервис для загрузки цен при запуске приложения.

**Функции:**

- Автоматическая загрузка при `ApplicationReadyEvent`
- Асинхронная загрузка цен
- Принудительная перезагрузка
- Обновление из API

### 3. PriceCacheController

REST API контроллер для управления кэшем.

**Endpoints:**

- `GET /api/price-cache/stats` - статистика кэша
- `GET /api/price-cache/close-price?figi={figi}&date={date}` - цена закрытия
- `GET /api/price-cache/evening-session-price?figi={figi}&date={date}` - цена вечерней сессии
- `GET /api/price-cache/last-close-price?figi={figi}` - последняя цена закрытия
- `GET /api/price-cache/last-evening-session-price?figi={figi}` - последняя цена вечерней сессии
- `GET /api/price-cache/close-prices?figis={figis}&date={date}` - цены закрытия для списка
- `GET /api/price-cache/evening-session-prices?figis={figis}&date={date}` - цены вечерней сессии для списка
- `GET /api/price-cache/last-close-prices?figis={figis}` - последние цены закрытия для списка
- `GET /api/price-cache/last-evening-session-prices?figis={figis}` - последние цены вечерней сессии для списка
- `POST /api/price-cache/reload` - перезагрузка кэша
- `POST /api/price-cache/clear` - очистка кэша
- `POST /api/price-cache/reload-all` - перезагрузка всех цен
- `POST /api/price-cache/refresh-from-api` - обновление из API

### 4. CacheConfig

Конфигурация Spring Cache.

**Настройки:**

- ConcurrentMapCacheManager для in-memory кэша
- Названия кэшей: closePrices, eveningSessionPrices, lastClosePrices, lastEveningSessionPrices

## Конфигурация

### application.properties

```properties
# Cache Configuration
spring.cache.type=simple
spring.cache.cache-names=closePrices,eveningSessionPrices,lastClosePrices,lastEveningSessionPrices

# Startup Price Loading Configuration
startup.price-loader.enabled=true
startup.price-loader.async-loading=true
startup.price-loader.load-close-prices=true
startup.price-loader.load-evening-session-prices=true
```

## Использование

### 1. Автоматическая загрузка при запуске

При запуске приложения автоматически загружаются все цены из базы данных в кэш.

### 2. Программное использование

```java
@Autowired
private PriceCacheService priceCacheService;

// Получение цены закрытия
BigDecimal closePrice = priceCacheService.getClosePrice("BBG004730N88", LocalDate.now());

// Получение последней цены закрытия
BigDecimal lastClosePrice = priceCacheService.getLastClosePrice("BBG004730N88");

// Получение цен для списка инструментов
List<String> figis = Arrays.asList("BBG004730N88", "BBG0047315Y7");
Map<String, BigDecimal> prices = priceCacheService.getLastClosePrices(figis);
```

### 3. REST API

```bash
# Получение статистики кэша
curl http://localhost:8085/api/price-cache/stats

# Получение цены закрытия
curl "http://localhost:8085/api/price-cache/close-price?figi=BBG004730N88&date=2024-01-15"

# Получение последней цены закрытия
curl "http://localhost:8085/api/price-cache/last-close-price?figi=BBG004730N88"

# Перезагрузка кэша
curl -X POST http://localhost:8085/api/price-cache/reload
```

### 4. Веб-интерфейс

Откройте `http://localhost:8085/price-cache.html` для тестирования функциональности кэша.

## Производительность

- In-memory кэш обеспечивает быстрый доступ к данным
- ConcurrentHashMap для thread-safe операций
- Spring Cache для дополнительной оптимизации
- Асинхронная загрузка при запуске не блокирует приложение
- **Оптимизированное потребление памяти** - загружаются только последние цены
- **Быстрая загрузка** - значительно меньше данных для обработки

## Мониторинг

- Статистика кэша через REST API
- Логирование операций загрузки и обновления
- Метрики через Spring Actuator

## Ограничения

- Кэш хранится в памяти, при перезапуске приложения требуется повторная загрузка
- Размер кэша ограничен доступной памятью
- Данные не синхронизируются между экземплярами приложения
- **Доступны только последние цены** - исторические данные не кэшируются
- Запросы к историческим датам (кроме последней) возвращают null

## Развитие

Возможные улучшения:

- Интеграция с Redis для распределенного кэша
- TTL для автоматического обновления данных
- Метрики производительности
- Автоматическое обновление из API по расписанию
