-- ============================================================================
-- НАСТРОЙКА ЗАДАНИЙ CRON ДЛЯ АВТОМАТИЧЕСКОГО ВЫПОЛНЕНИЯ ПРОЦЕДУР
-- ============================================================================
-- Требуется расширение pg_cron: CREATE EXTENSION IF NOT EXISTS pg_cron;
-- ============================================================================

-- ============================================================================
-- ЗАДАНИЕ: Запуск проверки соответствия дневных и минутных свечей
-- ============================================================================
-- Описание: Проверяет соответствие дневных и минутных свечей за предыдущий день
--           Выполняется каждый день в 4:00 утра по времени сервера PostgreSQL
--           Проверяет данные за вчерашний день (CURRENT_DATE - 1)
-- ============================================================================

-- Настройка автоматического запуска процедуры run_daily_vs_minute_check
-- Каждый день в 04:00 по времени сервера PostgreSQL
-- Проверяем, не существует ли уже задача с таким именем
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM cron.job 
        WHERE jobname = 'run-daily-vs-minute-check'
    ) THEN
        PERFORM cron.schedule(
            'run-daily-vs-minute-check'::text,
            '0 4 * * *'::text,  -- cron выражение: минута 0, час 4, каждый день
            'SELECT invest_candles.run_daily_vs_minute_check(CURRENT_DATE - 1);'::text
        );
        RAISE NOTICE 'Задание cron "run-daily-vs-minute-check" успешно создано';
    ELSE
        RAISE NOTICE 'Задание cron "run-daily-vs-minute-check" уже существует';
    END IF;
END $$;

-- Комментарий: Настроено автоматическое выполнение функции invest_candles.run_daily_vs_minute_check 
--              каждый день в 04:00 для проверки данных за предыдущий день

-- ============================================================================
-- ЗАДАНИЕ: Обновление материализованного представления historical_price_extremes
-- ============================================================================
-- Описание: Обновляет материализованное представление с историческими экстремумами цен
--           Выполняется каждый день в 5:10 утра по времени сервера PostgreSQL
--           Обновляет данные на основе дневных свечей
-- ============================================================================

-- Настройка автоматического обновления материализованного представления
-- Каждый день в 05:10 по времени сервера PostgreSQL
-- Если задание уже существует с другим временем, сначала удаляем его, затем создаем новое
DO $$
BEGIN
    -- Удаляем существующее задание, если оно есть (для обновления времени)
    IF EXISTS (
        SELECT 1 FROM cron.job 
        WHERE jobname = 'refresh-historical-price-extremes'
    ) THEN
        PERFORM cron.unschedule('refresh-historical-price-extremes');
        RAISE NOTICE 'Существующее задание "refresh-historical-price-extremes" удалено для обновления времени';
    END IF;
    
    -- Создаем новое задание с нужным временем
    PERFORM cron.schedule(
        'refresh-historical-price-extremes'::text,
        '10 5 * * *'::text,  -- cron выражение: минута 10, час 5, каждый день
        'REFRESH MATERIALIZED VIEW invest_views.historical_price_extremes;'::text
    );
    RAISE NOTICE 'Задание cron "refresh-historical-price-extremes" успешно создано на время 05:10';
END $$;

-- Комментарий: Настроено автоматическое обновление материализованного представления 
--              invest_views.historical_price_extremes каждый день в 05:10

-- ============================================================================
-- ЗАДАНИЕ: Обновление материализованного представления history_volume_aggregation
-- ============================================================================
-- Описание: Обновляет материализованное представление с агрегацией объемов торгов
--           Выполняется каждый день в 5:20 утра по времени сервера PostgreSQL
--           Обновляет данные на основе минутных свечей
-- ============================================================================

-- Настройка автоматического обновления материализованного представления
-- Каждый день в 05:20 по времени сервера PostgreSQL
-- Если задание уже существует с другим временем, сначала удаляем его, затем создаем новое
DO $$
BEGIN
    -- Удаляем существующее задание, если оно есть (для обновления времени)
    IF EXISTS (
        SELECT 1 FROM cron.job 
        WHERE jobname = 'refresh-history-volume-aggregation'
    ) THEN
        PERFORM cron.unschedule('refresh-history-volume-aggregation');
        RAISE NOTICE 'Существующее задание "refresh-history-volume-aggregation" удалено для обновления времени';
    END IF;
    
    -- Создаем новое задание с нужным временем
    PERFORM cron.schedule(
        'refresh-history-volume-aggregation'::text,
        '20 5 * * *'::text,  -- cron выражение: минута 20, час 5, каждый день
        'REFRESH MATERIALIZED VIEW history_volume_aggregation;'::text
    );
    RAISE NOTICE 'Задание cron "refresh-history-volume-aggregation" успешно создано на время 05:20';
