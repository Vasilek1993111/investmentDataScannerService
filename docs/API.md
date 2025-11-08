# API Документация

## Обзор

Investment Data Scanner Service предоставляет REST API и WebSocket endpoints для получения рыночных данных в реальном времени.

**Base URL:** `http://localhost:8085`  
**WebSocket URL:** `ws://localhost:8085`

## Аутентификация

В текущей версии API не требует аутентификации. В будущих версиях планируется добавление JWT токенов.

## REST API

### Scanner Controller

#### GET /api/scanner/stats

Получение статистики работы сканера.

**Параметры запроса:** Нет

**Ответ:**

```json
{
  "totalQuotesProcessed": 15000,
  "sharesMode": true,
  "trackedInstruments": 150,
  "marketDataProcessor": {
    "totalProcessed": 15000,
    "lastPriceProcessed": 8000,
    "tradeProcessed": 7000,
    "orderBookProcessed": 5000,
    "processingTime": 1250.5,
    "uniqueInstruments": 150
  },
  "notificationService": {
    "subscriberCount": 5,
    "notificationsSent": 75000,
    "notificationsFailed": 0,
    "hasSubscribers": true
  },
  "cacheStats": {
    "trackedInstruments": 150,
    "instrumentNames": 150,
    "instrumentTickers": 150,
    "avgVolumeMorning": 120,
    "avgVolumeWeekend": 80,
    "closePrices": 150,
    "openPrices": 50,
    "accumulatedVolumes": 150,
    "bestBids": 100,
    "bestAsks": 100
  },
  "sessionInfo": "MORNING_SESSION (утренняя сессия)",
  "isScannerActive": true
}
```

**Коды ответов:**

- `200 OK` - Успешный запрос
- `500 Internal Server Error` - Внутренняя ошибка сервера

---

#### GET /api/scanner/subscribers/count

Получение количества активных подписчиков на обновления котировок через WebSocket.

**Параметры запроса:** Нет

**Ответ:**

```json
{
  "subscriberCount": 5,
  "hasSubscribers": true,
  "notificationsSent": 75000,
  "notificationsFailed": 0
}
```

**Поля ответа:**

- `subscriberCount` (integer) - Текущее количество активных подписчиков
- `hasSubscribers` (boolean) - Есть ли активные подписчики
- `notificationsSent` (integer) - Общее количество отправленных уведомлений
- `notificationsFailed` (integer) - Количество неудачных отправок уведомлений

**Коды ответов:**

- `200 OK` - Успешный запрос
- `500 Internal Server Error` - Внутренняя ошибка сервера

**Пример использования:**

```bash
curl http://localhost:8085/api/scanner/subscribers/count
```

---

#### GET /api/scanner/instruments

Получение списка отслеживаемых инструментов.

**Параметры запроса:** Нет

**Ответ:**

```json
{
  "instruments": [
    "BBG004730N88",
    "BBG0047315Y7",
    "BBG000MZL0Y6",
    "BBG004S68598",
    "BBG004RVFFC0"
  ],
  "count": 150
}
```

**Коды ответов:**

- `200 OK` - Успешный запрос
- `500 Internal Server Error` - Внутренняя ошибка сервера

---

#### GET /api/scanner/prices

Получение текущих цен всех отслеживаемых инструментов.

**Параметры запроса:** Нет

**Ответ:**

```json
{
  "prices": {
    "BBG004730N88": 250.5,
    "BBG0047315Y7": 245.3,
    "BBG000MZL0Y6": 125.8
  },
  "instrumentNames": {
    "BBG004730N88": "SBER",
    "BBG0047315Y7": "SBERP",
    "BBG000MZL0Y6": "PMSB"
  },
  "count": 150
}
```

**Коды ответов:**

- `200 OK` - Успешный запрос
- `500 Internal Server Error` - Внутренняя ошибка сервера

---

### Instrument Pair Controller

#### GET /api/pairs

Получение списка пар инструментов.

**Параметры запроса:** Нет

**Ответ:**

