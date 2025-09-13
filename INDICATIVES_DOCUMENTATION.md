# 📊 Документация по поддержке индексов (Indicatives)

## 🎯 Обзор

Добавлена поддержка не торгуемых индексов (indicatives) в систему сканирования котировок. Индексы загружаются из таблицы `invest.indicatives` и отображаются в утреннем сканере наравне с акциями.

## 🏗️ Архитектура

### Новые компоненты

1. **IndicativeEntity** - Entity для таблицы `invest.indicatives`
2. **IndicativeRepository** - Repository для работы с индексами
3. **IndicativeService** - Сервис для бизнес-логики работы с индексами

### Обновленные компоненты

- **QuoteScannerService** - обновлен для поддержки indicatives
- **MarketDataStreamingService** - обновлен для подписки на котировки indicatives
- **MorningScannerController** - автоматически отображает индексы

## 📋 Структура таблицы indicatives

```sql
CREATE TABLE indicatives (
    figi                varchar(255) not null primary key,
    buy_available_flag  boolean,
    class_code          varchar(255),
    currency            varchar(255),
    exchange            varchar(255),
    name                varchar(255),
    sell_available_flag boolean,
    ticker              varchar(255),
    uid                 varchar(255)
);
```

## 🔧 Настройка

### Работа с существующими данными

Данные в таблице `indicatives` уже присутствуют и загружаются автоматически при запуске приложения. Дополнительное заполнение не требуется.

## 🚀 Использование

### В утреннем сканере

Индексы автоматически отображаются в утреннем сканере:

- Загружаются из существующей таблицы `invest.indicatives`
- Отображаются в общем списке инструментов
- Показываются в топ-15 растущих/падающих
- Имеют корректные имена и тикеры

### Подписка на котировки

При запуске приложения система автоматически:

1. **Загружает все инструменты** - акции из `invest.shares` + индексы из `invest.indicatives`
2. **Подписывается на котировки** - LastPrice и Trades для всех инструментов
3. **Подписывается на стаканы** - только для акций (если включено в конфигурации)

### Программное использование

```java
@Autowired
private IndicativeService indicativeService;

// Получить все индексы
List<IndicativeEntity> indicatives = indicativeService.getAllIndicatives();

// Получить индекс по тикеру
IndicativeEntity imoex2 = indicativeService.getIndicativeByTicker("IMOEX2");

// Получить FIGI всех индексов
List<String> figis = indicativeService.getAllIndicativeFigis();
```

## 📈 Мониторинг

### Логи

Сервис логирует:

- Количество загруженных индексов
- Статистику по биржам и валютам
- Ошибки при загрузке данных

### Метрики

- Общее количество индексов
- Количество индексов по биржам
- Количество индексов по валютам
- Статистика доступности для торговли

## 🔍 Отладка

### Проверка загрузки индексов

1. Проверить логи при запуске:

   ```
   Loaded X indicatives from database
   Using shares mode: Y shares + Z indicatives = W total instruments
   ```

2. Проверить утренний сканер:
   ```bash
   curl http://localhost:8085/api/morning-scanner/instruments
   ```

### Частые проблемы

1. **Индексы не загружаются** - проверить подключение к БД и наличие данных в таблице
2. **Индексы не отображаются в сканере** - проверить логи QuoteScannerService
3. **Ошибки подписки** - проверить T-Invest API токен и логи MarketDataStreamingService

## 📝 Примечания

- Индексы обрабатываются так же, как акции, но не имеют объема торгов
- Для индексов используется только LastPrice (нет Trade данных)
- Индексы участвуют в расчете топ-15 растущих/падающих
- Подписка на стаканы работает только для акций
- Управление индексами осуществляется через другое приложение
