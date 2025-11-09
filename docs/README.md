# Investment Data Scanner Service

Высокопроизводительный микросервис для обработки рыночных данных в реальном времени с интеграцией T-Invest API.

## 📋 Содержание

- [Обзор](#обзор)
- [Архитектура](#архитектура)
- [Быстрый старт](#быстрый-старт)
- [Конфигурация](#конфигурация)
- [API Документация](#api-документация)
- [Производительность](#производительность)
- [Мониторинг](#мониторинг)
- [Развертывание](#развертывание)
- [Рефакторинг и оптимизация](#рефакторинг-и-оптимизация)
- [Разработка](#разработка)

## 🚀 Обзор

**Investment Data Scanner Service** — это Spring Boot приложение, предназначенное для получения, обработки и трансляции рыночных данных в реальном времени через T-Invest API. Система обеспечивает высокую производительность и минимальные задержки при обработке котировок, сделок и данных стакана заявок.

### Основные возможности

- ✅ **Потоковая обработка данных** от T-Invest API через gRPC
- ✅ **WebSocket трансляция** котировок в реальном времени
- ✅ **Кэширование инструментов** для быстрого доступа
- ✅ **Обработка торговых сессий** (утренняя и выходного дня)
- ✅ **Метрики и мониторинг** производительности
- ✅ **Поддержка пар инструментов** для анализа дельт
- ✅ **Автоматическое переподключение** к API
- ✅ **Дедупликация данных** для оптимизации

### Технический стек

- **Java 21** - основной язык разработки
- **Spring Boot 3.5.4** - фреймворк приложения
- **Spring WebSocket** - WebSocket поддержка
- **Spring Data JPA** - работа с базой данных
- **PostgreSQL** - основная база данных
- **gRPC** - интеграция с T-Invest API
- **Lombok** - упрощение кода
- **Micrometer** - метрики и мониторинг
- **Maven** - управление зависимостями

## 🏗️ Архитектура

### Компонентная диаграмма

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   T-Invest API  │───▶│ MarketDataStream│───▶│ QuoteScanner    │
│   (gRPC)        │    │ Service         │    │ Service         │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │                       │
                                ▼                       ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │ MarketData      │    │ Notification    │
                       │ Processor       │    │ Service         │
                       └─────────────────┘    └─────────────────┘
                                │                       │
                                ▼                       ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │ Instrument      │    │ WebSocket       │
                       │ Cache Service   │    │ Controller      │
                       └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │ PostgreSQL      │
                       │ Database        │
                       └─────────────────┘
```

### Основные компоненты

| Компонент                      | Назначение                  | Ключевые функции                                     |
| ------------------------------ | --------------------------- | ---------------------------------------------------- |
| **MarketDataStreamingService** | Управление gRPC соединением | Подписка на данные, переподключение                  |
| **QuoteScannerService**        | Центральный оркестратор     | Координация компонентов, управление жизненным циклом |
| **MarketDataProcessor**        | Обработка рыночных данных   | Дедупликация, асинхронная обработка, метрики         |
| **NotificationService**        | Доставка уведомлений        | Управление подписками, параллельная отправка         |
| **InstrumentCacheService**     | Кэширование инструментов    | Хранение цен, имен, тикеров, объемов                 |
| **SessionTimeService**         | Управление сессиями         | Определение времени торговых сессий                  |

## 🚀 Быстрый старт

### Предварительные требования

- Java 21+
- Maven 3.6+
- PostgreSQL 12+
- T-Invest API токен

### Установка и запуск

1. **Клонирование репозитория**

```bash
git clone <repository-url>
cd InvestmentDataScannerService
```

2. **Настройка переменных окружения**

```bash
# Создайте файл .env в корне проекта
TINKOFF_API_TOKEN=your_token_here
DB_URL=jdbc:postgresql://localhost:5432/investment_db
DB_USERNAME=your_username
DB_PASSWORD=your_password
SERVER_PORT=8085
APP_TIMEZONE=Europe/Moscow
```

3. **Настройка базы данных**

```sql
-- Создайте схему и таблицы
CREATE SCHEMA IF NOT EXISTS invest;

-- Основные таблицы будут созданы автоматически при первом запуске
-- или используйте SQL скрипты из папки database/
```

4. **Сборка и запуск**

```bash
# Сборка проекта
mvn clean package

# Запуск приложения
java -jar target/investment-data-scanner-service-0.0.1-SNAPSHOT.jar

# Или через Maven
mvn spring-boot:run
```

5. **Проверка работы**

```bash
# Проверка статуса
curl http://localhost:8085/api/scanner/stats

# WebSocket подключение
wscat -c ws://localhost:8085/ws/quotes
```

## ⚙️ Конфигурация

### Основные настройки (application.properties)

```properties
# T-Invest API
tinkoff.api.token=${TINKOFF_API_TOKEN}
server.port=${SERVER_PORT:8085}

# Database
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false

# Performance
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-init-sql=SET search_path TO invest, public;

# Quote Scanner Configuration
quote-scanner.max-quotes-per-second=10000
quote-scanner.enable-database-saving=false
quote-scanner.enable-websocket-broadcast=true
quote-scanner.enable-order-book-subscription=true
quote-scanner.order-book-depth=1
quote-scanner.enable-test-mode-morning=true
quote-scanner.enable-test-mode-weekend=true
quote-scanner.enable-test-mode-futures=true
quote-scanner.enable-shares-mode=true

# Logging
logging.level.com.example.investmentdatascannerservice.service.QuoteScannerService=DEBUG
logging.level.com.example.investmentdatascannerservice.controller.QuoteWebSocketController=DEBUG
```

### Профили конфигурации

- `application-batch.properties` - настройки для batch обработки
- `application-nonblocking.properties` - неблокирующая обработка
- `application-orderbook.properties` - настройки стакана заявок
- `application-streaming.properties` - потоковая обработка

## 📡 API Документация

### WebSocket Endpoints

#### Подключение к котировкам

```javascript
const ws = new WebSocket("ws://localhost:8085/ws/quotes");

ws.onmessage = function (event) {
  const quoteData = JSON.parse(event.data);
  console.log("Received quote:", quoteData);
};
```

#### Подключение к парам инструментов

```javascript
const ws = new WebSocket("ws://localhost:8085/ws/pairs");

ws.onmessage = function (event) {
  const pairData = JSON.parse(event.data);
  console.log("Received pair data:", pairData);
};
```

### REST Endpoints

#### GET /api/scanner/stats

Получение статистики сканера

**Ответ:**

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
  "sessionInfo": "MORNING_SESSION (утренняя сессия)",
  "isScannerActive": true
}
```

#### GET /api/scanner/instruments

Получение списка отслеживаемых инструментов

**Ответ:**

```json
{
  "instruments": ["BBG004730N88", "BBG0047315Y7", "BBG000MZL0Y6"],
  "count": 150
}
```

#### GET /api/scanner/prices

Получение текущих цен

**Ответ:**

```json
{
  "prices": {
    "BBG004730N88": 250.5,
    "BBG0047315Y7": 245.3
  },
  "instrumentNames": {
    "BBG004730N88": "SBER",
    "BBG0047315Y7": "SBERP"
  },
  "count": 150
}
```

### Формат данных QuoteData

```json
{
  "figi": "BBG004730N88",
  "ticker": "SBER",
  "instrumentName": "Сбербанк",
  "currentPrice": 250.5,
  "previousPrice": 249.8,
  "priceChange": 0.7,
  "priceChangePercent": 0.28,
  "closePrice": 248.9,
  "openPrice": 249.2,
  "closePriceChange": 1.6,
  "closePriceChangePercent": 0.64,
  "bestBid": 250.45,
  "bestAsk": 250.55,
  "bestBidQuantity": 1000,
  "bestAskQuantity": 1500,
  "timestamp": "2024-01-15T10:30:45",
  "volume": 5000,
  "totalVolume": 150000,
  "direction": "UP",
  "avgVolumeMorning": 120000,
  "avgVolumeWeekend": 80000
}
```

## ⚡ Производительность

### Текущие показатели

| Метрика                    | Значение                       |
| -------------------------- | ------------------------------ |
| **Пропускная способность** | до 10,000 котировок/сек        |
| **Задержка обработки**     | < 10ms                         |
| **Память**                 | ~500MB при 1000 инструментах   |
| **CPU**                    | 2-4 ядра для стабильной работы |
| **WebSocket соединения**   | до 100 одновременных           |

### Оптимизации

- ✅ Асинхронная обработка данных
- ✅ Кэширование в памяти (ConcurrentHashMap)
- ✅ Дедупликация сообщений
- ✅ Параллельная отправка уведомлений
- ✅ Оптимизированные пулы потоков
- ✅ Batch операции с базой данных

### Настройка производительности

```properties
# Увеличение пропускной способности
quote-scanner.max-quotes-per-second=20000

# Оптимизация пула соединений
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10

# Настройка JVM
-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

## 📊 Мониторинг

### Доступные метрики

| Метрика                       | Описание                 | Тип     |
| ----------------------------- | ------------------------ | ------- |
| `market.data.processed`       | Обработанные данные      | Counter |
| `notifications.sent`          | Отправленные уведомления | Counter |
| `notifications.failed`        | Неудачные уведомления    | Counter |
| `market.data.processing.time` | Время обработки          | Timer   |
| `notifications.subscribers`   | Количество подписчиков   | Gauge   |

### Health Checks

```bash
# Проверка состояния приложения
curl http://localhost:8085/actuator/health

# Детальная информация о здоровье
curl http://localhost:8085/actuator/health/detail
```

### Логирование

```properties
# Настройка уровней логирования
logging.level.com.example.investmentdatascannerservice=INFO
logging.level.com.example.investmentdatascannerservice.service.QuoteScannerService=DEBUG
logging.level.com.example.investmentdatascannerservice.controller.QuoteWebSocketController=DEBUG

# Логирование в файл
logging.file.name=logs/investment-scanner.log
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

## 🚀 Развертывание

### Docker (Рекомендуемый способ)

#### Быстрый запуск

1. **Скопируйте файл с переменными окружения:**

   ```bash
   cp env.example .env
   ```

2. **Отредактируйте файл `.env` и укажите ваш Tinkoff API токен:**

   ```bash
   TINKOFF_API_TOKEN=your_actual_tinkoff_token_here
   ```

3. **Запустите приложение:**

   ```bash
   docker-compose up -d
   ```

#### Доступ к приложению

- **Основное приложение:** http://localhost:8085
- **Health Check:** http://localhost:8085/actuator/health
- **Metrics:** http://localhost:8085/actuator/metrics
- **WebSocket котировки:** ws://localhost:8085/ws/quotes
- **WebSocket пары:** ws://localhost:8085/ws/pairs

#### Управление контейнерами

```bash
# Остановка всех сервисов
docker-compose down

# Остановка с удалением данных
docker-compose down -v

# Пересборка образа
docker-compose build --no-cache

# Просмотр логов
docker-compose logs -f investment-scanner
```

#### Пересборка образа

```bash
# Пересборка образа приложения
docker-compose build --no-cache

# Перезапуск с новым образом
docker-compose up -d
```

#### Ручная сборка и запуск

```dockerfile
FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/investment-data-scanner-service-*.jar app.jar

EXPOSE 8085

ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

```bash
# Сборка образа
docker build -t investment-scanner:latest .

# Запуск контейнера
docker run -d \
  --name investment-scanner \
  -p 8085:8085 \
  -e TINKOFF_API_TOKEN=your_token \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/investment_db \
  investment-scanner:latest
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: investment-scanner
spec:
  replicas: 3
  selector:
    matchLabels:
      app: investment-scanner
  template:
    metadata:
      labels:
        app: investment-scanner
    spec:
      containers:
        - name: scanner
          image: investment-scanner:latest
          ports:
            - containerPort: 8085
          env:
            - name: TINKOFF_API_TOKEN
              valueFrom:
                secretKeyRef:
                  name: tinvest-secret
                  key: token
            - name: DB_URL
              value: "jdbc:postgresql://postgres:5432/investment_db"
          resources:
            limits:
              cpu: 1000m
              memory: 1Gi
            requests:
              cpu: 500m
              memory: 512Mi
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8085
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8085
            initialDelaySeconds: 30
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: investment-scanner-service
spec:
  selector:
    app: investment-scanner
  ports:
    - port: 8085
      targetPort: 8085
  type: ClusterIP
```

## 🔧 Рефакторинг и оптимизация

### Критические улучшения (Приоритет 1)

#### 1. Circuit Breaker для T-Invest API

```java
@CircuitBreaker(name = "tinvest-api", fallbackMethod = "fallbackMethod")
public MarketDataResponse getMarketData() {
    // Вызов T-Invest API
}
```

#### 2. Оптимизация кэширования с Caffeine

```java
private final Cache<String, InstrumentData> instrumentCache =
    Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();
```

#### 3. Расширенные метрики

```java
@Timed(name = "market.data.processing", description = "Time taken to process market data")
public void processMarketData() {
    // Обработка данных
}
```

### Производительность (Приоритет 2)

#### 1. Batch обработка данных

```java
@Scheduled(fixedDelay = 100)
public void processBatch() {
    if (batch.size() >= BATCH_SIZE) {
        processBatchInternal();
        batch.clear();
    }
}
```

#### 2. Оптимизация WebSocket

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) {
    session.setCompressionEnabled(true);
}
```

### Надежность (Приоритет 3)

#### 1. Health Checks

```java
@Component
public class CustomHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        boolean isHealthy = checkSystemHealth();
        return isHealthy ? Health.up().build() : Health.down().build();
    }
}
```

#### 2. Retry механизм

```java
@Retryable(value = {ConnectException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void connectToTInvest() {
    // Подключение к T-Invest API
}
```

## 💻 Разработка

### Структура проекта

```
src/
├── main/
│   ├── java/
│   │   └── com/example/investmentdatascannerservice/
│   │       ├── config/          # Конфигурационные классы
│   │       ├── controller/      # REST и WebSocket контроллеры
│   │       ├── dto/            # Data Transfer Objects
│   │       ├── entity/         # JPA сущности
│   │       ├── repository/     # Репозитории для работы с БД
│   │       ├── service/        # Бизнес-логика
│   │       └── utils/          # Утилитарные классы
│   └── resources/
│       ├── application*.properties  # Конфигурационные файлы
│       └── static/             # Статические ресурсы
└── test/                       # Тесты
```

### Запуск в режиме разработки

```bash
# Запуск с профилем разработки
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Запуск с отладкой
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

### Тестирование

```bash
# Запуск всех тестов
mvn test

# Запуск с покрытием кода
mvn test jacoco:report

# Запуск интеграционных тестов
mvn verify
```

### Сборка

```bash
# Очистка и сборка
mvn clean package

# Сборка без тестов
mvn clean package -DskipTests

# Создание Docker образа
mvn spring-boot:build-image
```

## 📝 Лицензия

Этот проект распространяется под лицензией MIT. См. файл [LICENSE](LICENSE) для получения дополнительной информации.

## 🤝 Вклад в проект

1. Форкните репозиторий
2. Создайте ветку для новой функции (`git checkout -b feature/amazing-feature`)
3. Зафиксируйте изменения (`git commit -m 'Add some amazing feature'`)
4. Отправьте в ветку (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📞 Поддержка

Если у вас есть вопросы или проблемы, пожалуйста:

1. Проверьте [Issues](../../issues) на наличие существующих проблем
2. Создайте новый Issue с подробным описанием
3. Обратитесь к команде разработки

---

**Версия документации:** 1.0.0  
**Последнее обновление:** 2024-01-15
