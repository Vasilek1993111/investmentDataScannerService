# Руководство по развертыванию

## Обзор

Данное руководство описывает различные способы развертывания Investment Data Scanner Service в различных средах.

## Предварительные требования

### Системные требования

| Компонент | Минимальные требования | Рекомендуемые требования |
| --------- | ---------------------- | ------------------------ |
| **CPU**   | 2 ядра                 | 4+ ядер                  |
| **RAM**   | 2 GB                   | 8+ GB                    |
| **Диск**  | 10 GB                  | 50+ GB SSD               |
| **Сеть**  | 100 Mbps               | 1+ Gbps                  |

### Программное обеспечение

- **Java**: 21+
- **Maven**: 3.6+
- **PostgreSQL**: 12+
- **Docker**: 20.10+ (опционально)
- **Kubernetes**: 1.20+ (опционально)

### Внешние зависимости

- **T-Invest API**: Токен доступа
- **PostgreSQL**: База данных
- **Мониторинг**: Prometheus/Grafana (опционально)

## Локальное развертывание

### 1. Подготовка окружения

#### Установка Java 21

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jdk

# CentOS/RHEL
sudo yum install java-21-openjdk-devel

# macOS (с Homebrew)
brew install openjdk@21

# Windows
# Скачайте с https://adoptium.net/
```

#### Установка Maven

```bash
# Ubuntu/Debian
sudo apt install maven

# CentOS/RHEL
sudo yum install maven

# macOS (с Homebrew)
brew install maven

# Windows
# Скачайте с https://maven.apache.org/download.cgi
```

#### Установка PostgreSQL

```bash
# Ubuntu/Debian
sudo apt install postgresql postgresql-contrib

# CentOS/RHEL
sudo yum install postgresql-server postgresql-contrib

# macOS (с Homebrew)
brew install postgresql

# Windows
# Скачайте с https://www.postgresql.org/download/windows/
```

### 2. Настройка базы данных

```sql
-- Создание пользователя и базы данных
sudo -u postgres psql

CREATE USER investment_user WITH PASSWORD 'secure_password';
CREATE DATABASE investment_db OWNER investment_user;
GRANT ALL PRIVILEGES ON DATABASE investment_db TO investment_user;

-- Создание схемы
\c investment_db
CREATE SCHEMA IF NOT EXISTS invest;
GRANT ALL ON SCHEMA invest TO investment_user;

-- Создание таблиц
\i database/schema.sql
```

### 3. Конфигурация приложения

#### Создание .env файла

```bash
# Создайте файл .env в корне проекта
cat > .env << EOF
TINKOFF_API_TOKEN=your_token_here
DB_URL=jdbc:postgresql://localhost:5432/investment_db
DB_USERNAME=investment_user
DB_PASSWORD=secure_password
SERVER_PORT=8085
APP_TIMEZONE=Europe/Moscow
EOF
```

#### Настройка application.properties

```properties
# Основные настройки
spring.application.name=investment-data-scanner-service
server.port=${SERVER_PORT:8085}

# T-Invest API
tinkoff.api.token=${TINKOFF_API_TOKEN}

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

# Logging
logging.level.com.example.investmentdatascannerservice=INFO
logging.file.name=logs/investment-scanner.log
```

### 4. Сборка и запуск

```bash
# Клонирование репозитория
git clone <repository-url>
cd InvestmentDataScannerService

# Сборка проекта
mvn clean package -DskipTests

# Запуск приложения
java -jar target/investment-data-scanner-service-0.0.1-SNAPSHOT.jar

# Или через Maven
mvn spring-boot:run
```

### 5. Проверка работы

```bash
# Проверка статуса
curl http://localhost:8085/api/scanner/stats

# Проверка WebSocket
wscat -c ws://localhost:8085/ws/quotes
```

## Docker развертывание

### 1. Создание Dockerfile

```dockerfile
# Dockerfile
FROM openjdk:21-jdk-slim

