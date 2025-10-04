# Конфигурация портов для разных окружений

## Обзор

Приложение настроено для работы на разных портах в зависимости от окружения:

- **Продакшн (prod)**: порт **8085**
- **Тест (test)**: порт **8088**

## Конфигурация портов

### 1. Файлы конфигурации

- `application-prod.properties`: `server.port=8085`
- `application-test.properties`: `server.port=8088`
- `application.properties`: `server.port=8088` (по умолчанию для разработки)

### 2. Автоматическое определение порта в сканерах

Все HTML сканеры автоматически определяют порт WebSocket на основе текущего URL:

```javascript
function getWebSocketPort() {
  const currentPort = window.location.port;
  if (currentPort === "8088") {
    return "8088"; // Тестовое окружение
  } else if (currentPort === "8085") {
    return "8085"; // Продакшн окружение
  } else {
    return "8088"; // По умолчанию тестовый порт
  }
}
```

## Запуск приложения

### Тестовое окружение (порт 8088)

```bash
# Через Maven
mvn spring-boot:run -Dspring-boot.run.profiles=test

# Через JAR
java -jar target/investment-data-scanner-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=test

# Через VS Code (конфигурация "🚀 TEST - Spring Boot")
```

**Доступ к сканерам:**

- http://localhost:8088/quote-scanner.html
- http://localhost:8088/weekend-scanner.html
- http://localhost:8088/morning-session-scanner.html
- http://localhost:8088/pair-scanner.html

### Продакшн окружение (порт 8085)

```bash
# Через Maven
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Через JAR
java -jar target/investment-data-scanner-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Через VS Code (конфигурация "🏭 PROD - Spring Boot")
```

**Доступ к сканерам:**

- http://localhost:8085/quote-scanner.html
- http://localhost:8085/weekend-scanner.html
- http://localhost:8085/morning-session-scanner.html
- http://localhost:8085/pair-scanner.html

### Docker (продакшн)

```bash
docker-compose up -d
```

Приложение будет доступно на порту 8085.

## WebSocket подключения

Сканеры автоматически подключаются к правильному WebSocket порту:

- **Тест**: `ws://localhost:8088/ws/quotes`, `ws://localhost:8088/ws/pairs`
- **Продакшн**: `ws://localhost:8085/ws/quotes`, `ws://localhost:8085/ws/pairs`

## Проверка работы

1. Запустите приложение в тестовом режиме
2. Откройте http://localhost:8088/quote-scanner.html
3. Нажмите "Подключиться" - должно подключиться к WebSocket на порту 8088
4. Запустите приложение в продакшн режиме
5. Откройте http://localhost:8085/quote-scanner.html
6. Нажмите "Подключиться" - должно подключиться к WebSocket на порту 8085

## Переменные окружения

### Тестовое окружение

```bash
export T_INVEST_TEST_TOKEN="your_test_token"
export SPRING_DATASOURCE_TEST_URL="jdbc:postgresql://localhost:5434/postgres"
export SPRING_DATASOURCE_TEST_USERNAME="postgres"
export SPRING_DATASOURCE_TEST_PASSWORD="123password123"
```

### Продакшн окружение

```bash
export T_INVEST_PROD_TOKEN="your_prod_token"
export SPRING_DATASOURCE_PROD_URL="jdbc:postgresql://45.132.176.136:5432/postgres"
export SPRING_DATASOURCE_PROD_USERNAME="postgres"
export SPRING_DATASOURCE_PROD_PASSWORD="your_prod_password"
```

## Устранение неполадок

### Проблема: WebSocket подключение не работает

1. Проверьте, что приложение запущено на правильном порту
2. Убедитесь, что в консоли браузера нет ошибок CORS
3. Проверьте, что WebSocket endpoint доступен: `http://localhost:PORT/ws/quotes`

### Проблема: Сканер подключается к неправильному порту

1. Убедитесь, что вы открыли сканер по правильному URL
2. Проверьте, что функция `getWebSocketPort()` работает корректно
3. Очистите кэш браузера

### Проблема: Порт уже используется

```bash
# Проверьте, какие процессы используют порты
netstat -ano | findstr :8085
netstat -ano | findstr :8088

# Остановите процесс, если необходимо
taskkill /PID <PID> /F
```
