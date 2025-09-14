# Руководство по рефакторингу и оптимизации

## Обзор

Данное руководство содержит детальные рекомендации по рефакторингу и оптимизации Investment Data Scanner Service для повышения производительности, надежности и поддерживаемости.

## Приоритизация улучшений

### Критические улучшения (Приоритет 1) - 1-2 недели

- [ ] Circuit Breaker для T-Invest API
- [ ] Оптимизация кэширования с Caffeine
- [ ] Расширенные метрики и мониторинг
- [ ] Улучшение обработки ошибок

### Производительность (Приоритет 2) - 2-3 недели

- [ ] Batch обработка данных
- [ ] Оптимизация WebSocket соединений
- [ ] Асинхронная обработка уведомлений
- [ ] Профилирование и оптимизация узких мест

### Надежность (Приоритет 3) - 2-3 недели

- [ ] Health Checks и мониторинг
- [ ] Distributed Tracing
- [ ] Улучшение логирования
- [ ] Автоматическое восстановление

### Масштабирование (Приоритет 4) - 3-4 недели

- [ ] Event-driven архитектура
- [ ] Микросервисная архитектура
- [ ] Kubernetes развертывание
- [ ] Service Mesh (Istio)

## 1. Критические улучшения

### 1.1 Circuit Breaker для T-Invest API

#### Проблема

Текущая система не имеет защиты от отказов внешнего API, что может привести к каскадным сбоям.

#### Решение

**Добавление зависимостей:**

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>2.1.0</version>
</dependency>
```

**Конфигурация Circuit Breaker:**

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      tinvest-api:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
        minimum-number-of-calls: 5
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

**Реализация:**

```java
@Service
public class TInvestApiClient {

    private final CircuitBreaker circuitBreaker;
    private final MarketDataStreamServiceGrpc.MarketDataStreamServiceStub streamStub;

    public TInvestApiClient(MarketDataStreamServiceGrpc.MarketDataStreamServiceStub streamStub,
                          CircuitBreakerRegistry circuitBreakerRegistry) {
        this.streamStub = streamStub;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("tinvest-api");
    }

    @CircuitBreaker(name = "tinvest-api", fallbackMethod = "fallbackMethod")
    public MarketDataResponse getMarketData() {
        // Вызов T-Invest API
        return streamStub.getMarketData(request);
    }

    public MarketDataResponse fallbackMethod(Exception ex) {
        log.warn("T-Invest API unavailable, using cached data", ex);
        return getCachedData();
    }

    private MarketDataResponse getCachedData() {
        // Возврат кэшированных данных
        return cachedDataService.getLastKnownData();
    }
}
```

**Преимущества:**

- Защита от каскадных сбоев
- Автоматическое восстановление
- Graceful degradation

### 1.2 Оптимизация кэширования с Caffeine

#### Проблема

ConcurrentHashMap не обеспечивает автоматическое удаление устаревших данных и не имеет продвинутых возможностей кэширования.

#### Решение

**Добавление зависимостей:**

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>
```

**Реализация оптимизированного кэша:**

```java
@Service
public class OptimizedInstrumentCacheService {

    private final Cache<String, InstrumentData> instrumentCache;
    private final Cache<String, BigDecimal> priceCache;
    private final Cache<String, String> nameCache;

    public OptimizedInstrumentCacheService() {
        this.instrumentCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

        this.priceCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

        this.nameCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .recordStats()
            .build();
    }

    public BigDecimal getLastPrice(String figi) {
        return priceCache.getIfPresent(figi);
    }

    public void setLastPrice(String figi, BigDecimal price) {
        priceCache.put(figi, price);
    }

    @Async
    public CompletableFuture<Void> updateCacheAsync(String figi, InstrumentData data) {
        instrumentCache.put(figi, data);
        return CompletableFuture.completedFuture(null);
    }

    public CacheStats getCacheStats() {
        return CacheStats.of(
            instrumentCache.stats(),
            priceCache.stats(),
            nameCache.stats()
        );
    }
}
```

