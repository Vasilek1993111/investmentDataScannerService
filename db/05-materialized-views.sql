--Материализованное представление daily_volume_aggregation
create materialized view history_volume_aggregation as
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
       (min(minute_candles."time") AT TIME ZONE 'Europe/Moscow'::text) AS first_candle_time,
       (max(minute_candles."time") AT TIME ZONE 'Europe/Moscow'::text) AS last_candle_time,
       (now() AT TIME ZONE 'Europe/Moscow'::text)                      AS last_updated
FROM minute_candles
         LEFT JOIN shares s ON minute_candles.figi::text = s.figi::text
         LEFT JOIN futures f ON minute_candles.figi::text = f.figi::text
GROUP BY minute_candles.figi, s.figi, f.figi
ORDER BY minute_candles.figi