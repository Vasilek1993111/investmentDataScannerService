# Circuit Breaker Implementation

## Обзор

Circuit Breaker (Предохранитель) — это паттерн проектирования, который защищает приложение от каскадных сбоев при работе с внешними сервисами. В Investment Data Scanner Service Circuit Breaker реализован для защиты от недоступности T-Invest API.

## Архитектура

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   T-Invest API  │───▶│ Circuit Breaker │───▶│ Quote Scanner   │
│   (External)    │    │   (Protection)  │    │   (Internal)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │ Fallback Data   │
                       │ (Cached)        │
                       └─────────────────┘
```

## Состояния Circuit Breaker

### 1. CLOSED (Закрыт) - Нормальная работа

- Все запросы проходят через внешний сервис
- Счетчик ошибок = 0
- Время последней ошибки = null

### 2. OPEN (Открыт) - Сервис недоступен

- Все запросы блокируются
- Счетчик ошибок > порога (50%)
- Время последней ошибки = текущее время

### 3. HALF_OPEN (Полуоткрыт) - Тестирование восстановления

- Ограниченное количество запросов (3) для проверки
- Если успешно → CLOSED
- Если ошибка → OPEN

## Конфигурация

### application.properties

```properties
# Resilience4j Configuration
resilience4j.circuitbreaker.instances.tinvest-api.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.tinvest-api.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.tinvest-api.sliding-window-size=10
resilience4j.circuitbreaker.instances.tinvest-api.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.tinvest-api.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.tinvest-api.automatic-transition-from-open-to-half-open-enabled=true
resilience4j.circuitbreaker.instances.tinvest-api.slow-call-rate-threshold=50
resilience4j.circuitbreaker.instances.tinvest-api.slow-call-duration-threshold=5s

# Retry Configuration
resilience4j.retry.instances.tinvest-api.max-attempts=3
resilience4j.retry.instances.tinvest-api.wait-duration=1s
resilience4j.retry.instances.tinvest-api.retry-exceptions=java.net.ConnectException,java.net.SocketTimeoutException,java.util.concurrent.TimeoutException

# TimeLimiter Configuration
resilience4j.timelimiter.instances.tinvest-api.timeout-duration=10s
resilience4j.timelimiter.instances.tinvest-api.cancel-running-future=true
```

### Java Configuration

```java
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerConfig tinvestApiCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)           // 50% ошибок
                .waitDurationInOpenState(Duration.ofSeconds(30))  // 30 сек в OPEN
                .slidingWindowSize(10)              // 10 последних запросов
                .minimumNumberOfCalls(5)            // Минимум 5 вызовов
                .permittedNumberOfCallsInHalfOpenState(3)  // 3 запроса в HALF_OPEN
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .slowCallRateThreshold(50)          // 50% медленных вызовов
                .slowCallDurationThreshold(Duration.ofSeconds(5))  // > 5 сек
                .build();
    }
}
```

## Использование

### Базовое использование

```java
@Service
public class TInvestApiClient {

    private final CircuitBreaker circuitBreaker;