END $$;

-- Комментарий: Настроено автоматическое обновление материализованного представления 
--              history_volume_aggregation каждый день в 05:20

-- ============================================================================
-- ПОЛЕЗНЫЕ КОМАНДЫ ДЛЯ УПРАВЛЕНИЯ ЗАДАНИЯМИ
-- ============================================================================

-- Проверка настроенных задач (для отладки)
SELECT * FROM cron.job WHERE jobname = 'run-daily-vs-minute-check';

-- Просмотр всех заданий cron
SELECT * FROM cron.job ORDER BY jobname;

-- Просмотр истории выполнения задания
SELECT * FROM cron.job_run_details WHERE jobid = (SELECT jobid FROM cron.job WHERE jobname = 'run-daily-vs-minute-check') ORDER BY start_time DESC LIMIT 10;

-- Обновление расписания задания (изменить время на 5:00 утра)
SELECT cron.unschedule('run-daily-vs-minute-check');
SELECT cron.schedule('run-daily-vs-minute-check', '0 5 * * *', 'SELECT invest_candles.run_daily_vs_minute_check(CURRENT_DATE - 1);');

-- Удаление задания (если нужно)
SELECT cron.unschedule('run-daily-vs-minute-check');

-- Ручной запуск задания для проверки
SELECT invest_candles.run_daily_vs_minute_check(CURRENT_DATE - 1);

-- ============================================================================
-- УПРАВЛЕНИЕ ЗАДАНИЕМ ОБНОВЛЕНИЯ MATERIALIZED VIEW
-- ============================================================================

-- Проверка задания обновления материализованного представления
SELECT * FROM cron.job WHERE jobname = 'refresh-historical-price-extremes';

-- Просмотр истории выполнения задания
SELECT * FROM cron.job_run_details 
WHERE jobid = (SELECT jobid FROM cron.job WHERE jobname = 'refresh-historical-price-extremes') 
ORDER BY start_time DESC 
LIMIT 10;

-- Обновление времени выполнения задания (например, изменить на 6:00)
-- SELECT cron.unschedule('refresh-historical-price-extremes');
-- SELECT cron.schedule('refresh-historical-price-extremes', '0 6 * * *', 'REFRESH MATERIALIZED VIEW invest_views.historical_price_extremes;');

-- Удаление задания (если нужно)
-- SELECT cron.unschedule('refresh-historical-price-extremes');

-- Ручное обновление материализованного представления
-- REFRESH MATERIALIZED VIEW invest_views.historical_price_extremes;

-- ============================================================================
-- УПРАВЛЕНИЕ ЗАДАНИЕМ ОБНОВЛЕНИЯ history_volume_aggregation
-- ============================================================================

-- Проверка задания обновления материализованного представления
SELECT * FROM cron.job WHERE jobname = 'refresh-history-volume-aggregation';

-- Просмотр истории выполнения задания
SELECT * FROM cron.job_run_details 
WHERE jobid = (SELECT jobid FROM cron.job WHERE jobname = 'refresh-history-volume-aggregation') 
ORDER BY start_time DESC 
LIMIT 10;

-- Обновление времени выполнения задания (например, изменить на 6:00)
-- SELECT cron.unschedule('refresh-history-volume-aggregation');
-- SELECT cron.schedule('refresh-history-volume-aggregation', '0 6 * * *', 'REFRESH MATERIALIZED VIEW history_volume_aggregation;');

-- Удаление задания (если нужно)
-- SELECT cron.unschedule('refresh-history-volume-aggregation');

-- Ручное обновление материализованного представления
-- REFRESH MATERIALIZED VIEW history_volume_aggregation;

-- ============================================================================
-- ПРИМЕЧАНИЯ
-- ============================================================================
-- 1. Расширение pg_cron должно быть установлено: CREATE EXTENSION IF NOT EXISTS pg_cron;
-- 2. Время выполнения указано в часовом поясе сервера PostgreSQL
-- 3. Функция проверяет данные за предыдущий день (CURRENT_DATE - 1), так как выполняется утром
-- 4. Результаты проверки сохраняются в таблицу invest_utils.data_quality_issues
-- 5. Для изменения времени выполнения используйте команду обновления расписания выше