# Установка необходимых пакетов
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Создание пользователя для безопасности
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Установка рабочей директории
WORKDIR /app

# Копирование JAR файла
COPY target/investment-data-scanner-service-*.jar app.jar

# Изменение владельца файлов
RUN chown -R appuser:appuser /app
USER appuser

# Настройка JVM
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Открытие порта
EXPOSE 8085

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8085/actuator/health || exit 1

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 2. Создание docker-compose.yml

```yaml
version: "3.8"

services:
  postgres:
    image: postgres:15
    container_name: investment-postgres
    environment:
      POSTGRES_DB: investment_db
      POSTGRES_USER: investment_user
      POSTGRES_PASSWORD: secure_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./database/schema.sql:/docker-entrypoint-initdb.d/schema.sql
    networks:
      - investment-network

  investment-scanner:
    build: .
    container_name: investment-scanner
    environment:
      TINKOFF_API_TOKEN: ${TINKOFF_API_TOKEN}
      DB_URL: jdbc:postgresql://postgres:5432/investment_db
      DB_USERNAME: investment_user
      DB_PASSWORD: secure_password
      SERVER_PORT: 8085
      APP_TIMEZONE: Europe/Moscow
    ports:
      - "8085:8085"
    depends_on:
      - postgres
    networks:
      - investment-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8085/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

volumes:
  postgres_data:

networks:
  investment-network:
    driver: bridge
```

### 3. Сборка и запуск

```bash
# Сборка образа
docker build -t investment-scanner:latest .

# Запуск с docker-compose
docker-compose up -d

# Просмотр логов
docker-compose logs -f investment-scanner

# Остановка
docker-compose down
```

### 4. Управление контейнерами

```bash
# Просмотр статуса
docker-compose ps

# Перезапуск сервиса
docker-compose restart investment-scanner

# Обновление образа
docker-compose pull
docker-compose up -d

# Очистка
docker-compose down -v
docker system prune -a
```

## Kubernetes развертывание

### 1. Создание Namespace

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: investment-scanner
  labels:
    name: investment-scanner
```

### 2. Создание ConfigMap

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: investment-scanner-config
  namespace: investment-scanner
data:
  application.properties: |
    spring.application.name=investment-data-scanner-service
    server.port=8085

    # Database
    spring.datasource.url=jdbc:postgresql://postgres-service:5432/investment_db
    spring.datasource.username=investment_user
    spring.datasource.password=secure_password
    spring.datasource.driver-class-name=org.postgresql.Driver

    # JPA/Hibernate
    spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
    spring.jpa.hibernate.ddl-auto=none
    spring.jpa.show-sql=false

    # Performance
    spring.datasource.hikari.maximum-pool-size=20
    spring.datasource.hikari.connection-init-sql=SET search_path TO invest, public;

    # Logging
    logging.level.com.example.investmentdatascannerservice=INFO
    logging.file.name=/app/logs/investment-scanner.log
```

### 3. Создание Secret

```yaml
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: investment-scanner-secret
  namespace: investment-scanner
type: Opaque
data:
  tinkoff-api-token: <base64-encoded-token>
  db-password: <base64-encoded-password>
```

### 4. Создание Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: investment-scanner
  namespace: investment-scanner
  labels:
    app: investment-scanner
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
        - name: investment-scanner
          image: investment-scanner:latest
          ports:
            - containerPort: 8085
          env:
            - name: TINKOFF_API_TOKEN
              valueFrom:
                secretKeyRef:
                  name: investment-scanner-secret
                  key: tinkoff-api-token
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: investment-scanner-secret
                  key: db-password
            - name: DB_URL
              value: "jdbc:postgresql://postgres-service:5432/investment_db"
            - name: DB_USERNAME
              value: "investment_user"
            - name: SERVER_PORT
              value: "8085"
            - name: APP_TIMEZONE
              value: "Europe/Moscow"
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
            timeoutSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8085
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          volumeMounts:
            - name: config-volume
              mountPath: /app/config
            - name: logs-volume
              mountPath: /app/logs
      volumes:
        - name: config-volume
          configMap:
            name: investment-scanner-config
        - name: logs-volume
          emptyDir: {}