```json
{
  "pairs": [
    {
      "pairId": 1,
      "firstInstrument": "BBG004730N88",
      "secondInstrument": "BBG0047315Y7",
      "firstInstrumentName": "SBER",
      "secondInstrumentName": "SBERP",
      "currentDelta": 5.2,
      "deltaPercent": 2.12
    },
    {
      "pairId": 2,
      "firstInstrument": "BBG000MZL0Y6",
      "secondInstrument": "BBG000MZL2S9",
      "firstInstrumentName": "PMSB",
      "secondInstrumentName": "PMSBP",
      "currentDelta": 2.15,
      "deltaPercent": 1.71
    }
  ],
  "count": 7
}
```

**Коды ответов:**

- `200 OK` - Успешный запрос
- `500 Internal Server Error` - Внутренняя ошибка сервера

---

#### GET /api/pairs/{pairId}

Получение информации о конкретной паре инструментов.

**Параметры пути:**

- `pairId` (integer, required) - ID пары инструментов

**Ответ:**

```json
{
  "pairId": 1,
  "firstInstrument": "BBG004730N88",
  "secondInstrument": "BBG0047315Y7",
  "firstInstrumentName": "SBER",
  "secondInstrumentName": "SBERP",
  "currentDelta": 5.2,
  "deltaPercent": 2.12,
  "firstPrice": 250.5,
  "secondPrice": 245.3,
  "timestamp": "2024-01-15T10:30:45"
}
```

**Коды ответов:**

- `200 OK` - Успешный запрос
- `404 Not Found` - Пара не найдена
- `500 Internal Server Error` - Внутренняя ошибка сервера

---

### Streaming Service Controller

#### GET /api/streaming/status

Получение статуса потокового сервиса.

**Параметры запроса:** Нет

**Ответ:**

```json
{
  "isRunning": true,
  "isConnected": true,
  "totalReceived": 15000,
  "totalTradeReceived": 8000,
  "totalOrderBookReceived": 2000,
  "totalReceivedAll": 25000
}
```

**Коды ответов:**

- `200 OK` - Успешный запрос
- `500 Internal Server Error` - Внутренняя ошибка сервера

---

#### POST /api/streaming/reconnect

Принудительное переподключение к T-Invest API.

**Параметры запроса:** Нет

**Ответ:**

```json
{
  "message": "Reconnection initiated",
  "timestamp": "2024-01-15T10:30:45"
}
```

**Коды ответов:**

- `200 OK` - Переподключение инициировано
- `500 Internal Server Error` - Ошибка при переподключении

---

## WebSocket API

### Подключение к котировкам

**URL:** `ws://localhost:8085/ws/quotes`

#### Подключение

```javascript
const ws = new WebSocket("ws://localhost:8085/ws/quotes");

ws.onopen = function (event) {
  console.log("Connected to quotes stream");
};

ws.onmessage = function (event) {
  const quoteData = JSON.parse(event.data);
  console.log("Received quote:", quoteData);
};

ws.onclose = function (event) {
  console.log("Disconnected from quotes stream");
};

ws.onerror = function (error) {
  console.error("WebSocket error:", error);
};
```

#### Формат сообщений

Каждое сообщение содержит объект `QuoteData`:

```json
{
  "figi": "BBG004730N88",
  "ticker": "SBER",
  "instrumentName": "Сбербанк",
  "currentPrice": 250.5,
  "previousPrice": 249.8,
  "priceChange": 0.7,
  "priceChangePercent": 0.28,
  "closePrice": 248.9,
  "openPrice": 249.2,
  "closePriceChange": 1.6,
  "closePriceChangePercent": 0.64,
  "closePriceOS": 248.9,
  "closePriceVS": 249.1,
  "closePriceVSChange": 1.4,
  "closePriceVSChangePercent": 0.56,
  "bestBid": 250.45,
  "bestAsk": 250.55,
  "bestBidQuantity": 1000,
  "bestAskQuantity": 1500,
  "timestamp": "2024-01-15T10:30:45",
  "volume": 5000,
  "totalVolume": 150000,
  "direction": "UP",
  "avgVolumeMorning": 120000,
  "avgVolumeWeekend": 80000
}
```

