-- auto-generated definition
create table system_logs
(
    id          bigserial
        primary key,
    task_id     varchar(255)             not null,
    endpoint    varchar(255)             not null,
    method      varchar(255)             not null,
    status      varchar(255)             not null,
    message     text                     not null,
    start_time  timestamp with time zone not null,
    end_time    timestamp with time zone,
    duration_ms bigint,
    created_at  timestamp with time zone default now()
);

comment on table system_logs is 'Упрощенная таблица для записи логов работы методов';

comment on column system_logs.task_id is 'ID задачи';

comment on column system_logs.endpoint is 'Название эндпоинта';

comment on column system_logs.method is 'HTTP метод';

comment on column system_logs.status is 'Статус выполнения';

comment on column system_logs.message is 'Текстовое сообщение о работе';

comment on column system_logs.start_time is 'Время начала';

comment on column system_logs.end_time is 'Время завершения';

comment on column system_logs.duration_ms is 'Длительность в миллисекундах';

alter table system_logs
    owner to postgres;

grant select, update, usage on sequence system_logs_id_seq to admin;

create index idx_system_logs_task_id
    on system_logs (task_id);

create index idx_system_logs_endpoint
    on system_logs (endpoint);

create index idx_system_logs_status
    on system_logs (status);

grant select on system_logs to tester;

grant delete, insert, references, select, trigger, truncate, update on system_logs to admin;




-- auto-generated definition
-- Универсальная таблица несоответствий (для любых DQ-проверок)
create table if not exists data_quality_issues
(
    id                bigserial primary key,
    task_id           varchar(255)             not null,  -- связь с system_logs.task_id
    check_name        varchar(128)             not null,  -- имя проверки (например, 'daily_vs_minute')
    entity_type       varchar(64)              not null,  -- тип сущности ('SHARE','FUTURE', ...)
    entity_id         varchar(255)             not null,  -- идентификатор сущности (например, FIGI)
    trade_date        date,                                  -- дата, если применимо
    metric            varchar(64)              not null,  -- что сравнивали: 'volume','high','low','open','close', ...
    expected_numeric  numeric(30,10),
    actual_numeric    numeric(30,10),
    diff_numeric      numeric(30,10),
    status            varchar(32)              not null,  -- 'OK','MISMATCH','MISSING','WARNING','ERROR'
    message           text,                                  -- произвольное пояснение
    details           jsonb,                                 -- доп. контекст, если нужен
    created_at        timestamp with time zone default now() not null
);

comment on table data_quality_issues is 'Универсальные результаты DQ-проверок: любая метрика/сущность/дата. Привязка к system_logs.task_id';

create index if not exists idx_dqi_task on data_quality_issues(task_id);
create index if not exists idx_dqi_check on data_quality_issues(check_name);
create index if not exists idx_dqi_entity on data_quality_issues(entity_type, entity_id);
create index if not exists idx_dqi_date on data_quality_issues(trade_date);

alter table data_quality_issues owner to postgres;
grant select on data_quality_issues to tester;
grant delete, insert, references, select, trigger, truncate, update on data_quality_issues to admin;


-- auto-generated definition
create table index_session_times
(
    figi               varchar(50)                            not null
        primary key,
    ticker             varchar(20)                            not null,
    name               varchar(255)                           not null,
    session_close_time time                                   not null,
    description        text,
    is_active          boolean                  default true  not null,
    created_at         timestamp with time zone default now() not null,
    updated_at         timestamp with time zone default now() not null
);

comment on table index_session_times is 'Таблица для хранения информации о времени закрытия торговой сессии для различных индексов';

comment on column index_session_times.figi is 'Уникальный идентификатор инструмента (FIGI)';

comment on column index_session_times.ticker is 'Тикер индекса';

comment on column index_session_times.name is 'Название индекса';

comment on column index_session_times.session_close_time is 'Время закрытия торговой сессии по Московскому времени';

comment on column index_session_times.description is 'Описание индекса';

comment on column index_session_times.is_active is 'Активна ли запись';

comment on column index_session_times.created_at is 'Дата и время создания записи';

comment on column index_session_times.updated_at is 'Дата и время последнего обновления записи';

alter table index_session_times
    owner to postgres;

grant select on index_session_times to tester;

grant delete, insert, references, select, trigger, truncate, update on index_session_times to admin;

