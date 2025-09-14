# Docker Setup для Investment Data Scanner Service

Этот документ описывает, как запустить Investment Data Scanner Service в Docker контейнере с подключением к существующей базе данных.

> **⚠️ ВАЖНО: Безопасность**
>
> - Никогда не коммитьте реальные токены и пароли в репозиторий
> - Используйте файл `.env` для хранения чувствительных данных
> - Убедитесь, что файл `.env` добавлен в `.gitignore`

## Предварительные требования

- Docker
- Docker Compose
- Tinkoff API токен
- **Существующая база данных PostgreSQL на порту 5434** с данными и схемой `invest`

## Быстрый старт

1. **Создайте файл `.env` на основе примера:**

   ```bash
   # Создайте файл .env с примером конфигурации
   cat > .env << 'EOF'
   # Tinkoff API Configuration
   TINKOFF_API_TOKEN=your_actual_tinkoff_token_here

   # Database Configuration
   DB_URL=jdbc:postgresql://host.docker.internal:5434/postgres?currentSchema=invest
   DB_USERNAME=postgres
   DB_PASSWORD=your_db_password_here

   # Application Configuration
   SERVER_PORT=8085
   APP_TIMEZONE=Europe/Moscow
   EOF
   ```

2. **Отредактируйте файл `.env` и укажите ваш Tinkoff API токен и данные БД:**

   ```bash
   # ⚠️ ЗАМЕНИТЕ НА РЕАЛЬНЫЕ ЗНАЧЕНИЯ:
   TINKOFF_API_TOKEN=your_actual_tinkoff_token_here
   DB_URL=jdbc:postgresql://host.docker.internal:5434/postgres?currentSchema=invest
   DB_USERNAME=postgres
   DB_PASSWORD=your_db_password_here
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

- `TINKOFF_API_TOKEN` - токен для Tinkoff API (⚠️ **ОБЯЗАТЕЛЬНО** замените на реальный)
- `DB_URL` - URL подключения к базе данных
- `DB_USERNAME` - имя пользователя БД
- `DB_PASSWORD` - пароль БД (⚠️ **ОБЯЗАТЕЛЬНО** замените на реальный)
- `SERVER_PORT` - порт приложения (по умолчанию 8085)
- `APP_TIMEZONE` - временная зона (по умолчанию Europe/Moscow)

### База данных

Приложение подключается к внешней базе данных PostgreSQL с:

- Схемой `invest`
- Необходимыми таблицами
- Тестовыми данными
- Индексами для оптимизации

**Важно:** Убедитесь, что база данных PostgreSQL запущена на порту 5434 и содержит схему `invest` с необходимыми таблицами.

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
# Если база данных запущена в Docker
docker exec -it postgres_container_name psql -U postgres -d postgres

# Или если подключаетесь к внешней базе данных
psql -h localhost -p 5434 -U postgres -d postgres
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
   export DB_URL=jdbc:postgresql://localhost:5434/postgres?currentSchema=invest
   export DB_USERNAME=postgres
   export DB_PASSWORD=your_db_password_here
   export TINKOFF_API_TOKEN=your_tinkoff_token_here

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

1. Проверьте, что внешняя база данных PostgreSQL запущена на порту 5434:

   ```bash
   # Проверьте, что порт 5434 доступен
   netstat -an | grep 5434

   # Или попробуйте подключиться
   psql -h localhost -p 5434 -U postgres -d postgres
   ```

2. Убедитесь, что в базе данных существует схема `invest`:

   ```sql
   \dn invest
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
│   (Port 8085)       │    │   (Port 5434)       │
│   Docker Container  │    │   External Host     │
└─────────────────────┘    └─────────────────────┘
           │
           ▼
┌─────────────────────┐
│   Tinkoff API       │
│   (External)        │
└─────────────────────┘
```

## Безопасность

### Рекомендации по безопасности

- **Никогда не коммитьте реальные токены и пароли** в репозиторий
- Используйте файл `.env` для хранения чувствительных данных
- Убедитесь, что файл `.env` добавлен в `.gitignore`
- Регулярно обновляйте токены и пароли
- Используйте сильные пароли для базы данных

### Технические меры безопасности

- Приложение запускается под непривилегированным пользователем
- База данных изолирована в отдельной сети
- Чувствительные данные передаются через переменные окружения
- Health checks для мониторинга состояния сервисов
- Circuit breaker для защиты от сбоев внешних сервисов
