# Docker Setup для Investment Data Scanner Service

Этот документ описывает, как запустить Investment Data Scanner Service в Docker контейнере с подключением к существующей базе данных.

## Предварительные требования

- Docker
- Docker Compose
- Tinkoff API токен
- **Существующая база данных PostgreSQL на порту 5434** с данными и схемой

## Быстрый старт

1. **Скопируйте файл с переменными окружения:**

   ```bash
   cp env.example .env
   ```

2. **Отредактируйте файл `.env` и укажите ваш Tinkoff API токен и данные БД:**

   ```bash
   TINKOFF_API_TOKEN=your_actual_tinkoff_token_here
   DB_URL=jdbc:postgresql://localhost:5434/investment_scanner?currentSchema=invest
   DB_USERNAME=your_db_username
   DB_PASSWORD=your_db_password
   ```

3. **Запустите приложение:**

   ```bash
   docker-compose up -d
   ```

4. **Проверьте статус сервисов:**

   ```bash
   docker-compose ps
   ```

5. **Просмотрите логи приложения:**
   ```bash
   docker-compose logs -f investment-scanner
   ```

## Доступ к приложению

- **Основное приложение:** http://localhost:8085
- **Health Check:** http://localhost:8085/actuator/health
- **Metrics:** http://localhost:8085/actuator/metrics
- **Prometheus Metrics:** http://localhost:8085/actuator/prometheus

## WebSocket эндпоинты

- **Котировки:** ws://localhost:8085/ws/quotes
- **Пары инструментов:** ws://localhost:8085/ws/pairs

## Управление контейнерами

### Остановка сервисов

```bash
docker-compose down
```

### Остановка с удалением данных

```bash
docker-compose down -v
```

### Пересборка образа

```bash
docker-compose build --no-cache
```

### Просмотр логов

```bash
# Все сервисы
docker-compose logs -f

# Только приложение
docker-compose logs -f investment-scanner

# Только база данных
docker-compose logs -f postgres
```

## Конфигурация

### Переменные окружения

Основные переменные окружения можно настроить в файле `.env`:

- `TINKOFF_API_TOKEN` - токен для Tinkoff API
- `SERVER_PORT` - порт приложения (по умолчанию 8085)
- `APP_TIMEZONE` - временная зона (по умолчанию Europe/Moscow)

### База данных

База данных PostgreSQL автоматически создается с:

- Схемой `invest`
- Необходимыми таблицами
- Тестовыми данными
- Индексами для оптимизации

### Персистентность данных

Данные базы данных сохраняются в Docker volume `postgres_data`.

## Мониторинг и отладка

### Health Checks

Приложение имеет встроенные health checks:

- Проверка доступности базы данных
- Проверка состояния Circuit Breaker
- Метрики производительности

### Логи

Логи приложения можно просматривать через:

```bash
docker-compose logs -f investment-scanner
```

### Подключение к базе данных

Для подключения к базе данных извне:

```bash
docker exec -it investment-scanner-db psql -U scanner_user -d investment_scanner
```

## Разработка

### Локальная разработка с Docker

1. Запустите только базу данных:

   ```bash
   docker-compose up -d postgres
   ```

2. Запустите приложение локально с подключением к Docker БД:

   ```bash
   # Установите переменные окружения
   export DB_URL=jdbc:postgresql://localhost:5432/investment_scanner?currentSchema=invest
   export DB_USERNAME=scanner_user
   export DB_PASSWORD=scanner_password
   export TINKOFF_API_TOKEN=your_token

   # Запустите приложение
   ./mvnw spring-boot:run
   ```

### Обновление приложения

1. Остановите приложение:

   ```bash
   docker-compose stop investment-scanner
   ```

2. Пересоберите образ:

   ```bash
   docker-compose build investment-scanner
   ```

3. Запустите обновленное приложение:
   ```bash
   docker-compose up -d investment-scanner
   ```

## Устранение неполадок

### Проблемы с подключением к базе данных

1. Проверьте, что база данных запущена:

   ```bash
   docker-compose ps postgres
   ```

2. Проверьте логи базы данных:
   ```bash
   docker-compose logs postgres
   ```

### Проблемы с Tinkoff API

1. Убедитесь, что токен указан правильно в `.env`
2. Проверьте логи приложения на наличие ошибок API

### Проблемы с портами

Если порт 8085 занят, измените его в `docker-compose.yml`:

```yaml
ports:
  - "8086:8085" # Внешний порт:внутренний порт
```

## Архитектура

```
┌─────────────────────┐    ┌─────────────────────┐
│   Investment        │    │   PostgreSQL        │
│   Scanner App       │◄───┤   Database          │
│   (Port 8085)       │    │   (Port 5432)       │
└─────────────────────┘    └─────────────────────┘
           │
           ▼
┌─────────────────────┐
│   Tinkoff API       │
│   (External)        │
└─────────────────────┘
```

## Безопасность

- Приложение запускается под непривилегированным пользователем
- База данных изолирована в отдельной сети
- Чувствительные данные передаются через переменные окружения
- Health checks для мониторинга состояния сервисов