    public List<LastPrice> getLastPrices(List<String> figis) {
        return Try.ofSupplier(
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
                    return investApi.getMarketDataService().getLastPrices(figis);
                })
        )
        .recover(CallNotPermittedException.class, ex -> {
            log.warn("Circuit breaker is OPEN, using cached data");
            return getCachedLastPrices(figis);
        })
        .recover(TimeoutException.class, ex -> {
            log.warn("Timeout occurred, using cached data");
            return getCachedLastPrices(figis);
        })
        .get();
    }
}
```

### С Retry

```java
public List<LastPrice> getLastPricesWithRetry(List<String> figis) {
    return Try.ofSupplier(
            CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    Retry.decorateSupplier(retry, () -> {
                        return investApi.getMarketDataService().getLastPrices(figis);
                    }).get()
            )
    )
    .recover(CallNotPermittedException.class, ex -> getCachedLastPrices(figis))
    .recover(TimeoutException.class, ex -> getCachedLastPrices(figis))
    .get();
}
```

## API Endpoints

### Мониторинг Circuit Breaker

#### GET /api/circuit-breaker/stats

Получение статистики Circuit Breaker

**Ответ:**

```json
{
  "state": "CLOSED",
  "failureRate": 0.0,
  "successRate": 1.0,
  "numberOfCalls": 150,
  "numberOfSuccessfulCalls": 150,
  "numberOfFailedCalls": 0,
  "numberOfNotPermittedCalls": 0,
  "averageResponseTime": 250.5
}
```

#### GET /api/circuit-breaker/health

Проверка здоровья Circuit Breaker

**Ответ:**

```json
{
  "isHealthy": true,
  "timestamp": 1642234567890
}
```

#### GET /api/circuit-breaker/detailed

Детальная информация о Circuit Breaker

**Ответ:**

```json
{
  "state": "CLOSED",
  "failureRate": 0.0,
  "successRate": 1.0,
  "numberOfCalls": 150,
  "numberOfSuccessfulCalls": 150,
  "numberOfFailedCalls": 0,
  "numberOfNotPermittedCalls": 0,
  "averageResponseTime": 250.5,
  "isHealthy": true,
  "timestamp": 1642234567890
}
```

#### POST /api/circuit-breaker/reset

Сброс состояния Circuit Breaker

**Ответ:**

```json
{
  "message": "Circuit breaker reset successfully",
  "state": "CLOSED",
  "timestamp": 1642234567890
}
```

#### POST /api/circuit-breaker/transition/{state}

Принудительный переход в определенное состояние

**Параметры:**

- `state`: OPEN, CLOSED, HALF_OPEN

**Ответ:**

```json
{
  "message": "Circuit breaker transitioned to OPEN state",
  "state": "OPEN",
  "timestamp": 1642234567890
}
```

#### GET /api/circuit-breaker/config

Получение конфигурации Circuit Breaker

**Ответ:**

```json
{
  "failureRateThreshold": 50.0,
  "waitDurationInOpenState": "PT30S",
  "slidingWindowSize": 10,
  "minimumNumberOfCalls": 5,
  "permittedNumberOfCallsInHalfOpenState": 3,
  "automaticTransitionFromOpenToHalfOpenEnabled": true,
  "slowCallRateThreshold": 50.0,
  "slowCallDurationThreshold": "PT5S"
}
```

## Мониторинг

### Prometheus метрики

```prometheus
# Состояние Circuit Breaker
circuit_breaker_state_current{name="tinvest-api"} 0

# Метрики Circuit Breaker
circuit_breaker_metrics_failure_rate{name="tinvest-api"} 0.0
circuit_breaker_metrics_success_rate{name="tinvest-api"} 1.0
circuit_breaker_metrics_number_of_calls{name="tinvest-api"} 150.0
circuit_breaker_metrics_number_of_successful_calls{name="tinvest-api"} 150.0
circuit_breaker_metrics_number_of_failed_calls{name="tinvest-api"} 0.0

# Счетчики состояний
circuit_breaker_state_open_total{name="tinvest-api"} 0
circuit_breaker_state_closed_total{name="tinvest-api"} 1
circuit_breaker_state_half_open_total{name="tinvest-api"} 0

# Счетчики вызовов
circuit_breaker_calls_not_permitted_total{name="tinvest-api"} 0
circuit_breaker_calls_success_total{name="tinvest-api"} 150
circuit_breaker_calls_failure_total{name="tinvest-api"} 0
```

### Grafana Dashboard

```json
{
  "dashboard": {
    "title": "Circuit Breaker Dashboard",
    "panels": [
      {
        "title": "Circuit Breaker State",
        "type": "stat",
        "targets": [
          {
            "expr": "circuit_breaker_state_current{name=\"tinvest-api\"}",
            "legendFormat": "State"
          }
        ]
      },
      {
        "title": "Failure Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "circuit_breaker_metrics_failure_rate{name=\"tinvest-api\"}",
            "legendFormat": "Failure Rate"
          }
        ]
      },
      {
        "title": "Calls per Second",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(circuit_breaker_calls_success_total{name=\"tinvest-api\"}[5m])",
            "legendFormat": "Successful Calls/sec"
          },
          {
            "expr": "rate(circuit_breaker_calls_failure_total{name=\"tinvest-api\"}[5m])",
            "legendFormat": "Failed Calls/sec"
          }
        ]
      }
    ]
  }
}
```

## Алертинг

### AlertManager правила

```yaml
groups:
  - name: circuit-breaker
    rules:
      - alert: CircuitBreakerOpen
        expr: circuit_breaker_state_current{name="tinvest-api"} == 1
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "T-Invest API Circuit Breaker is OPEN"
          description: "External API is unavailable, using fallback data"

      - alert: CircuitBreakerHighFailureRate
        expr: circuit_breaker_metrics_failure_rate{name="tinvest-api"} > 0.3
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High failure rate in T-Invest API"
          description: "Failure rate is {{ $value }}%"

      - alert: CircuitBreakerSlowCalls
        expr: rate(circuit_breaker_calls_success_total{name="tinvest-api"}[5m]) < 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Very low call rate to T-Invest API"
          description: "Only {{ $value }} calls per second"