**Конфигурация кэша:**

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .recordStats());
        return cacheManager;
    }
}
```

**Преимущества:**

- Автоматическое удаление устаревших данных
- Статистика использования кэша
- Асинхронное обновление
- Лучшая производительность

### 1.3 Расширенные метрики и мониторинг

#### Проблема

Текущие метрики недостаточно детализированы для эффективного мониторинга.

#### Решение

**Добавление зависимостей:**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

**Расширенные метрики:**

```java
@Component
public class AdvancedMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer processingTimer;
    private final Counter errorCounter;
    private final Gauge activeConnections;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public AdvancedMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.processingTimer = Timer.builder("market.data.processing.time")
            .description("Time taken to process market data")
            .register(meterRegistry);

        this.errorCounter = Counter.builder("errors.total")
            .description("Total number of errors")
            .register(meterRegistry);

        this.activeConnections = Gauge.builder("websocket.connections.active")
            .description("Number of active WebSocket connections")
            .register(meterRegistry, this, AdvancedMetrics::getActiveConnections);

        this.cacheHits = Counter.builder("cache.hits")
            .description("Cache hits")
            .register(meterRegistry);

        this.cacheMisses = Counter.builder("cache.misses")
            .description("Cache misses")
            .register(meterRegistry);
    }

    @Timed(name = "market.data.processing", description = "Time taken to process market data")
    public void processMarketData() {
        // Обработка данных
    }

    public void recordError(String errorType) {
        errorCounter.increment(Tags.of("type", errorType));
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    private double getActiveConnections() {
        return webSocketController.getActiveConnections();
    }
}
```

**Конфигурация метрик:**

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
```

### 1.4 Улучшение обработки ошибок

#### Проблема

Текущая обработка ошибок недостаточно детализирована и не обеспечивает достаточную информацию для диагностики.

#### Решение

**Глобальный обработчик ошибок:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;
    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TInvestApiException.class)
    public ResponseEntity<ErrorResponse> handleTInvestApiException(TInvestApiException ex) {
        log.error("T-Invest API error: {}", ex.getMessage(), ex);
        meterRegistry.counter("errors.tinvest.api").increment();

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("T-Invest API Error")
            .message(ex.getMessage())
            .path(getCurrentRequestPath())
            .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(WebSocketException.class)
    public ResponseEntity<ErrorResponse> handleWebSocketException(WebSocketException ex) {
        log.error("WebSocket error: {}", ex.getMessage(), ex);
        meterRegistry.counter("errors.websocket").increment();

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("WebSocket Error")
            .message(ex.getMessage())
            .path(getCurrentRequestPath())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        meterRegistry.counter("errors.generic").increment();

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .path(getCurrentRequestPath())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

**Кастомные исключения:**

```java
public class TInvestApiException extends RuntimeException {
    private final String errorCode;
    private final Instant timestamp;

    public TInvestApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.timestamp = Instant.now();
    }

    // getters
}

public class WebSocketException extends RuntimeException {
    private final String sessionId;
    private final String errorType;

    public WebSocketException(String message, String sessionId, String errorType) {
        super(message);
        this.sessionId = sessionId;
        this.errorType = errorType;
    }

    // getters
}
```

## 2. Производительность

### 2.1 Batch обработка данных

#### Проблема

Обработка данных по одному элементу неэффективна при высоких нагрузках.

#### Решение

**Batch процессор:**

```java
@Component
public class BatchDataProcessor {

    private final List<QuoteData> batch = new ArrayList<>();
    private final Object lock = new Object();
    private final int BATCH_SIZE = 100;
    private final long BATCH_TIMEOUT_MS = 100;

    private final QuoteDataRepository repository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 100) // Каждые 100ms
    public void processBatch() {
        List<QuoteData> currentBatch;

        synchronized (lock) {
            if (batch.size() >= BATCH_SIZE ||
                (!batch.isEmpty() && shouldProcessTimeout())) {
                currentBatch = new ArrayList<>(batch);
                batch.clear();
            } else {
                return;
            }
        }

        if (!currentBatch.isEmpty()) {
            processBatchInternal(currentBatch);
        }
    }

    public void addToBatch(QuoteData data) {
        synchronized (lock) {
            batch.add(data);
        }
    }

    private void processBatchInternal(List<QuoteData> batch) {
        try {
            // Batch сохранение в БД
            repository.saveAll(batch);

            // Batch уведомления
            notificationService.notifyBatch(batch);

            log.debug("Processed batch of {} items", batch.size());
        } catch (Exception e) {
            log.error("Error processing batch", e);
            // Fallback к индивидуальной обработке
            batch.forEach(notificationService::notifySubscribers);
        }
    }
}
```

**Оптимизированный NotificationService:**

```java
@Service
public class OptimizedNotificationService {

    private final Set<Consumer<QuoteData>> subscribers = ConcurrentHashMap.newKeySet();
    private final ExecutorService notificationExecutor;

    public void notifyBatch(List<QuoteData> batch) {
        if (subscribers.isEmpty() || batch.isEmpty()) {
            return;
        }

        // Группировка по подписчикам для параллельной обработки
        subscribers.parallelStream().forEach(subscriber -> {
            notificationExecutor.submit(() -> {
                try {
                    batch.forEach(subscriber::accept);
                } catch (Exception e) {
                    log.warn("Error notifying subscriber", e);
                }
            });
        });
    }
}
```

### 2.2 Оптимизация WebSocket соединений

#### Проблема

Текущая реализация WebSocket не оптимизирована для высокой производительности.

#### Решение

**Оптимизированный WebSocket контроллер:**

```java
@Component
public class OptimizedWebSocketController implements WebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final ExecutorService messageExecutor;

    // Кэш для сериализованных сообщений
    private final Cache<String, String> messageCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);

        // Настройка сжатия
        session.setCompressionEnabled(true);

        // Настройка буферов
        session.setTextMessageSizeLimit(1024 * 1024); // 1MB
        session.setBinaryMessageSizeLimit(1024 * 1024); // 1MB

        log.info("WebSocket connection established. Total: {}", sessions.size());
    }

    public void broadcastQuote(QuoteData quoteData) {
        if (sessions.isEmpty()) {
            return;
        }

        // Кэширование сериализованного сообщения
        String messageKey = generateMessageKey(quoteData);
        String json = messageCache.get(messageKey, k -> {
            try {
                return objectMapper.writeValueAsString(quoteData);
            } catch (Exception e) {
                log.error("Error serializing message", e);
                return null;
            }
        });

        if (json == null) {
            return;
        }

        TextMessage message = new TextMessage(json);

        // Параллельная отправка
        sessions.parallelStream().forEach(session -> {
            messageExecutor.submit(() -> {
                try {
                    if (session.isOpen()) {
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error sending message to session {}", session.getId(), e);
                    sessions.remove(session);
                }
            });
        });
    }

    private String generateMessageKey(QuoteData quoteData) {
        return quoteData.getFigi() + "_" + quoteData.getTimestamp().toString();
    }
}
```

**WebSocket конфигурация:**

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(optimizedWebSocketController(), "/ws/quotes")
            .setAllowedOrigins("*")
            .addInterceptors(new WebSocketInterceptor());
    }

    @Bean
    public WebSocketHandler optimizedWebSocketController() {
        return new OptimizedWebSocketController();
    }
}
```

### 2.3 Асинхронная обработка уведомлений

#### Проблема

Синхронная обработка уведомлений может создавать узкие места.

#### Решение

**Reactive NotificationService:**

```java
@Service
public class ReactiveNotificationService {

