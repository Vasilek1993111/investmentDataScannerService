# Circuit Breaker Implementation

## 🚀 Что реализовано

Мы успешно реализовали Circuit Breaker для Investment Data Scanner Service с использованием Resilience4j. Это обеспечивает отказоустойчивость при работе с T-Invest API.

## 📁 Структура файлов

```
src/main/java/com/example/investmentdatascannerservice/
├── config/
│   ├── ResilienceConfig.java              # Конфигурация Circuit Breaker
│   └── CircuitBreakerHealthIndicator.java # Health Check для Actuator
├── service/
│   ├── TInvestApiClient.java              # Клиент с Circuit Breaker
│   ├── CircuitBreakerMonitoringService.java # Мониторинг метрик
│   └── ResilientQuoteScannerService.java  # Отказоустойчивый сервис
├── controller/
│   └── CircuitBreakerController.java      # REST API для мониторинга
└── example/
    └── CircuitBreakerExample.java         # Примеры использования

docs/
└── CIRCUIT_BREAKER.md                     # Подробная документация
```

## 🔧 Основные компоненты

### 1. ResilienceConfig

- Конфигурация Circuit Breaker, Retry и TimeLimiter
- Настройки для T-Invest API
- Bean definitions для Spring

### 2. TInvestApiClient

- Клиент для работы с T-Invest API
- Интеграция с Circuit Breaker и Retry
- Fallback механизмы

### 3. CircuitBreakerMonitoringService

- Мониторинг состояния Circuit Breaker
- Метрики для Prometheus
- Event listeners

### 4. CircuitBreakerController

- REST API для мониторинга
- Управление состоянием
- Получение статистики

## 🚀 Быстрый старт

### 1. Запуск приложения

```bash
mvn spring-boot:run
```

### 2. Проверка состояния Circuit Breaker

```bash
curl http://localhost:8085/api/circuit-breaker/stats
```

### 3. Проверка здоровья

```bash
curl http://localhost:8085/actuator/health
```

### 4. Prometheus метрики

```bash
curl http://localhost:8085/actuator/prometheus | grep circuit_breaker
```

## 📊 API Endpoints

| Endpoint                                  | Метод | Описание                   |
| ----------------------------------------- | ----- | -------------------------- |
| `/api/circuit-breaker/stats`              | GET   | Статистика Circuit Breaker |
| `/api/circuit-breaker/health`             | GET   | Проверка здоровья          |
| `/api/circuit-breaker/detailed`           | GET   | Детальная информация       |
| `/api/circuit-breaker/reset`              | POST  | Сброс состояния            |
| `/api/circuit-breaker/transition/{state}` | POST  | Переход в состояние        |
| `/api/circuit-breaker/config`             | GET   | Конфигурация               |

## 🔍 Мониторинг

### Prometheus метрики

```prometheus
# Состояние Circuit Breaker
circuit_breaker_state_current{name="tinvest-api"} 0

# Метрики
circuit_breaker_metrics_failure_rate{name="tinvest-api"} 0.0
circuit_breaker_metrics_success_rate{name="tinvest-api"} 1.0
circuit_breaker_metrics_number_of_calls{name="tinvest-api"} 150.0

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
      }
    ]
  }
}
```

## ⚙️ Конфигурация

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

## 🧪 Тестирование

### Unit тесты

```bash
mvn test -Dtest=CircuitBreakerTest
```

### Integration тесты

```bash
mvn test -Dtest=*CircuitBreaker*Test
```

### Примеры использования

```java
@Autowired
private CircuitBreakerExample circuitBreakerExample;

// Базовое использование
String result = circuitBreakerExample.basicExample();

// С retry
String retryResult = circuitBreakerExample.withRetryExample();

// Асинхронно
CompletableFuture<String> asyncResult = circuitBreakerExample.asyncExample();

// Проверка состояния
circuitBreakerExample.checkCircuitBreakerState();
```

## 🔄 Состояния Circuit Breaker

### 1. CLOSED (Закрыт)

- Нормальная работа
- Все запросы проходят через внешний сервис
- Счетчик ошибок = 0

### 2. OPEN (Открыт)

- Сервис недоступен
- Все запросы блокируются
- Счетчик ошибок > порога (50%)

### 3. HALF_OPEN (Полуоткрыт)

- Тестирование восстановления
- Ограниченное количество запросов (3)
- Если успешно → CLOSED
- Если ошибка → OPEN

## 🛡️ Fallback стратегии

### 1. Кэшированные данные

```java
private List<LastPrice> getCachedLastPrices(List<String> figis) {
    return figis.stream()
            .map(cacheService::getLastPrice)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
```

### 2. Статические данные

```java
private List<LastPrice> getStaticData(List<String> figis) {
    return staticDataService.getLastKnownPrices(figis);
}
```

### 3. Альтернативные источники

```java
private List<LastPrice> getAlternativeData(List<String> figis) {
    return alternativeApiClient.getLastPrices(figis);
}
```

## 📈 Преимущества

### 1. Производительность

- **Без Circuit Breaker**: 1000 запросов × 30 сек = 8+ часов блокировки
- **С Circuit Breaker**: 1000 запросов × 0.001 сек = 1 секунда

### 2. Надежность

- Система продолжает работать при недоступности внешних сервисов
- Автоматическое восстановление при восстановлении сервиса

### 3. Мониторинг

- Детальные метрики работы Circuit Breaker
- Интеграция с Prometheus и Grafana
- Алертинг при проблемах

## 🚨 Алертинг

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
```

## 🔧 Troubleshooting

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

## 📚 Дополнительные ресурсы

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)

## 🎯 Следующие шаги

1. **Интеграция с реальным T-Invest API**
2. **Настройка алертинга в Grafana**
3. **Добавление дополнительных fallback стратегий**
4. **Оптимизация параметров Circuit Breaker**
5. **Добавление тестов производительности**

---

**Circuit Breaker успешно реализован и готов к использованию!** 🎉