#### Поля данных

| Поле                        | Тип    | Описание                                       |
| --------------------------- | ------ | ---------------------------------------------- |
| `figi`                      | string | Уникальный идентификатор инструмента           |
| `ticker`                    | string | Тикер инструмента                              |
| `instrumentName`            | string | Название инструмента                           |
| `currentPrice`              | number | Текущая цена                                   |
| `previousPrice`             | number | Предыдущая цена                                |
| `priceChange`               | number | Изменение цены                                 |
| `priceChangePercent`        | number | Изменение цены в процентах                     |
| `closePrice`                | number | Цена закрытия предыдущего дня                  |
| `openPrice`                 | number | Цена открытия                                  |
| `closePriceChange`          | number | Изменение от цены закрытия                     |
| `closePriceChangePercent`   | number | Изменение от цены закрытия в %                 |
| `closePriceOS`              | number | Цена закрытия основной сессии                  |
| `closePriceVS`              | number | Цена закрытия вечерней сессии                  |
| `closePriceVSChange`        | number | Изменение от цены закрытия вечерней сессии     |
| `closePriceVSChangePercent` | number | Изменение от цены закрытия вечерней сессии в % |
| `bestBid`                   | number | Лучшая цена покупки                            |
| `bestAsk`                   | number | Лучшая цена продажи                            |
| `bestBidQuantity`           | number | Количество лотов лучшего BID                   |
| `bestAskQuantity`           | number | Количество лотов лучшего ASK                   |
| `timestamp`                 | string | Время получения данных (ISO 8601)              |
| `volume`                    | number | Объем текущей сделки                           |
| `totalVolume`               | number | Общий объем за сессию                          |
| `direction`                 | string | Направление изменения цены (UP/DOWN/NEUTRAL)   |
| `avgVolumeMorning`          | number | Средний утренний объем                         |
| `avgVolumeWeekend`          | number | Средний объем выходного дня                    |

---

### Подключение к парам инструментов

**URL:** `ws://localhost:8085/ws/pairs`

#### Подключение

```javascript
const ws = new WebSocket("ws://localhost:8085/ws/pairs");

ws.onopen = function (event) {
  console.log("Connected to pairs stream");
};

ws.onmessage = function (event) {
  const pairData = JSON.parse(event.data);
  console.log("Received pair data:", pairData);
};
```

#### Формат сообщений

```json
{
  "pairId": 1,
  "firstInstrument": "BBG004730N88",
  "secondInstrument": "BBG0047315Y7",
  "firstInstrumentName": "SBER",
  "secondInstrumentName": "SBERP",
  "currentDelta": 5.2,
  "deltaPercent": 2.12,
  "firstPrice": 250.5,
  "secondPrice": 245.3,
  "timestamp": "2024-01-15T10:30:45"
}
```

---

## Коды ошибок

### HTTP коды ответов

| Код                         | Описание                  |
| --------------------------- | ------------------------- |
| `200 OK`                    | Успешный запрос           |
| `400 Bad Request`           | Некорректный запрос       |
| `404 Not Found`             | Ресурс не найден          |
| `500 Internal Server Error` | Внутренняя ошибка сервера |
| `503 Service Unavailable`   | Сервис недоступен         |

### WebSocket коды закрытия

| Код    | Описание                    |
| ------ | --------------------------- |
| `1000` | Нормальное закрытие         |
| `1001` | Клиент покидает страницу    |
| `1002` | Ошибка протокола            |
| `1003` | Неподдерживаемый тип данных |
| `1006` | Аномальное закрытие         |
| `1011` | Ошибка сервера              |

---

## Примеры использования

### JavaScript/Node.js

```javascript
// REST API
const axios = require("axios");

async function getScannerStats() {
  try {
    const response = await axios.get("http://localhost:8085/api/scanner/stats");
    console.log("Scanner stats:", response.data);
  } catch (error) {
    console.error("Error:", error.message);
  }
}

// WebSocket
const WebSocket = require("ws");

const ws = new WebSocket("ws://localhost:8085/ws/quotes");

ws.on("message", function (data) {
  const quote = JSON.parse(data);
  console.log(`${quote.ticker}: ${quote.currentPrice} (${quote.direction})`);
});
```