    private final Flux<QuoteData> quoteDataFlux;
    private final Sinks.Many<QuoteData> quoteDataSink;
    private final ExecutorService notificationExecutor;

    public ReactiveNotificationService() {
        this.quoteDataSink = Sinks.many().multicast().onBackpressureBuffer();
        this.quoteDataFlux = quoteDataSink.asFlux()
            .publishOn(Schedulers.fromExecutor(notificationExecutor))
            .share();
    }

    public void notifySubscribers(QuoteData quoteData) {
        quoteDataSink.tryEmitNext(quoteData);
    }

    public Flux<QuoteData> getQuoteDataStream() {
        return quoteDataFlux;
    }

    @EventListener
    public void handleQuoteData(QuoteDataEvent event) {
        notifySubscribers(event.getQuoteData());
    }
}
```

## 3. Надежность

### 3.1 Health Checks и мониторинг

#### Решение

**Кастомные Health Indicators:**

```java
@Component
public class TInvestApiHealthIndicator implements HealthIndicator {

    private final TInvestApiClient apiClient;
    private final MeterRegistry meterRegistry;

    @Override
    public Health health() {
        try {
            // Проверка доступности API
            boolean isApiAvailable = apiClient.isApiAvailable();

            if (isApiAvailable) {
                return Health.up()
                    .withDetail("api", "T-Invest API")
                    .withDetail("status", "Available")
                    .withDetail("timestamp", Instant.now())
                    .build();
            } else {
                return Health.down()
                    .withDetail("api", "T-Invest API")
                    .withDetail("status", "Unavailable")
                    .withDetail("timestamp", Instant.now())
                    .build();
            }
        } catch (Exception e) {
            meterRegistry.counter("health.check.failed", "component", "tinvest-api").increment();
            return Health.down()
                .withDetail("api", "T-Invest API")
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", Instant.now())
                .build();
        }
    }
}

