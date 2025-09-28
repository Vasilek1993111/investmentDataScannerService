-- Создание синонимов для таблиц свечей
CREATE OR REPLACE VIEW invest.minute_candles AS 
SELECT * FROM invest_candles.minute_candles;

CREATE OR REPLACE VIEW invest.daily_candles AS 
SELECT * FROM invest_candles.daily_candles;

COMMENT ON VIEW invest.minute_candles IS 'Синоним для таблицы invest_candles.minute_candles - минутные свечи';
COMMENT ON VIEW invest.daily_candles IS 'Синоним для таблицы invest_candles.daily_candles - дневные свечи';

ALTER VIEW invest.minute_candles OWNER TO postgres;
ALTER VIEW invest.daily_candles OWNER TO postgres;

-- Создание синонимов для таблиц из invest_utils
CREATE OR REPLACE VIEW invest.data_quality_issues AS 
SELECT * FROM invest_utils.data_quality_issues;

CREATE OR REPLACE VIEW invest.index_session_times AS 
SELECT * FROM invest_utils.index_session_times;

CREATE OR REPLACE VIEW invest.system_logs AS 
SELECT * FROM invest_utils.system_logs;

COMMENT ON VIEW invest.data_quality_issues IS 'Синоним для таблицы invest_utils.data_quality_issues - проблемы качества данных';
COMMENT ON VIEW invest.index_session_times IS 'Синоним для таблицы invest_utils.index_session_times - времена торговых сессий индексов';
COMMENT ON VIEW invest.system_logs IS 'Синоним для таблицы invest_utils.system_logs - системные логи';

ALTER VIEW invest.data_quality_issues OWNER TO postgres;
ALTER VIEW invest.index_session_times OWNER TO postgres;
ALTER VIEW invest.system_logs OWNER TO postgres;

-- Создание синонима для таблицы close_prices_evening_session
CREATE OR REPLACE VIEW invest.close_prices_evening_session AS 
SELECT * FROM invest_prices.close_prices_evening_session;

COMMENT ON VIEW invest.close_prices_evening_session IS 'Синоним для таблицы invest_prices.close_prices_evening_session - цены закрытия вечерней сессии';

ALTER VIEW invest.close_prices_evening_session OWNER TO postgres;

-- Создание синонима для таблицы trades
CREATE OR REPLACE VIEW invest.trades AS 
SELECT * FROM invest_prices.trades;

COMMENT ON VIEW invest.trades IS 'Синоним для таблицы invest_prices.trades - данные о сделках';

ALTER VIEW invest.trades OWNER TO postgres;

-- Создание синонима для today_volume_view
CREATE OR REPLACE VIEW invest.today_volume_view AS 
SELECT * FROM invest_views.today_volume_view;

COMMENT ON VIEW invest.today_volume_view IS 'Синоним для invest_views.today_volume_view - агрегированные объемы за сегодня';

ALTER VIEW invest.today_volume_view OWNER TO postgres;

-- Создание синонима для функции run_daily_vs_minute_check
CREATE OR REPLACE FUNCTION invest.run_daily_vs_minute_check(p_date DATE)
RETURNS TABLE(
    task_id TEXT,
    total_rows BIGINT,
    mismatches BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT * FROM invest_dq.run_daily_vs_minute_check(p_date);
END;
$$;

COMMENT ON FUNCTION invest.run_daily_vs_minute_check(DATE) IS 'Синоним для invest_dq.run_daily_vs_minute_check - проверка качества данных между дневными и минутными свечами с ограничением времени в выходные дни (выходные: 9:59-18:59, рабочие дни: без ограничений) и логированием результатов в system_logs';

ALTER FUNCTION invest.run_daily_vs_minute_check(DATE) OWNER TO postgres;

--Вью с агрегированными объемами за сегодня
create view invest_views.today_volume_view
            (figi, instrument_type, trade_date, total_volume, total_candles, avg_volume_per_candle,
             morning_session_volume, morning_session_candles, morning_avg_volume_per_candle, main_session_volume,
             main_session_candles, main_avg_volume_per_candle, evening_session_volume, evening_session_candles,
             evening_avg_volume_per_candle, weekend_exchange_session_volume, weekend_exchange_session_candles,
             weekend_exchange_avg_volume_per_candle, weekend_otc_session_volume, weekend_otc_session_candles,
             weekend_otc_avg_volume_per_candle, first_candle_time, last_candle_time, last_updated)
as
SELECT minute_candles.figi,
       CASE
           WHEN s.figi IS NOT NULL THEN 'share'::text
           WHEN f.figi IS NOT NULL THEN 'future'::text
           ELSE 'unknown'::text
           END                                           AS instrument_type,
       (CURRENT_DATE AT TIME ZONE 'Europe/Moscow'::text) AS trade_date,
       sum(minute_candles.volume)                        AS total_volume,
       count(*)                                          AS total_candles,
       round(avg(minute_candles.volume), 2)              AS avg_volume_per_candle,
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
                   END)                                  AS morning_session_volume,
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
                   END)                                  AS morning_session_candles,
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
           END                                           AS morning_avg_volume_per_candle,
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
                   END)                                  AS main_session_volume,
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
                   END)                                  AS main_session_candles,
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
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) >=
                                                                                                10::numeric AND
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                17::numeric OR
                                                                                                EXTRACT(hour FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) =
                                                                                                18::numeric AND
                                                                                                EXTRACT(minute FROM
                                                                                                        (minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) <=
                                                                                                59::numeric) THEN 1
                                                            ELSE NULL::integer
                                                            END)::numeric, 2)
           ELSE 0::numeric
           END                                           AS main_avg_volume_per_candle,
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
                   END)                                  AS evening_session_volume,
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
                   END)                                  AS evening_session_candles,
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
           END                                           AS evening_avg_volume_per_candle,
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
                   END)                                  AS weekend_exchange_session_volume,
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
                   END)                                  AS weekend_exchange_session_candles,
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
           END                                           AS weekend_exchange_avg_volume_per_candle,
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
                   END)                                  AS weekend_otc_session_volume,
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
                   END)                                  AS weekend_otc_session_candles,
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
           END                                           AS weekend_otc_avg_volume_per_candle,
       min(minute_candles."time")                        AS first_candle_time,
       max(minute_candles."time")                        AS last_candle_time,
       (now() AT TIME ZONE 'Europe/Moscow'::text)        AS last_updated
