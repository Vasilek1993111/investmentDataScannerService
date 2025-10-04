-- Создание схемы для материализованных представлений
CREATE SCHEMA IF NOT EXISTS invest_views;

-- Установка владельца схемы
ALTER SCHEMA invest_views OWNER TO postgres;

-- Предоставление прав на схему
GRANT USAGE ON SCHEMA invest_views TO tester;
GRANT USAGE ON SCHEMA invest_views TO admin;

--Материализованное представление history_volume_aggregation в схеме invest_views





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