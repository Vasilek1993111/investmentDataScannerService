############################################
# Stage 1: Build (with Maven)
############################################
FROM eclipse-temurin:21-jdk-jammy AS builder

# Устанавливаем Maven
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    maven \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Копируем pom.xml для кэширования зависимостей
COPY pom.xml ./

# Прогреваем зависимости
RUN mvn -B -ntp dependency:go-offline

# Копируем исходный код и собираем
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

############################################
# Stage 2: Runtime (JRE only, tuned for low latency)
############################################
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Устанавливаем необходимые пакеты (tzdata, ca-certificates, curl для healthcheck)
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    tzdata ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

# Создаем непривилегированного пользователя
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Копируем собранный JAR из builder-стейджа
COPY --from=builder /build/target/investment-data-scanner-service-0.0.1-SNAPSHOT.jar /app/app.jar

# Права
RUN chown -R appuser:appuser /app
USER appuser

# Открываем порт
EXPOSE 8085

# Переменные окружения по умолчанию
ENV SERVER_PORT=8085 \
    APP_TIMEZONE=Europe/Moscow \
    TZ=Europe/Moscow \
    LANG=C.UTF-8 \
    MALLOC_ARENA_MAX=2 \
    # Низкая задержка GC и предсказуемые паузы; процентная настройка heap под контейнер
    JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxGCPauseMillis=5 -XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=75 -XX:MinRAMPercentage=25 -Djava.security.egd=file:/dev/urandom -Dsun.net.inetaddr.ttl=60 -Dnetworkaddress.cache.ttl=60 -XX:+AlwaysActAsServerClassMachine -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:+AlwaysPreTouch"

# Запуск приложения
CMD ["java", "-jar", "/app/app.jar"]