FROM invest.minute_candles
         LEFT JOIN invest.shares s ON minute_candles.figi::text = s.figi::text
         LEFT JOIN invest.futures f ON minute_candles.figi::text = f.figi::text
WHERE
    date((minute_candles."time" AT TIME ZONE 'Europe/Moscow'::text)) = (CURRENT_DATE AT TIME ZONE 'Europe/Moscow'::text)
GROUP BY minute_candles.figi, s.figi, f.figi
ORDER BY minute_candles.figi;

comment on view invest_views.today_volume_view is 'Вью с агрегированными объемами за сегодня по всем торговым сессиям (использует синонимы)';

comment on column invest_views.today_volume_view.figi is 'Уникальный идентификатор инструмента (Financial Instrument Global Identifier)';
comment on column invest_views.today_volume_view.instrument_type is 'Тип инструмента: share, future, unknown';
comment on column invest_views.today_volume_view.trade_date is 'Дата торгов (текущая дата в московском времени)';
comment on column invest_views.today_volume_view.total_volume is 'Общий объем торгов за день';
comment on column invest_views.today_volume_view.total_candles is 'Общее количество свечей за день';
comment on column invest_views.today_volume_view.avg_volume_per_candle is 'Средний объем на свечу';
comment on column invest_views.today_volume_view.morning_session_volume is 'Объем утренней сессии (7:00-9:59)';
comment on column invest_views.today_volume_view.main_session_volume is 'Объем основной сессии (10:00-18:59)';
comment on column invest_views.today_volume_view.evening_session_volume is 'Объем вечерней сессии (19:00-23:50)';
comment on column invest_views.today_volume_view.weekend_exchange_session_volume is 'Объем выходной биржевой сессии';
comment on column invest_views.today_volume_view.weekend_otc_session_volume is 'Объем выходной внебиржевой сессии';

alter view invest_views.today_volume_view owner to postgres;


create view last_prices_daily_partition_stats(schemaname, partition_name, partition_date, size, size_bytes) as
SELECT pg_tables.schemaname,
       pg_tables.tablename                                                                                          AS partition_name,
       to_date("substring"(pg_tables.tablename::text, 'last_prices_(\d{4}_\d{2}_\d{2})'::text),
               'YYYY_MM_DD'::text)                                                                                  AS partition_date,
       pg_size_pretty(pg_total_relation_size(((pg_tables.schemaname::text || '.'::text) ||
                                              pg_tables.tablename::text)::regclass))                                AS size,
       pg_total_relation_size(((pg_tables.schemaname::text || '.'::text) ||
                               pg_tables.tablename::text)::regclass)                                                AS size_bytes
FROM pg_tables
WHERE pg_tables.schemaname = 'invest'::name
  AND pg_tables.tablename ~~ 'last_prices_%'::text
  AND pg_tables.tablename ~ '^last_prices_\d{4}_\d{2}_\d{2}$'::text
ORDER BY (to_date("substring"(pg_tables.tablename::text, 'last_prices_(\d{4}_\d{2}_\d{2})'::text), 'YYYY_MM_DD'::text))
    DESC;

comment on view last_prices_daily_partition_stats is 'Статистика по дневным партициям таблицы last_prices';

alter table last_prices_daily_partition_stats
    owner to postgres;

grant select on last_prices_daily_partition_stats to tester;

grant delete, insert, references, select, trigger, truncate, update on last_prices_daily_partition_stats to admin;



