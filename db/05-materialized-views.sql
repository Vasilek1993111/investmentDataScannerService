-- Создание схемы для материализованных представлений
CREATE SCHEMA IF NOT EXISTS invest_views;

-- Установка владельца схемы
ALTER SCHEMA invest_views OWNER TO postgres;

-- Предоставление прав на схему
GRANT USAGE ON SCHEMA invest_views TO tester;
GRANT USAGE ON SCHEMA invest_views TO admin;

--Материализованное представление history_volume_aggregation в схеме invest_views

CREATE MATERIALIZED VIEW invest_views.history_volume_aggregation AS
SELECT minute_candles.figi,
       CASE
           WHEN s.figi IS NOT NULL THEN 'share'::text
           WHEN f.figi IS NOT NULL THEN 'future'::text
           ELSE 'unknown'::text
           END                                                         AS instrument_type,
       sum(minute_candles.volume)                                      AS total_volume,
       count(*)                                                        AS total_candles,
       round(avg(minute_candles.volume), 2)                            AS avg_volume_per_candle,
       sum(
               CASE
                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 6::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                         59::numeric AND
                         EXTRACT(second FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         59::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 7::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 8::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 9::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                       THEN minute_candles.volume
                   ELSE 0::bigint
                   END)                                                AS morning_session_volume,
       count(
               CASE
                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 6::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                         59::numeric AND
                         EXTRACT(second FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         59::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 7::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 8::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 9::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                       THEN 1
                   ELSE NULL::integer
                   END)                                                AS morning_session_candles,
       CASE
           WHEN count(
                        CASE
                            WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                 1::numeric AND
                                 EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                 5::numeric AND
                                 (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  6::numeric AND
                                  EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  59::numeric AND
                                  EXTRACT(second FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                  59::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                  7::numeric AND
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  8::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  9::numeric AND
                                  EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  59::numeric) THEN 1
                            ELSE NULL::integer
                            END) > 0 THEN round(sum(
                                                        CASE
                                                            WHEN EXTRACT(dow FROM
                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                 1::numeric AND EXTRACT(dow FROM
                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                5::numeric AND (EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                6::numeric AND
                                                                                                EXTRACT(minute FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                59::numeric AND
                                                                                                EXTRACT(second FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                59::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                7::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                8::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                9::numeric AND
                                                                                                EXTRACT(minute FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                59::numeric)
                                                                THEN minute_candles.volume
                                                            ELSE 0::bigint
                                                            END) / count(
                                                        CASE
                                                            WHEN EXTRACT(dow FROM
                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                 1::numeric AND EXTRACT(dow FROM
                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                5::numeric AND (EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                6::numeric AND
                                                                                                EXTRACT(minute FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                59::numeric AND
                                                                                                EXTRACT(second FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                59::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                7::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                8::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                9::numeric AND
                                                                                                EXTRACT(minute FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                59::numeric) THEN 1
                                                            ELSE NULL::integer
                                                            END)::numeric, 2)
           ELSE 0::numeric
           END                                                         AS morning_avg_volume_per_candle,
       sum(
               CASE
                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         10::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 17::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 18::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                       THEN minute_candles.volume
                   ELSE 0::bigint
                   END)                                                AS main_session_volume,
       count(
               CASE
                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         10::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 17::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 18::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                       THEN 1
                   ELSE NULL::integer
                   END)                                                AS main_session_candles,
       CASE
           WHEN count(
                        CASE
                            WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                 1::numeric AND
                                 EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                 5::numeric AND
                                 (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                  10::numeric AND
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  17::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  18::numeric AND
                                  EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  59::numeric) THEN 1
                            ELSE NULL::integer
                            END) > 0 THEN round(sum(
                                                        CASE
                                                            WHEN EXTRACT(dow FROM
                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                 1::numeric AND EXTRACT(dow FROM
                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                5::numeric AND (EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                10::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                17::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                18::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                59::numeric)
                                                                THEN minute_candles.volume
                                                            ELSE 0::bigint
                                                            END) / count(
                                                        CASE
                                                            WHEN EXTRACT(dow FROM
                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                 1::numeric AND EXTRACT(dow FROM
                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                5::numeric AND (EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                10::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                17::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                18::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                59::numeric) THEN 1
                                                            ELSE NULL::integer
                                                            END)::numeric, 2)
           ELSE 0::numeric
           END                                                         AS main_avg_volume_per_candle,
       sum(
               CASE
                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         19::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 22::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 23::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 50::numeric)
                       THEN minute_candles.volume
                   ELSE 0::bigint
                   END)                                                AS evening_session_volume,
       count(
               CASE
                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         19::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 22::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 23::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 50::numeric)
                       THEN 1
                   ELSE NULL::integer
                   END)                                                AS evening_session_candles,
       CASE
           WHEN count(
                        CASE
                            WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                 1::numeric AND
                                 EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                 5::numeric AND
                                 (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                  19::numeric AND
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  22::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  23::numeric AND
                                  EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  50::numeric) THEN 1
                            ELSE NULL::integer
                            END) > 0 THEN round(sum(
                                                        CASE
                                                            WHEN EXTRACT(dow FROM
                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                 1::numeric AND EXTRACT(dow FROM
                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                5::numeric AND (EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                19::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                22::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                23::numeric AND
                                                                                                EXTRACT(minute FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                50::numeric)
                                                                THEN minute_candles.volume
                                                            ELSE 0::bigint
                                                            END) / count(
                                                        CASE
                                                            WHEN EXTRACT(dow FROM
                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                 1::numeric AND EXTRACT(dow FROM
                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                5::numeric AND (EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                19::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                22::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                23::numeric AND
                                                                                                EXTRACT(minute FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                50::numeric) THEN 1
                                                            ELSE NULL::integer
                                                            END)::numeric, 2)
           ELSE 0::numeric
           END                                                         AS evening_avg_volume_per_candle,
       sum(
               CASE
                   WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                         (ARRAY [0::numeric, 6::numeric])) AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         10::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 17::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 18::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                       THEN minute_candles.volume
                   ELSE 0::bigint
                   END)                                                AS weekend_exchange_session_volume,
       count(
               CASE
                   WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                         (ARRAY [0::numeric, 6::numeric])) AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                         10::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 17::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 18::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                       THEN 1
                   ELSE NULL::integer
                   END)                                                AS weekend_exchange_session_candles,
       CASE
           WHEN count(
                        CASE
                            WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                  (ARRAY [0::numeric, 6::numeric])) AND
                                 (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                  10::numeric AND
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  17::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  18::numeric AND
                                  EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  59::numeric) THEN 1
                            ELSE NULL::integer
                            END) > 0 THEN round(sum(
                                                        CASE
                                                            WHEN (EXTRACT(dow FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                  (ARRAY [0::numeric, 6::numeric])) AND
                                                                 (EXTRACT(hour FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                  10::numeric AND EXTRACT(hour FROM
                                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                  17::numeric OR EXTRACT(hour FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                 18::numeric AND
                                                                                                 EXTRACT(minute FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                 59::numeric)
                                                                THEN minute_candles.volume
                                                            ELSE 0::bigint
                                                            END) / count(
                                                        CASE
                                                            WHEN (EXTRACT(dow FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                  (ARRAY [0::numeric, 6::numeric])) AND
                                                                 (EXTRACT(hour FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                  10::numeric AND EXTRACT(hour FROM
                                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                  17::numeric OR EXTRACT(hour FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                 18::numeric AND
                                                                                                 EXTRACT(minute FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                 59::numeric) THEN 1
                                                            ELSE NULL::integer
                                                            END)::numeric, 2)
           ELSE 0::numeric
           END                                                         AS weekend_exchange_avg_volume_per_candle,
       sum(
               CASE
                   WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                         (ARRAY [0::numeric, 6::numeric])) AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 2::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 8::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 9::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                         59::numeric OR EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                        19::numeric AND
                                        EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                        22::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 23::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 50::numeric)
                       THEN minute_candles.volume
                   ELSE 0::bigint
                   END)                                                AS weekend_otc_session_volume,
       count(
               CASE
                   WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                         (ARRAY [0::numeric, 6::numeric])) AND
                        (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 2::numeric AND
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 8::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 9::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                         59::numeric OR EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                        19::numeric AND
                                        EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                        22::numeric OR
                         EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 23::numeric AND
                         EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 50::numeric)
                       THEN 1
                   ELSE NULL::integer
                   END)                                                AS weekend_otc_session_candles,
       CASE
           WHEN count(
                        CASE
                            WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                  (ARRAY [0::numeric, 6::numeric])) AND
                                 (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                  2::numeric AND
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  8::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  9::numeric AND
                                  EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  59::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                  19::numeric AND
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  22::numeric OR
                                  EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                  23::numeric AND
                                  EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                  50::numeric) THEN 1
                            ELSE NULL::integer
                            END) > 0 THEN round(sum(
                                                        CASE
                                                            WHEN (EXTRACT(dow FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                  (ARRAY [0::numeric, 6::numeric])) AND
                                                                 (EXTRACT(hour FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                  2::numeric AND EXTRACT(hour FROM
                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                 8::numeric OR EXTRACT(hour FROM
                                                                                                       (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                               9::numeric AND
                                                                                               EXTRACT(minute FROM
                                                                                                       (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                               59::numeric OR
                                                                  EXTRACT(hour FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                  19::numeric AND EXTRACT(hour FROM
                                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                  22::numeric OR EXTRACT(hour FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                 23::numeric AND
                                                                                                 EXTRACT(minute FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                 50::numeric)
                                                                THEN minute_candles.volume
                                                            ELSE 0::bigint
                                                            END) / count(
                                                        CASE
                                                            WHEN (EXTRACT(dow FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                  (ARRAY [0::numeric, 6::numeric])) AND
                                                                 (EXTRACT(hour FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                  2::numeric AND EXTRACT(hour FROM
                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                 8::numeric OR EXTRACT(hour FROM
                                                                                                       (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                               9::numeric AND
                                                                                               EXTRACT(minute FROM
                                                                                                       (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                               59::numeric OR
                                                                  EXTRACT(hour FROM
                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                  19::numeric AND EXTRACT(hour FROM
                                                                                          (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                  22::numeric OR EXTRACT(hour FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                 23::numeric AND
                                                                                                 EXTRACT(minute FROM
                                                                                                         (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                 50::numeric) THEN 1
                                                            ELSE NULL::integer
                                                            END)::numeric, 2)
           ELSE 0::numeric
           END                                                         AS weekend_otc_avg_volume_per_candle,
       -- Подсчет количества дней
       count(DISTINCT DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) AS total_days,
       count(DISTINCT CASE
                          WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                               EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric
                          THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                          ELSE NULL
                          END) AS working_days,
       count(DISTINCT CASE
                          WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                               (ARRAY [0::numeric, 6::numeric])
                          THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                          ELSE NULL
                          END) AS weekend_days,
       -- Средние объемы за день для каждой сессии
       CASE
           WHEN count(DISTINCT CASE
                                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric
                                   THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                   ELSE NULL
                                   END) > 0 THEN round(sum(
                                                           CASE
                                                               WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                                                    EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                                                                    (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 6::numeric AND
                                                                     EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 59::numeric AND
                                                                     EXTRACT(second FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 59::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 7::numeric AND
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 8::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 9::numeric AND
                                                                     EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                                                               THEN minute_candles.volume
                                                               ELSE 0::bigint
                                                               END) / count(DISTINCT CASE
                                                                                         WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                                                                              EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric
                                                                                         THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                                                                         ELSE NULL
                                                                                         END)::numeric, 2)
           ELSE 0::numeric
           END AS morning_avg_volume_per_day,
       CASE
           WHEN count(DISTINCT CASE
                                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric
                                   THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                   ELSE NULL
                                   END) > 0 THEN round(sum(
                                                           CASE
                                                               WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                                                    EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                                                                    (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 10::numeric AND
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 17::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 18::numeric AND
                                                                     EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                                                               THEN minute_candles.volume
                                                               ELSE 0::bigint
                                                               END) / count(DISTINCT CASE
                                                                                         WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                                                                              EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric
                                                                                         THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                                                                         ELSE NULL
                                                                                         END)::numeric, 2)
           ELSE 0::numeric
           END AS main_avg_volume_per_day,
       CASE
           WHEN count(DISTINCT CASE
                                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                        EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric
                                   THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                   ELSE NULL
                                   END) > 0 THEN round(sum(
                                                           CASE
                                                               WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                                                    EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric AND
                                                                    (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 19::numeric AND
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 22::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 23::numeric AND
                                                                     EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 50::numeric)
                                                               THEN minute_candles.volume
                                                               ELSE 0::bigint
                                                               END) / count(DISTINCT CASE
                                                                                         WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 1::numeric AND
                                                                                              EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 5::numeric
                                                                                         THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                                                                         ELSE NULL
                                                                                         END)::numeric, 2)
           ELSE 0::numeric
           END AS evening_avg_volume_per_day,
       CASE
           WHEN count(DISTINCT CASE
                                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                        (ARRAY [0::numeric, 6::numeric])
                                   THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                   ELSE NULL
                                   END) > 0 THEN round(sum(
                                                           CASE
                                                               WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                    (ARRAY [0::numeric, 6::numeric])) AND
                                                                    (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 10::numeric AND
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 17::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 18::numeric AND
                                                                     EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric)
                                                               THEN minute_candles.volume
                                                               ELSE 0::bigint
                                                               END) / count(DISTINCT CASE
                                                                                         WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                                              (ARRAY [0::numeric, 6::numeric])
                                                                                         THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                                                                         ELSE NULL
                                                                                         END)::numeric, 2)
           ELSE 0::numeric
           END AS weekend_exchange_avg_volume_per_day,
       CASE
           WHEN count(DISTINCT CASE
                                   WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                        (ARRAY [0::numeric, 6::numeric])
                                   THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                   ELSE NULL
                                   END) > 0 THEN round(sum(
                                                           CASE
                                                               WHEN (EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                    (ARRAY [0::numeric, 6::numeric])) AND
                                                                    (EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 2::numeric AND
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 8::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 9::numeric AND
                                                                     EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 59::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >= 19::numeric AND
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 22::numeric OR
                                                                     EXTRACT(hour FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = 23::numeric AND
                                                                     EXTRACT(minute FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <= 50::numeric)
                                                               THEN minute_candles.volume
                                                               ELSE 0::bigint
                                                               END) / count(DISTINCT CASE
                                                                                         WHEN EXTRACT(dow FROM (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = ANY
                                                                                              (ARRAY [0::numeric, 6::numeric])
                                                                                         THEN DATE(minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)
                                                                                         ELSE NULL
                                                                                         END)::numeric, 2)
           ELSE 0::numeric
           END AS weekend_otc_avg_volume_per_day,
       (min(minute_candles."time") AT TIME ZONE 'Europe/Moscow'::text) AS first_candle_time,
       (max(minute_candles."time") AT TIME ZONE 'Europe/Moscow'::text) AS last_candle_time,
       (now() AT TIME ZONE 'Europe/Moscow'::text)                      AS last_updated
FROM invest.minute_candles
         LEFT JOIN invest.shares s ON minute_candles.figi::text = s.figi::text
         LEFT JOIN invest.futures f ON minute_candles.figi::text = f.figi::text
GROUP BY minute_candles.figi, s.figi, f.figi
ORDER BY minute_candles.figi;

-- Установка владельца материализованного представления
ALTER MATERIALIZED VIEW invest_views.history_volume_aggregation OWNER TO postgres;

-- Предоставление прав на материализованное представление
GRANT SELECT ON invest_views.history_volume_aggregation TO tester;
GRANT DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON invest_views.history_volume_aggregation TO admin;

-- Создание синонима в схеме invest для обратной совместимости
CREATE OR REPLACE VIEW invest.history_volume_aggregation AS
SELECT * FROM invest_views.history_volume_aggregation;

-- Установка владельца синонима
ALTER VIEW invest.history_volume_aggregation OWNER TO postgres;

-- Предоставление прав на синоним
GRANT SELECT ON invest.history_volume_aggregation TO tester;
GRANT SELECT ON invest.history_volume_aggregation TO admin;

-- Создание индекса для улучшения производительности
CREATE INDEX IF NOT EXISTS idx_history_volume_aggregation_figi 
ON invest_views.history_volume_aggregation (figi);

-- Создание индекса по типу инструмента
CREATE INDEX IF NOT EXISTS idx_history_volume_aggregation_instrument_type 
ON invest_views.history_volume_aggregation (instrument_type);

-- Комментарии для документации
COMMENT ON MATERIALIZED VIEW invest_views.history_volume_aggregation IS 
'Материализованное представление для агрегации исторических данных объемов торгов по инструментам с разбивкой по торговым сессиям';

COMMENT ON VIEW invest.history_volume_aggregation IS 
'Синоним для invest_views.history_volume_aggregation для обратной совместимости';