# Архитектурные диаграммы

## Общая архитектура системы

```mermaid
graph TB
    subgraph "External Systems"
        TInvest[T-Invest API<br/>gRPC Stream]
        Clients[WebSocket Clients<br/>Web Browsers]
        Mobile[Mobile Apps]
    end

    subgraph "Load Balancer"
        LB[Nginx/HAProxy<br/>Load Balancer]
    end

    subgraph "Application Layer"
        subgraph "Presentation Layer"
            QWSC[QuoteWebSocketController]
            PSC[PairWebSocketController]
            SC[ScannerController]
            SSC[StreamingServiceController]
        end

        subgraph "Application Services"
            QSS[QuoteScannerService<br/>Orchestrator]
            MDS[MarketDataStreamingService<br/>gRPC Client]
            MDP[MarketDataProcessor<br/>Data Processing]
            NS[NotificationService<br/>Event Broadcasting]
            QDF[QuoteDataFactory<br/>DTO Creation]
            IPS[InstrumentPairService<br/>Pair Analysis]
        end

        subgraph "Domain Layer"
            QD[QuoteData<br/>Entity]
            IP[InstrumentPair<br/>Entity]
            PCR[PairComparisonResult<br/>Value Object]
        end

        subgraph "Infrastructure Layer"
            ICS[InstrumentCacheService<br/>In-Memory Cache]
            STS[SessionTimeService<br/>Time Management]
            SS[ShareService<br/>Shares Data]
            IS[IndicativeService<br/>Indices Data]
            SADS[SharesAggregatedDataService<br/>Aggregated Data]
            CPS[ClosePriceService<br/>Close Prices]
            CPESS[ClosePriceEveningSessionService<br/>Evening Prices]
        end
    end

    subgraph "Data Layer"
        PG[(PostgreSQL<br/>invest schema)]
        Repos[Repositories<br/>Data Access]
    end

    subgraph "Monitoring"
        Prometheus[Prometheus<br/>Metrics Collection]
        Grafana[Grafana<br/>Dashboards]
        ELK[ELK Stack<br/>Logging]
    end

    %% External connections
    TInvest -->|gRPC Stream| MDS
    Clients -->|WebSocket| QWSC
    Mobile -->|REST API| SC

    %% Load balancer
    LB --> QWSC
    LB --> PSC
    LB --> SC
    LB --> SSC

    %% Application flow
    MDS --> QSS
    QSS --> MDP
    QSS --> IPS
    MDP --> QDF
    QDF --> NS
    NS --> QWSC
    QWSC --> Clients

    %% Data flow
    QSS --> ICS
    QSS --> STS
    ICS --> SS
    ICS --> IS
    SS --> Repos
    IS --> Repos
    Repos --> PG

    %% Monitoring
    QSS -->|Metrics| Prometheus
    MDP -->|Metrics| Prometheus
    NS -->|Metrics| Prometheus
    Prometheus --> Grafana
    QSS -->|Logs| ELK
    MDS -->|Logs| ELK
```

## Поток обработки данных

```mermaid
sequenceDiagram
    participant TInvest as T-Invest API
    participant MDS as MarketDataStreamingService
    participant QSS as QuoteScannerService
    participant MDP as MarketDataProcessor
    participant ICS as InstrumentCacheService
    participant QDF as QuoteDataFactory
    participant NS as NotificationService
    participant WS as WebSocket Clients

    Note over TInvest,WS: Поток обработки котировок

    TInvest->>MDS: LastPrice data (gRPC)
    MDS->>QSS: processLastPrice()
    QSS->>MDP: processLastPrice()

    Note over MDP: Дедупликация и валидация

    MDP->>ICS: setLastPrice(figi, price)
    MDP->>ICS: getInstrumentData(figi)
    ICS-->>MDP: instrument data

    MDP->>QDF: createFromLastPrice(price, data)
    QDF-->>MDP: QuoteData object

    MDP->>NS: notifySubscribers(quoteData)

    Note over NS: Параллельная отправка

    NS->>WS: broadcastQuote(quoteData)
    NS->>WS: broadcastQuote(quoteData)
    NS->>WS: broadcastQuote(quoteData)

    Note over WS: WebSocket клиенты получают данные
```

## Поток обработки сделок

```mermaid
sequenceDiagram
    participant TInvest as T-Invest API
    participant MDS as MarketDataStreamingService
    participant QSS as QuoteScannerService
    participant MDP as MarketDataProcessor
    participant ICS as InstrumentCacheService
    participant NS as NotificationService
    participant WS as WebSocket Clients

    Note over TInvest,WS: Поток обработки сделок

    TInvest->>MDS: Trade data (gRPC)
    MDS->>QSS: processTrade()
    QSS->>MDP: processTrade()

    Note over MDP: Проверка сессии и дедупликация

    MDP->>ICS: setLastPrice(figi, price)
    MDP->>ICS: addToAccumulatedVolume(figi, quantity)

    Note over ICS: Накопление объема только в выходные

    MDP->>NS: notifySubscribers(tradeData)
    NS->>WS: broadcastTrade(tradeData)
```