```

## Fallback стратегии

### 1. Кэшированные данные

```java
private List<LastPrice> getCachedLastPrices(List<String> figis) {
    return figis.stream()
            .map(cacheService::getLastPrice)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
```

### 2. Альтернативные источники

```java
private List<LastPrice> getAlternativeData(List<String> figis) {
    // Использование альтернативных API или источников данных
    return alternativeApiClient.getLastPrices(figis);
}
```

### 3. Статические данные

```java
private List<LastPrice> getStaticData(List<String> figis) {
    // Возврат последних известных значений
    return staticDataService.getLastKnownPrices(figis);
}
```

## Тестирование

### Unit тесты

```java
@Test
void testCircuitBreakerOpenState() {
    // Given
    when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

    // When
    List<LastPrice> result = apiClient.getLastPrices(figis);

    // Then
    assertThat(result).isEqualTo(cachedData);
    verify(cacheService).getLastPrice(any());
}
```

### Integration тесты

```java
@Test
void testCircuitBreakerIntegration() {
    // Simulate API failure
    when(investApi.getMarketDataService().getLastPrices(any()))
            .thenThrow(new RuntimeException("API unavailable"));

    // Circuit breaker should open after failures
    for (int i = 0; i < 10; i++) {
        apiClient.getLastPrices(figis);
    }

    // Should use fallback data
    verify(cacheService, atLeastOnce()).getLastPrice(any());
}
```

## Лучшие практики

### 1. Настройка параметров

- **failureRateThreshold**: 50% для внешних API
- **waitDurationInOpenState**: 30-60 секунд
- **slidingWindowSize**: 10-20 запросов
- **minimumNumberOfCalls**: 5-10 запросов

### 2. Мониторинг

- Отслеживайте состояние Circuit Breaker
- Настройте алерты на открытие
- Мониторьте fallback использование

### 3. Fallback стратегии

- Всегда имейте fallback данные
- Тестируйте fallback сценарии
- Документируйте fallback поведение

### 4. Логирование

```java
log.warn("Circuit breaker is OPEN, using cached data for {} instruments", figis.size());
log.info("Circuit breaker state transition: {} -> {}", fromState, toState);
log.debug("Circuit breaker call successful for FIGI: {}", figi);
```

## Troubleshooting

### Проблема: Circuit Breaker не открывается

**Решение:**

- Проверьте настройки `failureRateThreshold`
- Убедитесь, что `minimumNumberOfCalls` достигнут
- Проверьте логи на наличие исключений

### Проблема: Circuit Breaker не закрывается

**Решение:**

- Проверьте настройки `waitDurationInOpenState`
- Убедитесь, что API восстановился
- Проверьте логи HALF_OPEN состояния

### Проблема: Fallback не работает

**Решение:**

- Проверьте реализацию fallback методов
- Убедитесь, что кэш содержит данные
- Проверьте логи fallback вызовов

## Заключение

Circuit Breaker обеспечивает:

- **Защиту от каскадных сбоев**
- **Быстрое обнаружение проблем**
- **Автоматическое восстановление**
- **Graceful degradation**
- **Мониторинг и алертинг**

Правильная настройка и мониторинг Circuit Breaker критически важны для обеспечения надежности системы.
