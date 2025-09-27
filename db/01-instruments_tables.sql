--Таблица для хранения информации об акциях
create table shares
(
    figi           varchar(255) not null
        primary key,
    ticker         varchar(255),
    name           varchar(255),
    currency       varchar(255),
    exchange       varchar(255),
    sector         varchar(255),
    trading_status varchar(255),
    created_at     timestamp with time zone default (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow'::text),
    updated_at     timestamp with time zone default (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow'::text)
);

comment on table shares is 'Таблица для хранения информации об акциях';

comment on column shares.figi is 'Уникальный идентификатор инструмента (Financial Instrument Global Identifier)';

comment on column shares.ticker is 'Тикер акции';

comment on column shares.name is 'Название компании';

comment on column shares.currency is 'Валюта торговли';

comment on column shares.exchange is 'Биржа, на которой торгуется акция';

comment on column shares.sector is 'Сектор экономики';

comment on column shares.trading_status is 'Статус торговли';

comment on column shares.created_at is 'Дата и время создания записи (московское время)';

comment on column shares.updated_at is 'Дата и время последнего обновления записи (московское время)';

alter table shares
    owner to postgres;



--Таблица для хранения информации об фьючерсах
create table futures
(
    figi        varchar(255) not null
        primary key,
    asset_type  varchar(255),
    basic_asset varchar(255),
    currency    varchar(255),
    exchange    varchar(255),
    ticker      varchar(255),
    created_at  timestamp(6) not null,
    updated_at  timestamp(6) not null
);

comment on table futures is 'Таблица фьючерсов из T-Invest API';

comment on column futures.figi is 'Уникальный идентификатор инструмента';

comment on column futures.asset_type is 'Тип базового актива (TYPE_SECURITY, TYPE_COMMODITY, TYPE_CURRENCY, TYPE_INDEX)';

comment on column futures.basic_asset is 'Базовый актив фьючерса';

comment on column futures.currency is 'Валюта инструмента';

comment on column futures.exchange is 'Биржа, на которой торгуется инструмент';

comment on column futures.ticker is 'Тикер инструмента';

comment on column futures.created_at is 'Дата и время создания записи';

comment on column futures.updated_at is 'Дата и время последнего обновления записи';

alter table futures
    owner to postgres;

grant select on futures to tester;

grant delete, insert, references, select, trigger, truncate, update on futures to admin;


--Таблица для хранения информации об индикативах
create table indicatives
(
    figi                varchar(255) not null
        primary key,
    ticker              varchar(255) not null,
    name                varchar(255) not null,
    currency            varchar(255) not null,
    exchange            varchar(255) not null,
    class_code          varchar(255),
    uid                 varchar(255),
    sell_available_flag boolean                  default false,
    buy_available_flag  boolean                  default false,
    created_at          timestamp with time zone default (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow'::text),
    updated_at          timestamp with time zone default (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow'::text)
);

comment on table indicatives is 'Индикативные инструменты (индексы, товары и другие)';

comment on column indicatives.figi is 'FIGI инструмента (уникальный идентификатор)';

comment on column indicatives.ticker is 'Тикер инструмента';

comment on column indicatives.name is 'Название инструмента';

comment on column indicatives.currency is 'Валюта инструмента';

comment on column indicatives.exchange is 'Биржа/площадка';

comment on column indicatives.class_code is 'Код класса инструмента';

comment on column indicatives.uid is 'Уникальный идентификатор';

comment on column indicatives.sell_available_flag is 'Флаг доступности для продажи';

comment on column indicatives.buy_available_flag is 'Флаг доступности для покупки';

comment on column indicatives.created_at is 'Дата создания записи (UTC+3)';

comment on column indicatives.updated_at is 'Дата последнего обновления записи (UTC+3)';

alter table indicatives
    owner to postgres;