## Поток обработки стакана заявок

```mermaid
sequenceDiagram
    participant TInvest as T-Invest API
    participant MDS as MarketDataStreamingService
    participant QSS as QuoteScannerService
    participant MDP as MarketDataProcessor
    participant ICS as InstrumentCacheService

    Note over TInvest,ICS: Поток обработки стакана заявок

    TInvest->>MDS: OrderBook data (gRPC)
    MDS->>QSS: processOrderBook()
    QSS->>MDP: processOrderBook()

    Note over MDP: Извлечение лучших BID/ASK

    MDP->>ICS: setBestBid(figi, price, quantity)
    MDP->>ICS: setBestAsk(figi, price, quantity)

    Note over ICS: Обновление данных стакана в кэше
```

## Архитектура кэширования

```mermaid
graph TB
    subgraph "Cache Layers"
        L1[L1 Cache<br/>ConcurrentHashMap<br/>Hot Data]
        L2[L2 Cache<br/>Caffeine<br/>Warm Data]
        L3[L3 Cache<br/>Redis<br/>Cold Data]
    end

    subgraph "Data Sources"
        DB[(PostgreSQL<br/>Database)]
        API[T-Invest API<br/>External]
    end

    subgraph "Cache Services"
        ICS[InstrumentCacheService<br/>Main Cache Manager]
        PCS[PriceCacheService<br/>Price Data]
        NCS[NameCacheService<br/>Instrument Names]
        VCS[VolumeCacheService<br/>Volume Data]
    end

    subgraph "Applications"
        MDP[MarketDataProcessor]
        QSS[QuoteScannerService]
        WS[WebSocketController]
    end

    %% Cache hierarchy
    L1 --> L2
    L2 --> L3
    L3 --> DB

    %% Data flow
    API --> ICS
    DB --> ICS
    ICS --> L1
    ICS --> L2
    ICS --> L3

    %% Service usage
    MDP --> PCS
    QSS --> NCS
    WS --> VCS

    %% Cache services to layers
    PCS --> L1
    NCS --> L2
    VCS --> L3
```

## Микросервисная архитектура (будущее)

```mermaid
graph TB
    subgraph "API Gateway"
        AG[Kong/Istio Gateway<br/>Load Balancing<br/>Authentication<br/>Rate Limiting]
    end

    subgraph "Core Services"
        DIS[Data Ingestion Service<br/>T-Invest Integration]
        DPS[Data Processing Service<br/>Market Data Processing]
        DSS[Data Streaming Service<br/>WebSocket Broadcasting]
        CAS[Cache Service<br/>Distributed Caching]
        AUS[Analytics Service<br/>Data Analysis]
    end

    subgraph "Supporting Services"
        NTS[Notification Service<br/>Event Broadcasting]
        MTS[Metrics Service<br/>Monitoring]
        LGS[Logging Service<br/>Centralized Logging]
        HCS[Health Check Service<br/>Service Discovery]
    end

    subgraph "Data Layer"
        PG[(PostgreSQL<br/>Primary Database)]
        RD[(Redis<br/>Cache & Sessions)]
        ES[(Elasticsearch<br/>Search & Analytics)]
    end

    subgraph "External Systems"
        TInvest[T-Invest API]
        Clients[WebSocket Clients]
        Mobile[Mobile Apps]
    end

    %% External connections
    TInvest -->|gRPC| DIS
    Clients -->|WebSocket| DSS
    Mobile -->|REST| AG

    %% API Gateway routing
    AG --> DIS
    AG --> DPS
    AG --> DSS
    AG --> AUS

    %% Service communication
    DIS --> DPS
    DPS --> DSS
    DPS --> CAS
    DSS --> NTS
    AUS --> CAS

    %% Data access
    DIS --> PG
    DPS --> PG
    DSS --> RD
    CAS --> RD
    AUS --> ES

    %% Monitoring
    DIS --> MTS
    DPS --> MTS
    DSS --> MTS
    MTS --> LGS
```

## Event-driven архитектура (будущее)