```

### 5. Создание Service

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: investment-scanner-service
  namespace: investment-scanner
  labels:
    app: investment-scanner
spec:
  selector:
    app: investment-scanner
  ports:
    - name: http
      port: 8085
      targetPort: 8085
      protocol: TCP
  type: ClusterIP
```

### 6. Создание Ingress

```yaml
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: investment-scanner-ingress
  namespace: investment-scanner
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "false"
    nginx.ingress.kubernetes.io/websocket-services: "investment-scanner-service"
spec:
  rules:
    - host: investment-scanner.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: investment-scanner-service
                port:
                  number: 8085
```

### 7. Развертывание

```bash
# Применение манифестов
kubectl apply -f namespace.yaml
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml

# Проверка статуса
kubectl get pods -n investment-scanner
kubectl get services -n investment-scanner
kubectl get ingress -n investment-scanner

# Просмотр логов
kubectl logs -f deployment/investment-scanner -n investment-scanner

# Масштабирование
kubectl scale deployment investment-scanner --replicas=5 -n investment-scanner
```

## Helm развертывание

### 1. Создание Helm Chart

```bash
# Создание chart
helm create investment-scanner

# Структура chart
investment-scanner/
├── Chart.yaml
├── values.yaml
├── templates/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   └── secret.yaml
└── charts/
```

### 2. Настройка values.yaml

```yaml
# values.yaml
replicaCount: 3

image:
  repository: investment-scanner
  tag: latest
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 8085

ingress:
  enabled: true
  className: nginx
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/websocket-services: "{{ .Release.Name }}-service"
  hosts:
    - host: investment-scanner.local
      paths:
        - path: /
          pathType: Prefix
  tls: []

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80

nodeSelector: {}

tolerations: []

affinity: {}

config:
  tinkoffApiToken: ""
  dbUrl: "jdbc:postgresql://postgres:5432/investment_db"
  dbUsername: "investment_user"
  dbPassword: ""
  serverPort: 8085
  appTimezone: "Europe/Moscow"
```

### 3. Развертывание с Helm

```bash
# Установка chart
helm install investment-scanner ./investment-scanner \
  --namespace investment-scanner \
  --create-namespace \
  --set config.tinkoffApiToken=your_token_here \
  --set config.dbPassword=secure_password

# Обновление chart
helm upgrade investment-scanner ./investment-scanner \
  --namespace investment-scanner \
  --set image.tag=v1.1.0

# Удаление chart
helm uninstall investment-scanner --namespace investment-scanner
```

## Мониторинг и логирование

### 1. Prometheus метрики

```yaml
# prometheus-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
    scrape_configs:
    - job_name: 'investment-scanner'
      static_configs:
      - targets: ['investment-scanner-service:8085']
      metrics_path: '/actuator/prometheus'
      scrape_interval: 5s
```

### 2. Grafana дашборд

```json
{
  "dashboard": {
    "title": "Investment Scanner Dashboard",
    "panels": [
      {
        "title": "Quotes Processed",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(market_data_processed_total[5m])",
            "legendFormat": "Quotes/sec"
          }
        ]
      },
      {
        "title": "WebSocket Connections",
        "type": "singlestat",
        "targets": [
          {
            "expr": "notifications_subscribers",
            "legendFormat": "Active Connections"
          }
        ]
      }
    ]
  }
}
```

### 3. ELK Stack для логов

```yaml
# elasticsearch.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: elasticsearch
spec:
  replicas: 1
  selector:
    matchLabels:
      app: elasticsearch
  template:
    metadata:
      labels:
        app: elasticsearch
    spec:
      containers:
        - name: elasticsearch
          image: elasticsearch:7.15.0
          ports:
            - containerPort: 9200
          env:
            - name: discovery.type
              value: single-node
          resources:
            limits:
              memory: 2Gi
            requests:
              memory: 1Gi
```