@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5);

            if (isValid) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "Connected")
                    .withDetail("timestamp", Instant.now())
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "Disconnected")
                    .withDetail("timestamp", Instant.now())
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", Instant.now())
                .build();
        }
    }
}
```

### 3.2 Distributed Tracing

#### Решение

**Конфигурация Tracing:**

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

**Трассировка в коде:**

```java
@Service
public class TracedMarketDataProcessor {

    private final Tracer tracer;

    public void processLastPrice(LastPrice price) {
        Span span = tracer.nextSpan()
            .name("process-last-price")
            .tag("figi", price.getFigi())
            .tag("price", price.getPrice().toString())
            .start();

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Обработка данных
            processLastPriceInternal(price);
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 3.3 Улучшение логирования

#### Решение

**Structured Logging:**

```java
@Component
public class StructuredLogger {

    private final Logger log = LoggerFactory.getLogger(StructuredLogger.class);
    private final ObjectMapper objectMapper;

    public void logQuoteProcessed(QuoteData quoteData, long processingTime) {
        Map<String, Object> logData = Map.of(
            "event", "quote_processed",
            "figi", quoteData.getFigi(),
            "ticker", quoteData.getTicker(),
            "price", quoteData.getCurrentPrice(),
            "processingTime", processingTime,
            "timestamp", Instant.now()
        );

        log.info("Quote processed: {}", logData);
    }

    public void logError(String errorType, String message, Exception e) {
        Map<String, Object> logData = Map.of(
            "event", "error",
            "errorType", errorType,
            "message", message,
            "exception", e.getClass().getSimpleName(),
            "stackTrace", Arrays.toString(e.getStackTrace()),
            "timestamp", Instant.now()
        );

        log.error("Error occurred: {}", logData);
    }
}
```

**Конфигурация логирования:**

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <message/>
                <mdc/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/investment-scanner.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/investment-scanner.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <message/>
                <mdc/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

## 4. Масштабирование

### 4.1 Event-driven архитектура

#### Решение

**Event Publisher:**

```java
@Component
public class MarketDataEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishLastPriceEvent(LastPrice price) {
        LastPriceEvent event = new LastPriceEvent(this, price);
        eventPublisher.publishEvent(event);
    }

    public void publishTradeEvent(Trade trade) {
        TradeEvent event = new TradeEvent(this, trade);
        eventPublisher.publishEvent(event);
    }
}

// Event классы
public class LastPriceEvent extends ApplicationEvent {
    private final LastPrice price;

    public LastPriceEvent(Object source, LastPrice price) {
        super(source);
        this.price = price;
    }

    public LastPrice getPrice() {
        return price;
    }
}
```

**Event Listeners:**

```java
@Component
public class MarketDataEventListener {

    @EventListener
    @Async
    public void handleLastPriceEvent(LastPriceEvent event) {
        // Обработка события
    }

    @EventListener
    @Async
    public void handleTradeEvent(TradeEvent event) {
        // Обработка события
    }
}
```

### 4.2 Микросервисная архитектура

#### Решение

**Разделение на сервисы:**

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Data Ingestion  │    │ Data Processing │    │ Data Streaming  │
│ Service         │    │ Service         │    │ Service         │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

**API Gateway:**

```java
@RestController
@RequestMapping("/api/v1")
public class ApiGatewayController {

    @Autowired
    private DataIngestionServiceClient ingestionClient;

    @Autowired
    private DataProcessingServiceClient processingClient;

    @Autowired
    private DataStreamingServiceClient streamingClient;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // Агрегация данных из всех сервисов
        Map<String, Object> stats = new HashMap<>();
        stats.put("ingestion", ingestionClient.getStats());
        stats.put("processing", processingClient.getStats());
        stats.put("streaming", streamingClient.getStats());

        return ResponseEntity.ok(stats);
    }
}
```

## 5. Тестирование

### 5.1 Unit тесты

```java
@ExtendWith(MockitoExtension.class)
class MarketDataProcessorTest {

    @Mock
    private InstrumentCacheService cacheService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MarketDataProcessor processor;

    @Test
    void shouldProcessLastPrice() {
        // Given
        LastPrice price = createTestLastPrice();
        when(cacheService.getLastPrice(any())).thenReturn(BigDecimal.valueOf(100));

        // When
        processor.processLastPrice(price);

        // Then
        verify(cacheService).setLastPrice(any(), any());
        verify(notificationService).notifySubscribers(any());
    }

    @Test
    void shouldNotProcessWhenSessionInactive() {
        // Given
        when(sessionService.isAnySessionActive()).thenReturn(false);
        LastPrice price = createTestLastPrice();

        // When
        processor.processLastPrice(price);

        // Then
        verify(cacheService, never()).setLastPrice(any(), any());
    }
}
```

### 5.2 Integration тесты

```java
@SpringBootTest
@AutoConfigureTestDatabase
class IntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldProcessEndToEnd() {
        // Тест полного цикла обработки данных
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/scanner/stats", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("totalQuotesProcessed");
    }
}
```

### 5.3 Performance тесты

```java
@Test
@Timeout(value = 1, unit = TimeUnit.SECONDS)
void shouldProcessWithinTimeLimit() {
    // Тест производительности
    long startTime = System.currentTimeMillis();

    for (int i = 0; i < 1000; i++) {
        processor.processLastPrice(createTestLastPrice());
    }

    long endTime = System.currentTimeMillis();
    assertThat(endTime - startTime).isLessThan(1000);
}
```

## 6. Мониторинг и алертинг

### 6.1 Prometheus метрики

```java
@Component
public class CustomMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter quotesProcessed;
    private final Timer processingTime;
    private final Gauge activeConnections;

    public CustomMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.quotesProcessed = Counter.builder("quotes.processed")
            .description("Total quotes processed")
            .register(meterRegistry);

        this.processingTime = Timer.builder("processing.time")
            .description("Processing time")
            .register(meterRegistry);

        this.activeConnections = Gauge.builder("websocket.connections")
            .description("Active WebSocket connections")
            .register(meterRegistry, this, CustomMetrics::getActiveConnections);
    }

    public void recordQuoteProcessed() {
        quotesProcessed.increment();
    }

    public void recordProcessingTime(Duration duration) {
        processingTime.record(duration);
    }

    private double getActiveConnections() {
        return webSocketController.getActiveConnections();
    }
}
```

### 6.2 Grafana дашборд

```json
{
  "dashboard": {
    "title": "Investment Scanner Dashboard",
    "panels": [
      {
        "title": "Quotes Processed Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(quotes_processed_total[5m])",
            "legendFormat": "Quotes/sec"
          }
        ]
      },
      {
        "title": "Processing Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(processing_time_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      },
      {
        "title": "Active Connections",
        "type": "singlestat",
        "targets": [
          {
            "expr": "websocket_connections",
            "legendFormat": "Active"
          }
        ]
      }
    ]
  }
}
```

## 7. План внедрения

### Этап 1: Критические улучшения (1-2 недели)

1. **Неделя 1:**

   - Внедрение Circuit Breaker
   - Оптимизация кэширования с Caffeine
   - Добавление расширенных метрик

2. **Неделя 2:**
   - Улучшение обработки ошибок
   - Настройка мониторинга
   - Тестирование изменений

### Этап 2: Производительность (2-3 недели)

1. **Неделя 3:**

   - Внедрение batch обработки
   - Оптимизация WebSocket
   - Профилирование производительности

2. **Неделя 4:**

   - Асинхронная обработка уведомлений
   - Оптимизация пулов потоков
   - Нагрузочное тестирование

3. **Неделя 5:**
   - Финальная оптимизация
   - Документирование изменений
   - Подготовка к следующему этапу

### Этап 3: Надежность (2-3 недели)

1. **Неделя 6:**

   - Внедрение Health Checks
   - Настройка Distributed Tracing
   - Улучшение логирования

2. **Неделя 7:**

   - Настройка алертинга
   - Автоматическое восстановление
   - Мониторинг в продакшене

3. **Неделя 8:**
   - Тестирование надежности
   - Документирование процедур
   - Обучение команды

### Этап 4: Масштабирование (3-4 недели)

1. **Недели 9-10:**

   - Внедрение Event-driven архитектуры
   - Разделение на микросервисы
   - Настройка API Gateway

2. **Недели 11-12:**
   - Kubernetes развертывание
   - Service Mesh (Istio)
   - Автоматическое масштабирование

## Заключение

Данное руководство предоставляет детальный план рефакторинга и оптимизации Investment Data Scanner Service. Следование предложенным рекомендациям позволит значительно улучшить производительность, надежность и масштабируемость системы.

**Ключевые принципы:**

- **Поэтапное внедрение**: Небольшие, управляемые изменения
- **Тестирование**: Обязательное тестирование каждого изменения
- **Мониторинг**: Постоянное отслеживание метрик
- **Документирование**: Ведение документации по изменениям

**Ожидаемые результаты:**

- Увеличение производительности в 2-3 раза
- Повышение надежности до 99.9%
- Улучшение времени отклика в 2 раза
- Снижение потребления ресурсов на 30%