```mermaid
graph TB
    subgraph "Event Sources"
        TInvest[T-Invest API]
        User[User Actions]
        System[System Events]
    end

    subgraph "Event Bus"
        EB[Apache Kafka<br/>Event Streaming Platform]
    end

    subgraph "Event Handlers"
        LPEH[LastPriceEventHandler]
        TEH[TradeEventHandler]
        OBEH[OrderBookEventHandler]
        UEH[UserEventHandler]
        SEH[SystemEventHandler]
    end

    subgraph "Event Processors"
        QP[QuoteProcessor]
        TP[TradeProcessor]
        OP[OrderBookProcessor]
        AP[AnalyticsProcessor]
        NP[NotificationProcessor]
    end

    subgraph "Event Stores"
        ES[Event Store<br/>Event Sourcing]
        CS[Command Store<br/>CQRS]
    end

    subgraph "Read Models"
        RM1[Quote Read Model]
        RM2[Trade Read Model]
        RM3[Analytics Read Model]
    end

    %% Event flow
    TInvest -->|Events| EB
    User -->|Events| EB
    System -->|Events| EB

    EB --> LPEH
    EB --> TEH
    EB --> OBEH
    EB --> UEH
    EB --> SEH

    LPEH --> QP
    TEH --> TP
    OBEH --> OP
    UEH --> AP
    SEH --> NP

    QP --> ES
    TP --> ES
    OP --> ES
    AP --> CS
    NP --> CS

    ES --> RM1
    ES --> RM2
    CS --> RM3
```

## Мониторинг и наблюдаемость

```mermaid
graph TB
    subgraph "Application"
        APP[Investment Scanner<br/>Spring Boot App]
    end

    subgraph "Metrics Collection"
        ACT[Actuator<br/>Spring Boot Actuator]
        CUS[Custom Metrics<br/>Business Metrics]
        JVM[JVM Metrics<br/>Memory, CPU, GC]
    end

    subgraph "Monitoring Stack"
        PROM[Prometheus<br/>Metrics Storage]
        GRAF[Grafana<br/>Dashboards]
        ALERT[AlertManager<br/>Alerting]
    end

    subgraph "Logging Stack"
        LOG[Application Logs<br/>Structured JSON]
        FILE[Filebeat<br/>Log Shipper]
        ES[Elasticsearch<br/>Log Storage]
        KIB[Kibana<br/>Log Analysis]
    end

    subgraph "Tracing"
        TRACE[Distributed Tracing<br/>Zipkin/Jaeger]
        SPAN[Span Data<br/>Request Tracing]
    end

    subgraph "Health Checks"
        HC[Health Indicators<br/>Custom Health Checks]
        K8S[Kubernetes<br/>Liveness/Readiness]
    end

    %% Metrics flow
    APP --> ACT
    APP --> CUS
    APP --> JVM
    ACT --> PROM
    CUS --> PROM
    JVM --> PROM
    PROM --> GRAF
    PROM --> ALERT

    %% Logging flow
    APP --> LOG
    LOG --> FILE
    FILE --> ES
    ES --> KIB

    %% Tracing flow
    APP --> TRACE
    TRACE --> SPAN

    %% Health checks
    APP --> HC
    HC --> K8S
```

## Безопасность

```mermaid
graph TB
    subgraph "External Access"
        INTERNET[Internet]
        VPN[VPN]
        DMZ[DMZ]
    end

    subgraph "Security Layers"
        WAF[Web Application Firewall<br/>OWASP Protection]
        LB[Load Balancer<br/>SSL Termination]
        AUTH[Authentication<br/>JWT/OAuth2]
        AUTHZ[Authorization<br/>RBAC]
    end

    subgraph "Application Security"
        VAL[Input Validation<br/>Data Sanitization]
        ENC[Encryption<br/>Data at Rest/Transit]
        AUDIT[Audit Logging<br/>Security Events]
        RATE[Rate Limiting<br/>DDoS Protection]
    end

    subgraph "Infrastructure Security"
        NS[Network Security<br/>Firewalls, VPC]
        CS[Container Security<br/>Image Scanning]
        SS[Secret Management<br/>Vault/K8s Secrets]
        MS[Monitoring Security<br/>SIEM]
    end

    %% Security flow
    INTERNET --> WAF
    VPN --> WAF
    DMZ --> WAF

    WAF --> LB
    LB --> AUTH
    AUTH --> AUTHZ

    AUTHZ --> VAL
    VAL --> ENC
    ENC --> AUDIT
    AUDIT --> RATE

    %% Infrastructure security
    NS --> CS
    CS --> SS
    SS --> MS
```

## Заключение

Эти диаграммы показывают:

1. **Текущую архитектуру** - монолитное Spring Boot приложение с четким разделением слоев
2. **Потоки данных** - как данные проходят через систему от T-Invest API до клиентов
3. **Кэширование** - многоуровневая система кэширования для оптимизации производительности
4. **Будущую архитектуру** - микросервисы и event-driven подход
5. **Мониторинг** - полная система наблюдаемости
6. **Безопасность** - многоуровневая защита системы

Диаграммы помогают понять:

- Как компоненты взаимодействуют друг с другом
- Где находятся узкие места
- Как система может масштабироваться
- Какие улучшения необходимы