## Безопасность

### 1. Network Policies

```yaml
# network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: investment-scanner-network-policy
  namespace: investment-scanner
spec:
  podSelector:
    matchLabels:
      app: investment-scanner
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - protocol: TCP
          port: 8085
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              name: postgres
      ports:
        - protocol: TCP
          port: 5432
```

### 2. Pod Security Policy

```yaml
# psp.yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: investment-scanner-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - "configMap"
    - "emptyDir"
    - "projected"
    - "secret"
    - "downwardAPI"
    - "persistentVolumeClaim"
  runAsUser:
    rule: "MustRunAsNonRoot"
  seLinux:
    rule: "RunAsAny"
  fsGroup:
    rule: "RunAsAny"
```

## Troubleshooting

### 1. Общие проблемы

#### Приложение не запускается

```bash
# Проверка логов
kubectl logs -f deployment/investment-scanner -n investment-scanner

# Проверка конфигурации
kubectl describe pod <pod-name> -n investment-scanner

# Проверка переменных окружения
kubectl exec -it <pod-name> -n investment-scanner -- env
```

#### Проблемы с базой данных

```bash
# Проверка подключения к БД
kubectl exec -it <pod-name> -n investment-scanner -- \
  curl -f http://localhost:8085/actuator/health

# Проверка логов БД
kubectl logs -f deployment/postgres -n postgres
```

#### Проблемы с WebSocket

```bash
# Проверка WebSocket соединения
wscat -c ws://investment-scanner.local/ws/quotes

# Проверка nginx конфигурации
kubectl describe ingress investment-scanner-ingress -n investment-scanner
```

### 2. Мониторинг производительности

```bash
# Проверка использования ресурсов
kubectl top pods -n investment-scanner

# Проверка метрик
curl http://investment-scanner.local/actuator/prometheus

# Проверка health check
curl http://investment-scanner.local/actuator/health
```

### 3. Логирование

```bash
# Просмотр логов приложения
kubectl logs -f deployment/investment-scanner -n investment-scanner

# Просмотр логов с фильтрацией
kubectl logs -f deployment/investment-scanner -n investment-scanner | grep ERROR

# Экспорт логов
kubectl logs deployment/investment-scanner -n investment-scanner > logs.txt
```

## Резервное копирование

### 1. Backup базы данных

```bash
# Создание backup
kubectl exec -it postgres-0 -n postgres -- \
  pg_dump -U investment_user investment_db > backup.sql

# Восстановление из backup
kubectl exec -i postgres-0 -n postgres -- \
  psql -U investment_user investment_db < backup.sql
```

### 2. Backup конфигурации

```bash
# Экспорт конфигурации
kubectl get configmap investment-scanner-config -n investment-scanner -o yaml > config-backup.yaml

# Экспорт секретов
kubectl get secret investment-scanner-secret -n investment-scanner -o yaml > secret-backup.yaml
```

## Обновление и откат

### 1. Обновление приложения

```bash
# Обновление образа
kubectl set image deployment/investment-scanner \
  investment-scanner=investment-scanner:v1.1.0 \
  -n investment-scanner

# Проверка статуса обновления
kubectl rollout status deployment/investment-scanner -n investment-scanner
```

### 2. Откат к предыдущей версии

```bash
# Просмотр истории
kubectl rollout history deployment/investment-scanner -n investment-scanner

# Откат к предыдущей версии
kubectl rollout undo deployment/investment-scanner -n investment-scanner

# Откат к конкретной версии
kubectl rollout undo deployment/investment-scanner --to-revision=2 -n investment-scanner
```

## Заключение

Данное руководство покрывает основные сценарии развертывания Investment Data Scanner Service. Выберите подходящий метод развертывания в зависимости от ваших требований и инфраструктуры.

Для получения дополнительной помощи обращайтесь к команде разработки или создавайте Issues в репозитории проекта.
