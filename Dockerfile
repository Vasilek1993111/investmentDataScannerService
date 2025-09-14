# Используем официальный образ OpenJDK 21
FROM openjdk:21-jdk-slim

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем Maven wrapper и pom.xml для кэширования зависимостей
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY pom.xml .

# Даем права на выполнение Maven wrapper
RUN chmod +x mvnw

# Скачиваем зависимости (этот слой будет кэшироваться если pom.xml не изменился)
RUN ./mvnw dependency:go-offline -B

# Копируем исходный код
COPY src src

# Собираем приложение
RUN ./mvnw clean package -DskipTests

# Создаем пользователя для безопасности
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app
USER appuser

# Открываем порт
EXPOSE 8085

# Устанавливаем переменные окружения по умолчанию
ENV SERVER_PORT=8085
ENV APP_TIMEZONE=Europe/Moscow

# Команда запуска приложения
CMD ["java", "-jar", "target/investment-data-scanner-service-0.0.1-SNAPSHOT.jar"]