### Python

```python
import requests
import websocket
import json

# REST API
def get_scanner_stats():
    response = requests.get('http://localhost:8085/api/scanner/stats')
    return response.json()

# WebSocket
def on_message(ws, message):
    quote = json.loads(message)
    print(f"{quote['ticker']}: {quote['currentPrice']} ({quote['direction']})")

def on_error(ws, error):
    print(f"WebSocket error: {error}")

def on_close(ws, close_status_code, close_msg):
    print("WebSocket connection closed")

ws = websocket.WebSocketApp("ws://localhost:8085/ws/quotes",
                          on_message=on_message,
                          on_error=on_error,
                          on_close=on_close)
ws.run_forever()
```

### Java

```java
// REST API
import org.springframework.web.client.RestTemplate;

RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8085/api/scanner/stats";
Map<String, Object> stats = restTemplate.getForObject(url, Map.class);

// WebSocket
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

WebSocketClient client = new WebSocketClient(new URI("ws://localhost:8085/ws/quotes")) {
    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected to quotes stream");
    }

    @Override
    public void onMessage(String message) {
        // Обработка сообщения
        System.out.println("Received: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Connection closed");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
};
client.connect();
```

---

## Ограничения

### Rate Limiting

- **REST API**: 1000 запросов в минуту на IP
- **WebSocket**: Максимум 100 одновременных соединений

### gRPC подписки (T-Invest API)

Сервис использует gRPC для получения потоковых данных от T-Invest API. При работе с подписками необходимо учитывать следующие лимиты:

#### Количество активных stream-соединений

- **Зависит от лимитного грейда**: от 16 до 64 одновременных соединений
- **Лимитный грейд** определяется активностью за последние 30 дней (количество заявок и процент исполнения)
- Чем выше грейд, тем больше доступных ресурсов

#### Количество подписок в одном stream-соединении

- **Максимум 300 одновременных подписок** в одном stream-соединении
- Лимит считается суммарно по всем типам данных:
  - Свечи (candles)
  - Стаканы (orderbook)
  - Лента обезличенных сделок (trades)

#### Количество инструментов в одной подписке

- **Максимум 200 инструментов** в одном запросе подписки
- В сервисе используется батчинг: инструменты разбиваются на батчи по 200 штук
- Между батчами добавляется задержка 100 мс для соблюдения лимитов

#### Частота запросов подписки

- **Максимум 100 запросов на подписку в минуту**

#### Реализация в коде

```java
// Лимит T-Invest API на количество инструментов в одной подписке
private static final int SUBSCRIPTION_BATCH_SIZE = 200;

// Задержка между отправкой батчей подписки (мс)
private static final int BATCH_DELAY_MS = 100;
```

**Подробнее:** См. [GRPC_LIMITS.md](GRPC_LIMITS.md) для детальной информации о лимитах и рекомендациях по оптимизации.

### Размер данных

- **WebSocket сообщения**: Максимум 1MB
- **REST ответы**: Максимум 10MB
- **gRPC сообщения**: Максимум 4MB (настроено в `GrpcConfig`)

### Таймауты

- **WebSocket**: 30 секунд неактивности
- **REST API**: 30 секунд на запрос
- **gRPC keep-alive**: 30 секунд (настроено в `GrpcConfig`)

---

## Версионирование

API использует семантическое версионирование (SemVer).

**Текущая версия:** 1.0.0

### Изменения в версиях

#### v1.0.0 (2024-01-15)

- Первоначальный релиз
- REST API для статистики и данных
- WebSocket для котировок и пар инструментов
- Поддержка T-Invest API

---

## Поддержка

Для получения поддержки:

1. Проверьте [Issues](../../issues) на наличие существующих проблем
2. Создайте новый Issue с подробным описанием
3. Обратитесь к команде разработки

**Email:** support@investment-scanner.com  
**Документация:** [https://docs.investment-scanner.com](https://docs.investment-scanner.com)
