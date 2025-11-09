// Элементы DOM
const connectBtn = document.getElementById('connectBtn');
const disconnectBtn = document.getElementById('disconnectBtn');
const connectionStatus = document.getElementById('connectionStatus');
const stockNearFuturesTableBody = document.getElementById('stockNearFuturesTableBody');
const stockFarFuturesTableBody = document.getElementById('stockFarFuturesTableBody');
const nearFarFuturesTableBody = document.getElementById('nearFarFuturesTableBody');
const activeInstruments = document.getElementById('activeInstruments');
const totalVolume = document.getElementById('totalVolume');
const updateRate = document.getElementById('updateRate');
const lastUpdate = document.getElementById('lastUpdate');

// Элементы для полоски индексов
const indicesContainer = document.getElementById('indicesContainer');

// Функция для определения порта WebSocket
function getWebSocketPort() {
    const currentPort = window.location.port;
    if (currentPort === '8088') return '8088';
    if (currentPort === '8085') return '8085';
    return '8088';
}

// Состояние
let websocket = null;
let isConnected = false;
let quotes = new Map();
let stockNearFutures = [];
let stockFarFutures = [];
let nearFarFutures = [];
let updateCount = 0;
let lastUpdateTime = null;
let updateTimer = null;
let baseVolumeCache = new Map();
let incrementVolumeCache = new Map();
let totalVolumeCache = new Map();
let previousValues = new Map();

// Индексы для полоски (используются из indices-bar.js)
// Переменные indices и INDICES_CONFIG объявлены в indices-bar.js

// Настройки сортировки
let stockNearSortBy = 'spreadPercent';
let stockNearSortOrder = 'desc';
let stockNearMaxResults = 15;
let stockFarSortBy = 'spreadPercent';
let stockFarSortOrder = 'desc';
let stockFarMaxResults = 15;
let nearFarSortBy = 'spreadPercent';
let nearFarSortOrder = 'desc';
let nearFarMaxResults = 15;

// Глобальные переменные для кэширования данных
let historyVolumeData = null;
let todayVolumeData = null;
let isSessionActive = false;
let isTestModeGlobal = false;

// Кэш данных о фьючерсах (FIGI -> данные о фьючерсе)
let futuresDataCache = new Map();

// Ключевая ставка ЦБ РФ (в процентах)
let keyRate = 16.5;

/**
 * Загрузить данные о фьючерсах с сервера
 */
async function loadFuturesData() {
    try {
        console.log('Loading futures data...');
        const response = await fetch('/api/scanner/futures');
        if (!response.ok) {
            console.warn('Failed to load futures data:', response.status);
            return;
        }
        const data = await response.json();

        if (data && data.futures && Array.isArray(data.futures)) {
            let loadedWithBasicAssetSize = 0;
            let loadedWithDefault = 0;

            data.futures.forEach(future => {
                // Получаем basicAssetSize, пробуя все возможные варианты названия поля
                // Spring Boot Jackson может сериализовать в camelCase (basicAssetSize) или snake_case (basic_asset_size)
                let assetSize = future.basicAssetSize !== undefined ? future.basicAssetSize :
                    (future.basic_asset_size !== undefined ? future.basic_asset_size :
                        (future['basicAssetSize'] !== undefined ? future['basicAssetSize'] :
                            future['basic_asset_size']));

                // Проверяем, может быть значение есть, но равно 0, null или undefined
                let basicAssetSizeValue = 100; // значение по умолчанию

                // Проверяем, есть ли значение (включая проверку на 0, так как 0 - невалидное значение для лотности)
                if (assetSize !== null && assetSize !== undefined && assetSize !== '' && assetSize !== 0) {
                    const numValue = Number(assetSize);
                    if (Number.isFinite(numValue) && numValue > 0) {
                        basicAssetSizeValue = numValue;
                        loadedWithBasicAssetSize++;
                    } else {
                        // Логируем случаи, когда значение есть, но невалидно
                        console.warn(`Invalid basicAssetSize for ${future.ticker} (${future.figi}): ${assetSize} (type: ${typeof assetSize}), using default 100`);
                        loadedWithDefault++;
                    }
                } else {
                    // Если basicAssetSize отсутствует, пробуем использовать поле 'lot' как резервный вариант
                    // (хотя обычно lot - это размер лота, а не лотность базового актива)
                    const lotValue = future.lot !== undefined ? future.lot : future['lot'];
                    if (lotValue !== null && lotValue !== undefined && lotValue !== '' && lotValue !== 0) {
                        const numLot = Number(lotValue);
                        if (Number.isFinite(numLot) && numLot > 0) {
                            basicAssetSizeValue = numLot;
                            loadedWithBasicAssetSize++;
                            console.warn(`Using 'lot' field as fallback for basicAssetSize for ${future.ticker} (${future.figi}): ${numLot}`);
                        } else {
                            loadedWithDefault++;
                        }
                    } else {
                        loadedWithDefault++;
                    }

                    // Логируем случаи, когда значение отсутствует, особенно для интересующих нас инструментов
                    if (future.ticker && (future.ticker.includes('RNFT') || future.ticker.includes('SBER') ||
                        future.ticker.includes('SRZ') || future.ticker.includes('RUZ') ||
                        future.ticker.includes('Z5') || future.ticker.includes('H6'))) {
                        console.warn(`Missing basicAssetSize and lot for ${future.ticker} (${future.figi}), available fields:`, Object.keys(future));
                        // Выводим все числовые поля, которые могут быть лотностью
                        Object.keys(future).forEach(key => {
                            if (typeof future[key] === 'number' || (typeof future[key] === 'string' && !isNaN(future[key]))) {
                                console.log(`  ${key} = ${future[key]}`);
                            }
                        });
                    }
                }

                futuresDataCache.set(future.figi, {
                    figi: future.figi,
                    ticker: future.ticker,
                    expirationDate: future.expirationDate,
                    basicAsset: future.basicAsset,
                    assetType: future.assetType,
                    basicAssetSize: basicAssetSizeValue
                });
            });

            console.log(`Loaded ${data.futures.length} futures with expiration data`);
            console.log(`Futures cache size: ${futuresDataCache.size}`);
            console.log(`Futures with basicAssetSize: ${loadedWithBasicAssetSize}, using default (100): ${loadedWithDefault}`);

            // Выводим примеры для отладки - показываем несколько фьючерсов с разными ticker
            if (data.futures.length > 0) {
                const samples = data.futures.slice(0, Math.min(5, data.futures.length));
                samples.forEach(sample => {
                    console.log(`Future data for ${sample.ticker} (${sample.figi}):`, {
                        figi: sample.figi,
                        ticker: sample.ticker,
                        basicAsset: sample.basicAsset,
                        expirationDate: sample.expirationDate,
                        basicAssetSize_raw: sample.basicAssetSize || sample.basic_asset_size || 'NOT FOUND',
                        allFields: Object.keys(sample)
                    });
                });
            }
        } else {
            console.warn('Futures data is missing or invalid:', data);
        }
    } catch (error) {
        console.error('Error loading futures data:', error);
    }
}

/**
 * Рассчитать количество дней до экспирации фьючерса (число)
 */
function getDaysToExpirationNumber(figi) {
    const futuresData = futuresDataCache.get(figi);
    if (!futuresData || !futuresData.expirationDate) {
        return 0;
    }

    try {
        const expirationDate = new Date(futuresData.expirationDate);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        expirationDate.setHours(0, 0, 0, 0);

        const diffTime = expirationDate - today;
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        return diffDays > 0 ? diffDays : 0;
    } catch (error) {
        console.error('Error calculating expiration days:', error);
        return 0;
    }
}

/**
 * Рассчитать количество дней до экспирации фьючерса (строка для отображения)
 */
function calculateDaysToExpiration(figi) {
    const days = getDaysToExpirationNumber(figi);

    if (days === 0) {
        return 'Сегодня';
    } else if (days < 0) {
        return `Истек (${Math.abs(days)} дн. назад)`;
    } else {
        return `${days} дн.`;
    }
}

/**
 * Определить, является ли инструмент акцией
 */
function isStock(ticker, figi) {
    if (!ticker) return false;

    // Если инструмент есть в кэше фьючерсов, это не акция
    if (figi && futuresDataCache.has(figi)) {
        return false;
    }

    // Проверяем по тикеру - если заканчивается на суффикс фьючерса, это не акция
    const futuresSuffixes = ['F', 'G', 'H', 'J', 'K', 'M', 'N', 'Q', 'U', 'V', 'X', 'Z'];
    for (const suffix of futuresSuffixes) {
        if (ticker.endsWith(suffix)) {
            return false;
        }
    }

    // Проверяем специальные суффиксы Z5 и H6
    if (ticker.endsWith('Z5') || ticker.endsWith('H6')) {
        return false;
    }

    return true;
}

/**
 * Получить базовый тикер для группировки
 */
function getBaseTicker(ticker, figi) {
    if (!ticker) return null;

    // Если это фьючерс из кэша, используем basicAsset для группировки
    if (figi && futuresDataCache.has(figi)) {
        const futuresData = futuresDataCache.get(figi);
        if (futuresData && futuresData.basicAsset) {
            return futuresData.basicAsset.toUpperCase();
        }
    }

    // Убираем суффиксы фьючерсов Z5 и H6
    if (ticker.endsWith('Z5')) {
        return ticker.slice(0, -2).toUpperCase();
    }
    if (ticker.endsWith('H6')) {
        return ticker.slice(0, -2).toUpperCase();
    }

    // Для других фьючерсов убираем последний символ (месяц экспирации)
    const futuresSuffixes = ['F', 'G', 'H', 'J', 'K', 'M', 'N', 'Q', 'U', 'V', 'X', 'Z'];
    for (const suffix of futuresSuffixes) {
        if (ticker.endsWith(suffix)) {
            const withoutSuffix = ticker.slice(0, -1);
            if (withoutSuffix.length > 0 && !isNaN(withoutSuffix[withoutSuffix.length - 1])) {
                return withoutSuffix.slice(0, -1).toUpperCase();
            }
            return withoutSuffix.toUpperCase();
        }
    }

    // Для акций возвращаем тикер как есть
    return ticker.toUpperCase();
}

/**
 * Получить квартал экспирации фьючерса
 */
function getExpirationQuarter(figi) {
    const futuresData = futuresDataCache.get(figi);
    if (!futuresData || !futuresData.expirationDate) {
        return null;
    }

    try {
        const expirationDate = new Date(futuresData.expirationDate);
        const year = expirationDate.getFullYear();
        const month = expirationDate.getMonth() + 1; // 1-12

        // Определяем квартал
        let quarter;
        if (month >= 1 && month <= 3) quarter = 1;
        else if (month >= 4 && month <= 6) quarter = 2;
        else if (month >= 7 && month <= 9) quarter = 3;
        else quarter = 4;

        return { year, quarter, date: expirationDate };
    } catch (error) {
        console.error('Error getting expiration quarter:', error);
        return null;
    }
}

/**
 * Найти ближний и дальний фьючерсы для базового актива
 * Возвращает { nearFutures: figi, farFutures: figi } или null
 */
function findNearAndFarFutures(baseTicker) {
    // Собираем все фьючерсы для данного базового актива
    // Ищем фьючерсы в кэше фьючерсов, а не только в quotes (так как некоторые фьючерсы могут не иметь цен)
    const futuresForAsset = [];

    // Сначала проверяем фьючерсы в кэше
    futuresDataCache.forEach((futuresData, figi) => {
        // Проверяем, что базовый актив совпадает
        const futuresBasicAsset = futuresData.basicAsset ? futuresData.basicAsset.toUpperCase() : null;
        if (futuresBasicAsset !== baseTicker) {
            // Пробуем определить базовый тикер по тикеру фьючерса
            const baseTickerFromTicker = getBaseTicker(futuresData.ticker, figi);
            if (baseTickerFromTicker !== baseTicker) {
                return; // Пропускаем, если базовый актив не совпадает
            }
        }

        // Проверяем, что фьючерс есть в quotes (должен быть добавлен при загрузке)
        if (!quotes.has(figi)) {
            console.warn(`Futures ${futuresData.ticker} (${figi}) found in cache but not in quotes, adding it`);
            // Добавляем фьючерс в quotes, даже если для него нет цены
            const quoteData = {
                figi: figi,
                ticker: futuresData.ticker,
                currentPrice: 0,
                volume: 0,
                timestamp: new Date().toISOString()
            };
            quotes.set(figi, quoteData);
        }

        const quarter = getExpirationQuarter(figi);
        if (quarter) {
            futuresForAsset.push({
                figi,
                quarter,
                expirationDate: quarter.date
            });
        }
    });

    if (futuresForAsset.length === 0) {
        console.log(`No futures found for base ticker: ${baseTicker}`);
        return null;
    }

    console.log(`Found ${futuresForAsset.length} futures for ${baseTicker}:`, futuresForAsset.map(f => ({
        figi: f.figi,
        ticker: quotes.get(f.figi)?.ticker,
        quarter: `${f.quarter.year}Q${f.quarter.quarter}`
    })));

    // Сортируем по дате экспирации
    futuresForAsset.sort((a, b) => a.expirationDate - b.expirationDate);

    // Ближний фьючерс - первый (ближайший)
    const nearFutures = futuresForAsset[0];

    // Дальний фьючерс - следующий квартал после ближнего
    // Ищем фьючерс, экспирация которого на квартал позже ближнего
    let farFutures = null;

    const nearQuarter = nearFutures.quarter;
    for (let i = 1; i < futuresForAsset.length; i++) {
        const candidate = futuresForAsset[i];
        const candidateQuarter = candidate.quarter;

        // Проверяем, является ли кандидат следующим кварталом
        if (candidateQuarter.year === nearQuarter.year) {
            if (candidateQuarter.quarter === nearQuarter.quarter + 1) {
                farFutures = candidate;
                break;
            }
        } else if (candidateQuarter.year === nearQuarter.year + 1) {
            // Если ближний - Q4, то дальний может быть Q1 следующего года
            if (nearQuarter.quarter === 4 && candidateQuarter.quarter === 1) {
                farFutures = candidate;
                break;
            }
        }
    }

    const result = {
        nearFutures: nearFutures ? nearFutures.figi : null,
        farFutures: farFutures ? farFutures.figi : null
    };

    console.log(`Result for ${baseTicker}:`, {
        nearFutures: result.nearFutures ? quotes.get(result.nearFutures)?.ticker : null,
        farFutures: result.farFutures ? quotes.get(result.farFutures)?.ticker : null
    });

    return result;
}

/**
 * Определить, является ли фьючерс ближним для его базового актива
 */
function isNearFutures(figi) {
    if (!futuresDataCache.has(figi)) return false;

    const quote = quotes.get(figi);
    if (!quote) return false;

    const baseTicker = getBaseTicker(quote.ticker, figi);
    if (!baseTicker) return false;

    const result = findNearAndFarFutures(baseTicker);
    return result && result.nearFutures === figi;
}

/**
 * Определить, является ли фьючерс дальним для его базового актива
 */
function isFarFutures(figi) {
    if (!futuresDataCache.has(figi)) return false;

    const quote = quotes.get(figi);
    if (!quote) return false;

    const baseTicker = getBaseTicker(quote.ticker, figi);
    if (!baseTicker) return false;

    const result = findNearAndFarFutures(baseTicker);
    return result && result.farFutures === figi;
}

/**
 * Получить лотность фьючерса (basic_asset_size)
 */
function getFuturesLotSize(figi) {
    if (!figi) {
        console.warn('getFuturesLotSize called without figi');
        return 100;
    }

    if (futuresDataCache.has(figi)) {
        const futuresData = futuresDataCache.get(figi);
        if (futuresData && futuresData.basicAssetSize) {
            const lotSize = Number(futuresData.basicAssetSize);
            // Если значение валидно и больше 0, возвращаем его
            if (Number.isFinite(lotSize) && lotSize > 0) {
                return lotSize;
            } else {
                // Логируем только для проблемных инструментов
                if (futuresData.ticker && (futuresData.ticker.includes('RNFT') || futuresData.ticker.includes('RUZ') ||
                    futuresData.ticker.includes('SRZ') || futuresData.ticker.includes('SBER'))) {
                    console.warn(`Invalid lotSize for ${futuresData.ticker} (${figi}): ${futuresData.basicAssetSize}, using default 100`);
                }
            }
        } else {
            const ticker = futuresData ? futuresData.ticker : 'UNKNOWN';
            // Логируем только для проблемных инструментов
            if (ticker && (ticker.includes('RNFT') || ticker.includes('RUZ') || ticker.includes('SRZ') || ticker.includes('SBER'))) {
                console.warn(`No basicAssetSize in cache for ${ticker} (${figi}), using default 100`);
            }
        }
    } else {
        // Логируем только при первом обращении к несуществующему FIGI
        if (!getFuturesLotSize._warnedFigis) {
            getFuturesLotSize._warnedFigis = new Set();
        }
        if (!getFuturesLotSize._warnedFigis.has(figi)) {
            getFuturesLotSize._warnedFigis.add(figi);
            console.warn(`FIGI ${figi} not found in futuresDataCache, using default lot size 100`);
        }
    }

    // По умолчанию 100, если данные не найдены или значение невалидно
    return 100;
}

function connect() {
    if (isConnected) {
        console.log('Already connected, skipping');
        return;
    }

    try {
        const port = getWebSocketPort();
        console.log(`Attempting to connect WebSocket on port ${port}...`);
        websocket = new WebSocket(`ws://localhost:${port}/ws/quotes`);

        websocket.onopen = function () {
            console.log('WebSocket connected successfully');
            isConnected = true;
            connectBtn.disabled = true;
            disconnectBtn.disabled = false;
            connectionStatus.textContent = 'Подключено';
            connectionStatus.className = 'status connected';

            // Очищаем таблицы и состояние
            stockNearFuturesTableBody.innerHTML = '<tr><td colspan="10" class="no-data">Нет данных</td></tr>';
            stockFarFuturesTableBody.innerHTML = '<tr><td colspan="10" class="no-data">Нет данных</td></tr>';
            nearFarFuturesTableBody.innerHTML = '<tr><td colspan="10" class="no-data">Нет данных</td></tr>';
            quotes.clear();
            baseVolumeCache.clear();
            incrementVolumeCache.clear();
            totalVolumeCache.clear();
            previousValues.clear();
            stockNearFutures = [];
            stockFarFutures = [];
            nearFarFutures = [];
            updateCount = 0;

            // Сохраняем данные индексов при подключении, чтобы не обнулять их
            initializeIndicesBar(true);

            // Загружаем данные о фьючерсах
            loadFuturesData();

            updateTimer = setInterval(() => {
                updateRate.textContent = updateCount + '/сек';
                updateCount = 0;
            }, 1000);

            updateScannerStatus();
        };

        websocket.onmessage = function (event) {
            try {
                const quoteData = JSON.parse(event.data);
                console.log('WebSocket message received:', quoteData.ticker, quoteData.figi);
                updateQuote(quoteData);
            } catch (error) {
                console.error('Error parsing quote data:', error);
            }
        };

        websocket.onclose = function () {
            console.log('WebSocket connection closed');
            isConnected = false;
            connectBtn.disabled = false;
            disconnectBtn.disabled = true;
            connectionStatus.textContent = 'Отключено';
            connectionStatus.className = 'status disconnected';

            if (updateTimer) {
                clearInterval(updateTimer);
                updateTimer = null;
            }
        };

        websocket.onerror = function (error) {
            console.error('WebSocket error:', error);
        };
    } catch (error) {
        console.error('Connection error:', error);
    }
}

function disconnect() {
    if (websocket) {
        websocket.close();
    }
}

function updateQuote(quoteData) {
    const figi = quoteData.figi;
    console.log('updateQuote called:', quoteData.ticker, quoteData.figi, 'currentPrice:', quoteData.currentPrice);

    // Сохраняем цену ВС из предыдущей котировки, если она уже была загружена
    // Цена ВС не обновляется в течение дня, только загружается один раз при первой загрузке
    const existingQuote = quotes.get(figi);
    if (existingQuote && existingQuote.closePriceVS) {
        quoteData.closePriceVS = existingQuote.closePriceVS;
    }

    if (!quoteData.closePriceOS && !quoteData.closePrice) {
        loadClosePricesForQuote(quoteData);
    }

    // Загружаем цену закрытия вечерней сессии из кэша, если она еще не загружена
    if (!quoteData.closePriceVS) {
        loadEveningSessionPriceForQuote(quoteData);
    }

    updateVolumeDataForQuote(quoteData);
    quotes.set(figi, quoteData);

    updateCount++;
    lastUpdateTime = new Date();

    activeInstruments.textContent = quotes.size;
    updateTotalVolume();
    lastUpdate.textContent = lastUpdateTime.toLocaleTimeString();

    // Проверяем, является ли этот инструмент индексом из строки индексов
    const indexInfo = window.indices.get(figi) || (quoteData.ticker ? window.indices.get(quoteData.ticker) : null);
    if (indexInfo) {
        // Если это индекс и у него еще нет цен закрытия, загружаем их
        if (!indexInfo.closePriceOS || !indexInfo.closePriceEvening) {
            loadIndexPricesForSingleIndex(indexInfo, figi);
        }
    }

    updateIndicesBar(quoteData);
    updateFuturesComparisons();
}

function updateFuturesComparisons() {
    const quotesArray = Array.from(quotes.values());
    console.log(`updateFuturesComparisons: processing ${quotesArray.length} quotes, futures cache size: ${futuresDataCache.size}`);

    // Если кэш фьючерсов пуст, пытаемся загрузить данные
    if (futuresDataCache.size === 0) {
        console.warn('Futures cache is empty in updateFuturesComparisons, cannot proceed');
        // Не загружаем данные здесь, так как это может вызвать бесконечный цикл
        // Вместо этого просто выходим - данные должны быть загружены заранее
        return;
    }

    // Если нет котировок, ничего не делаем
    if (quotesArray.length === 0) {
        console.warn('No quotes available for comparison');
        return;
    }

    // Собираем все уникальные базовые тикеры
    const baseTickers = new Set();
    quotesArray.forEach(quote => {
        const baseTicker = getBaseTicker(quote.ticker, quote.figi);
        if (baseTicker) {
            baseTickers.add(baseTicker);
        }
    });
    console.log(`Found ${baseTickers.size} unique base tickers:`, Array.from(baseTickers).slice(0, 10));

    // Группируем инструменты по базовому тикеру
    const instrumentGroups = new Map();

    quotesArray.forEach(quote => {
        const baseTicker = getBaseTicker(quote.ticker, quote.figi);
        if (!baseTicker) return;

        if (!instrumentGroups.has(baseTicker)) {
            instrumentGroups.set(baseTicker, {
                stock: null,
                nearFuturesFigi: null,
                farFuturesFigi: null
            });
        }

        const group = instrumentGroups.get(baseTicker);

        if (isStock(quote.ticker, quote.figi)) {
            group.stock = quote;
            console.log(`Found stock: ${quote.ticker} (${quote.figi}) -> baseTicker: ${baseTicker}`);
        }
    });

    // Находим ближние и дальние фьючерсы для каждого базового актива
    baseTickers.forEach(baseTicker => {
        const futuresInfo = findNearAndFarFutures(baseTicker);
        if (futuresInfo) {
            const group = instrumentGroups.get(baseTicker);
            if (group) {
                group.nearFuturesFigi = futuresInfo.nearFutures;
                group.farFuturesFigi = futuresInfo.farFutures;
                if (futuresInfo.nearFutures) {
                    const nearQuote = quotes.get(futuresInfo.nearFutures);
                    if (nearQuote) {
                        console.log(`Found near futures for ${baseTicker}: ${nearQuote.ticker} (${futuresInfo.nearFutures})`);
                    } else {
                        console.warn(`Near futures FIGI ${futuresInfo.nearFutures} found for ${baseTicker}, but quote not found in quotes Map`);
                    }
                }
                if (futuresInfo.farFutures) {
                    const farQuote = quotes.get(futuresInfo.farFutures);
                    if (farQuote) {
                        console.log(`Found far futures for ${baseTicker}: ${farQuote.ticker} (${futuresInfo.farFutures})`);
                    } else {
                        console.warn(`Far futures FIGI ${futuresInfo.farFutures} found for ${baseTicker}, but quote not found in quotes Map`);
                    }
                }
            }
        } else {
            console.log(`No futures info found for base ticker: ${baseTicker}`);
        }
    });

    // Создаем сравнения
    const stockNearComparisons = [];
    const stockFarComparisons = [];
    const nearFarComparisons = [];

    instrumentGroups.forEach((group, baseTicker) => {
        const nearFuturesQuote = group.nearFuturesFigi ? quotes.get(group.nearFuturesFigi) : null;
        const farFuturesQuote = group.farFuturesFigi ? quotes.get(group.farFuturesFigi) : null;

        // Акция vs Ближний фьючерс
        if (group.stock && nearFuturesQuote) {
            stockNearComparisons.push(createComparison(baseTicker, group.stock, nearFuturesQuote));
            console.log(`Created stock-near comparison for ${baseTicker}`);
        }

        // Акция vs Дальний фьючерс
        if (group.stock && farFuturesQuote) {
            stockFarComparisons.push(createComparison(baseTicker, group.stock, farFuturesQuote));
            console.log(`Created stock-far comparison for ${baseTicker}`);
        }

        // Ближний vs Дальний фьючерс
        if (nearFuturesQuote && farFuturesQuote) {
            nearFarComparisons.push(createFuturesComparison(baseTicker, nearFuturesQuote, farFuturesQuote));
            console.log(`Created near-far comparison for ${baseTicker}`);
        }
    });

    console.log(`Found ${stockNearComparisons.length} stock-near, ${stockFarComparisons.length} stock-far, ${nearFarComparisons.length} near-far comparisons`);

    // Сортируем и обновляем таблицы
    stockNearFutures = sortComparisons([...stockNearComparisons], stockNearSortBy, stockNearSortOrder).slice(0, stockNearMaxResults);
    stockFarFutures = sortComparisons([...stockFarComparisons], stockFarSortBy, stockFarSortOrder).slice(0, stockFarMaxResults);
    nearFarFutures = sortComparisons([...nearFarComparisons], nearFarSortBy, nearFarSortOrder).slice(0, nearFarMaxResults);

    updateStockNearFuturesTable();
    updateStockFarFuturesTable();
    updateNearFarFuturesTable();
}

function createComparison(baseTicker, stock, futures) {
    const lotSize = getFuturesLotSize(futures.figi);
    const stockPrice = stock.currentPrice || 0;
    const futuresPrice = futures.currentPrice || 0;

    // Логируем для отладки проблемных инструментов
    if (stock.ticker && (stock.ticker.includes('RNFT') || stock.ticker.includes('SBER')) &&
        futures.ticker && (futures.ticker.includes('RUZ') || futures.ticker.includes('SRZ'))) {
        console.log(`createComparison for ${baseTicker}: stock=${stock.ticker} (${stockPrice}), futures=${futures.ticker} (${futuresPrice}), lotSize=${lotSize}`);
    }

    // Спред % = ((фьючерс / (акция * лотность)) - 1) * 100
    const spreadPercent = stockPrice > 0 && lotSize > 0 ?
        ((futuresPrice / (stockPrice * lotSize)) - 1) * 100 : 0;

    // Справедливое расхождение = ключевая ставка / 365 * количество дней до экспирации
    const daysToExpiration = getDaysToExpirationNumber(futures.figi);
    const fairSpread = daysToExpiration > 0 ? (keyRate / 365) * daysToExpiration : 0;

    // Дельта = Спред - Справедливое расхождение
    const delta = spreadPercent - fairSpread;

    return {
        baseTicker,
        stock,
        futures,
        lotSize,
        stockPrice,
        futuresPrice,
        spreadPercent,
        fairSpread: Math.round(fairSpread * 100) / 100, // Округление до сотых
        delta: Math.round(delta * 100) / 100, // Округление до сотых
        stockVolume: getDisplayVolume(stock),
        futuresVolume: getDisplayVolume(futures),
        futuresBid: futures.bestBid || null,
        futuresBidQuantity: futures.bestBidQuantity || 0,
        futuresAsk: futures.bestAsk || null,
        futuresAskQuantity: futures.bestAskQuantity || 0,
        timestamp: Math.max(
            stock.timestamp ? new Date(stock.timestamp).getTime() : 0,
            futures.timestamp ? new Date(futures.timestamp).getTime() : 0
        )
    };
}

function createFuturesComparison(baseTicker, nearFutures, farFutures) {
    const nearPrice = nearFutures.currentPrice || 0;
    const farPrice = farFutures.currentPrice || 0;

    // Спред % = ((дальний / ближний) - 1) * 100
    const spreadPercent = nearPrice > 0 ? ((farPrice / nearPrice) - 1) * 100 : 0;

    // Справедливое расхождение = ключевая ставка / 365 * разница в днях до экспирации
    const nearDays = getDaysToExpirationNumber(nearFutures.figi);
    const farDays = getDaysToExpirationNumber(farFutures.figi);
    const daysDifference = farDays > 0 && nearDays > 0 ? farDays - nearDays : 0;
    const fairSpread = daysDifference > 0 ? (keyRate / 365) * daysDifference : 0;

    // Дельта = Спред - Справедливое расхождение
    const delta = spreadPercent - fairSpread;

    return {
        baseTicker,
        nearFutures,
        farFutures,
        lotSize: getFuturesLotSize(nearFutures.figi),
        nearPrice,
        farPrice,
        spreadPercent,
        fairSpread: Math.round(fairSpread * 100) / 100, // Округление до сотых
        delta: Math.round(delta * 100) / 100, // Округление до сотых
        nearVolume: getDisplayVolume(nearFutures),
        farVolume: getDisplayVolume(farFutures),
        nearBid: nearFutures.bestBid || null,
        nearBidQuantity: nearFutures.bestBidQuantity || 0,
        nearAsk: nearFutures.bestAsk || null,
        nearAskQuantity: nearFutures.bestAskQuantity || 0,
        farBid: farFutures.bestBid || null,
        farBidQuantity: farFutures.bestBidQuantity || 0,
        farAsk: farFutures.bestAsk || null,
        farAskQuantity: farFutures.bestAskQuantity || 0,
        timestamp: Math.max(
            nearFutures.timestamp ? new Date(nearFutures.timestamp).getTime() : 0,
            farFutures.timestamp ? new Date(farFutures.timestamp).getTime() : 0
        )
    };
}

function sortComparisons(comparisons, sortBy, sortOrder) {
    return comparisons.sort((a, b) => {
        let valueA, valueB;

        // Обработка сортировки по объему через sortOrder
        if (sortOrder === 'volume_desc' || sortOrder === 'volume_asc') {
            // Для stock-near и stock-far
            if (a.stockVolume !== undefined) {
                valueA = Math.max(a.stockVolume || 0, a.futuresVolume || 0);
                valueB = Math.max(b.stockVolume || 0, b.futuresVolume || 0);
            } else {
                // Для near-far
                valueA = Math.max(a.nearVolume || 0, a.farVolume || 0);
                valueB = Math.max(b.nearVolume || 0, b.farVolume || 0);
            }
            return sortOrder === 'volume_desc' ? valueB - valueA : valueA - valueB;
        }

        // Обычная сортировка по спреду
        switch (sortBy) {
            case 'spread':
                valueA = a.spreadPercent;
                valueB = b.spreadPercent;
                break;
            case 'spreadPercent':
                valueA = a.spreadPercent;
                valueB = b.spreadPercent;
                break;
            case 'volume':
                // Для stock-near и stock-far
                if (a.stockVolume !== undefined) {
                    valueA = Math.max(a.stockVolume || 0, a.futuresVolume || 0);
                    valueB = Math.max(b.stockVolume || 0, b.futuresVolume || 0);
                } else {
                    // Для near-far
                    valueA = Math.max(a.nearVolume || 0, a.farVolume || 0);
                    valueB = Math.max(b.nearVolume || 0, b.farVolume || 0);
                }
                break;
            default:
                valueA = 0;
                valueB = 0;
        }
        return sortOrder === 'desc' ? valueB - valueA : valueA - valueB;
    });
}

function updateSortingSettings() {
    stockNearSortBy = document.getElementById('stockNearSortBy').value;
    stockNearSortOrder = document.getElementById('stockNearSortOrder').value;
    stockNearMaxResults = parseInt(document.getElementById('stockNearMaxResults').value);

    stockFarSortBy = document.getElementById('stockFarSortBy').value;
    stockFarSortOrder = document.getElementById('stockFarSortOrder').value;
    stockFarMaxResults = parseInt(document.getElementById('stockFarMaxResults').value);

    nearFarSortBy = document.getElementById('nearFarSortBy').value;
    nearFarSortOrder = document.getElementById('nearFarSortOrder').value;
    nearFarMaxResults = parseInt(document.getElementById('nearFarMaxResults').value);

    updateTableTitles();
    updateFuturesComparisons();
}

function updateTableTitles() {
    const stockNearTitle = document.getElementById('stockNearFuturesTitle');
    const stockFarTitle = document.getElementById('stockFarFuturesTitle');
    const nearFarTitle = document.getElementById('nearFarFuturesTitle');

    let stockNearSortText = '';
    if (stockNearSortOrder === 'volume_desc' || stockNearSortOrder === 'volume_asc') {
        stockNearSortText = 'по объему';
    } else {
        switch (stockNearSortBy) {
            case 'spread':
                stockNearSortText = 'по спреду';
                break;
            case 'spreadPercent':
                stockNearSortText = 'по спреду %';
                break;
        }
    }
    let stockNearOrderText = '';
    if (stockNearSortOrder === 'desc') stockNearOrderText = 'наибольший';
    else if (stockNearSortOrder === 'asc') stockNearOrderText = 'наименьший';
    else if (stockNearSortOrder === 'volume_desc') stockNearOrderText = 'наибольший объем';
    else if (stockNearSortOrder === 'volume_asc') stockNearOrderText = 'наименьший объем';
    const stockNearResultsText = `Топ-${stockNearMaxResults}`;
    stockNearTitle.textContent = `📊 ${stockNearResultsText} акций vs ближние фьючерсы (${stockNearSortText}, ${stockNearOrderText})`;

    let stockFarSortText = '';
    if (stockFarSortOrder === 'volume_desc' || stockFarSortOrder === 'volume_asc') {
        stockFarSortText = 'по объему';
    } else {
        switch (stockFarSortBy) {
            case 'spread':
                stockFarSortText = 'по спреду';
                break;
            case 'spreadPercent':
                stockFarSortText = 'по спреду %';
                break;
        }
    }
    let stockFarOrderText = '';
    if (stockFarSortOrder === 'desc') stockFarOrderText = 'наибольший';
    else if (stockFarSortOrder === 'asc') stockFarOrderText = 'наименьший';
    else if (stockFarSortOrder === 'volume_desc') stockFarOrderText = 'наибольший объем';
    else if (stockFarSortOrder === 'volume_asc') stockFarOrderText = 'наименьший объем';
    const stockFarResultsText = `Топ-${stockFarMaxResults}`;
    stockFarTitle.textContent = `📊 ${stockFarResultsText} акций vs дальние фьючерсы (${stockFarSortText}, ${stockFarOrderText})`;

    let nearFarSortText = '';
    if (nearFarSortOrder === 'volume_desc' || nearFarSortOrder === 'volume_asc') {
        nearFarSortText = 'по объему';
    } else {
        switch (nearFarSortBy) {
            case 'spread':
                nearFarSortText = 'по спреду';
                break;
            case 'spreadPercent':
                nearFarSortText = 'по спреду %';
                break;
        }
    }
    let nearFarOrderText = '';
    if (nearFarSortOrder === 'desc') nearFarOrderText = 'наибольший';
    else if (nearFarSortOrder === 'asc') nearFarOrderText = 'наименьший';
    else if (nearFarSortOrder === 'volume_desc') nearFarOrderText = 'наибольший объем';
    else if (nearFarSortOrder === 'volume_asc') nearFarOrderText = 'наименьший объем';
    const nearFarResultsText = `Топ-${nearFarMaxResults}`;
    nearFarTitle.textContent = `📊 ${nearFarResultsText} ближние vs дальние фьючерсы (${nearFarSortText}, ${nearFarOrderText})`;
}

// --- Форматирование ---
function formatPrice(price) {
    if (price === null || price === undefined) return '--';
    const num = Number(price);
    if (!Number.isFinite(num)) return '--';
    if (num === 0) return '0';
    if (Math.abs(num) < 0.01) return num.toFixed(6);
    if (Math.abs(num) < 1) return num.toFixed(4);
    return num.toFixed(2);
}

function formatPercent(percent) {
    if (percent === null || percent === undefined) return '--';
    const num = Number(percent);
    if (!Number.isFinite(num)) return '--';
    return num.toFixed(2) + '%';
}

function formatVolume(volume) {
    if (volume === null || volume === undefined) return '--';
    const v = Number(volume);
    if (!Number.isFinite(v)) return '--';
    return Math.round(v).toLocaleString();
}

function formatTime(timestamp) {
    if (!timestamp) return '--:--:--';
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
}

function formatDelta(delta) {
    if (delta === null || delta === undefined) return '--';
    const num = Number(delta);
    if (!Number.isFinite(num)) return '--';
    return num.toFixed(2) + '%';
}

function formatBidAsk(price, quantity) {
    if (price === null || price === undefined || price === 0) return '--';
    const priceNum = Number(price);
    if (!Number.isFinite(priceNum)) return '--';
    const qty = quantity || 0;
    return `${priceNum.toFixed(2)} (${qty})`;
}

function formatSpreadPercent(spreadPercent) {
    if (spreadPercent === null || spreadPercent === undefined) return '--';
    const num = Number(spreadPercent);
    if (!Number.isFinite(num)) return '--';
    return num.toFixed(2) + '%';
}

function getChangeClass(change) {
    if (change === null || change === undefined) return '';
    return change > 0 ? 'positive' : change < 0 ? 'negative' : '';
}

// --- Рендер таблиц ---
function updateStockNearFuturesTable() {
    const tbody = stockNearFuturesTableBody;
    if (!tbody) return;

    if (!stockNearFutures || stockNearFutures.length === 0) {
        tbody.innerHTML = '<tr><td colspan="14" class="no-data">Нет данных</td></tr>';
        return;
    }

    tbody.innerHTML = '';
    stockNearFutures.forEach(comparison => {
        const row = document.createElement('tr');
        const expirationDays = calculateDaysToExpiration(comparison.futures.figi);
        row.innerHTML = `
            <td><strong>${comparison.stock.ticker}</strong></td>
            <td><strong>${comparison.futures.ticker}</strong></td>
            <td>${comparison.lotSize}</td>
            <td>${formatPrice(comparison.stockPrice)}</td>
            <td>${formatPrice(comparison.futuresPrice)}</td>
            <td>${formatVolume(comparison.stockVolume)}</td>
            <td>${formatVolume(comparison.futuresVolume)}</td>
            <td>${formatBidAsk(comparison.futuresBid, comparison.futuresBidQuantity)}</td>
            <td>${formatBidAsk(comparison.futuresAsk, comparison.futuresAskQuantity)}</td>
            <td class="${getChangeClass(comparison.spreadPercent)}">${formatSpreadPercent(comparison.spreadPercent)}</td>
            <td>${formatSpreadPercent(comparison.fairSpread)}</td>
            <td class="${getChangeClass(comparison.delta)}">${formatDelta(comparison.delta)}</td>
            <td class="expiration-cell">${expirationDays}</td>
            <td>${formatTime(comparison.timestamp)}</td>
        `;
        tbody.appendChild(row);
    });
}

function updateStockFarFuturesTable() {
    const tbody = stockFarFuturesTableBody;
    if (!tbody) return;

    if (!stockFarFutures || stockFarFutures.length === 0) {
        tbody.innerHTML = '<tr><td colspan="14" class="no-data">Нет данных</td></tr>';
        return;
    }

    tbody.innerHTML = '';
    stockFarFutures.forEach(comparison => {
        const row = document.createElement('tr');
        const expirationDays = calculateDaysToExpiration(comparison.futures.figi);
        row.innerHTML = `
            <td><strong>${comparison.stock.ticker}</strong></td>
            <td><strong>${comparison.futures.ticker}</strong></td>
            <td>${comparison.lotSize}</td>
            <td>${formatPrice(comparison.stockPrice)}</td>
            <td>${formatPrice(comparison.futuresPrice)}</td>
            <td>${formatVolume(comparison.stockVolume)}</td>
            <td>${formatVolume(comparison.futuresVolume)}</td>
            <td>${formatBidAsk(comparison.futuresBid, comparison.futuresBidQuantity)}</td>
            <td>${formatBidAsk(comparison.futuresAsk, comparison.futuresAskQuantity)}</td>
            <td class="${getChangeClass(comparison.spreadPercent)}">${formatSpreadPercent(comparison.spreadPercent)}</td>
            <td>${formatSpreadPercent(comparison.fairSpread)}</td>
            <td class="${getChangeClass(comparison.delta)}">${formatDelta(comparison.delta)}</td>
            <td class="expiration-cell">${expirationDays}</td>
            <td>${formatTime(comparison.timestamp)}</td>
        `;
        tbody.appendChild(row);
    });
}

function updateNearFarFuturesTable() {
    const tbody = nearFarFuturesTableBody;
    if (!tbody) return;

    if (!nearFarFutures || nearFarFutures.length === 0) {
        tbody.innerHTML = '<tr><td colspan="16" class="no-data">Нет данных</td></tr>';
        return;
    }

    tbody.innerHTML = '';
    nearFarFutures.forEach(comparison => {
        const row = document.createElement('tr');
        const nearExpirationDays = calculateDaysToExpiration(comparison.nearFutures.figi);
        const farExpirationDays = calculateDaysToExpiration(comparison.farFutures.figi);
        row.innerHTML = `
            <td><strong>${comparison.nearFutures.ticker}</strong></td>
            <td><strong>${comparison.farFutures.ticker}</strong></td>
            <td>${comparison.lotSize}</td>
            <td>${formatPrice(comparison.nearPrice)}</td>
            <td>${formatPrice(comparison.farPrice)}</td>
            <td>${formatVolume(comparison.nearVolume)}</td>
            <td>${formatVolume(comparison.farVolume)}</td>
            <td>${formatBidAsk(comparison.nearBid, comparison.nearBidQuantity)}</td>
            <td>${formatBidAsk(comparison.nearAsk, comparison.nearAskQuantity)}</td>
            <td>${formatBidAsk(comparison.farBid, comparison.farBidQuantity)}</td>
            <td>${formatBidAsk(comparison.farAsk, comparison.farAskQuantity)}</td>
            <td class="${getChangeClass(comparison.spreadPercent)}">${formatSpreadPercent(comparison.spreadPercent)}</td>
            <td>${formatSpreadPercent(comparison.fairSpread)}</td>
            <td class="${getChangeClass(comparison.delta)}">${formatDelta(comparison.delta)}</td>
            <td class="expiration-cell">
                <div class="expiration-info">
                    <div class="near-expiration">Ближний: ${nearExpirationDays}</div>
                    <div class="far-expiration">Дальний: ${farExpirationDays}</div>
                </div>
            </td>
            <td>${formatTime(comparison.timestamp)}</td>
        `;
        tbody.appendChild(row);
    });
}

// --- Объемы ---
function ensureBaseVolume(figi, quoteData) {
    if (baseVolumeCache.has(figi)) {
        return baseVolumeCache.get(figi);
    }

    let base = 0;
    if (todayVolumeData && todayVolumeData.volumes) {
        base = todayVolumeData.volumes[figi] || 0;
    } else if (quoteData && typeof quoteData.totalVolume === 'number' && quoteData.totalVolume > 0) {
        base = quoteData.totalVolume;
    }

    baseVolumeCache.set(figi, base);
    return base;
}

function updateIncrementalVolume(figi, quoteData, baseVolume) {
    const serverTotal = (quoteData && typeof quoteData.totalVolume === 'number') ? quoteData.totalVolume : null;

    if (serverTotal !== null && serverTotal >= baseVolume) {
        const increment = serverTotal - baseVolume;
        incrementVolumeCache.set(figi, increment);
        totalVolumeCache.set(figi, serverTotal);
        return increment;
    }

    const lastIncrement = incrementVolumeCache.get(figi) || 0;
    const newVolume = (quoteData && typeof quoteData.volume === 'number') ? quoteData.volume : 0;
    if (newVolume > 0) {
        const updatedIncrement = lastIncrement + newVolume;
        incrementVolumeCache.set(figi, updatedIncrement);
        totalVolumeCache.set(figi, baseVolume + updatedIncrement);
        return updatedIncrement;
    }

    incrementVolumeCache.set(figi, lastIncrement);
    totalVolumeCache.set(figi, baseVolume + lastIncrement);
    return lastIncrement;
}

function getIncrementalVolume(figi) {
    return incrementVolumeCache.get(figi) || 0;
}

function getDisplayVolume(quoteData) {
    const figi = quoteData.figi;
    if (totalVolumeCache.has(figi)) {
        return totalVolumeCache.get(figi) || 0;
    }
    const baseVolume = ensureBaseVolume(figi, quoteData);
    const increment = getIncrementalVolume(figi);
    const total = baseVolume + increment;
    totalVolumeCache.set(figi, total);
    return total;
}

function updateVolumeDataForQuote(quoteData) {
    const figi = quoteData.figi;
    const baseVolume = ensureBaseVolume(figi, quoteData);
    const increment = updateIncrementalVolume(figi, quoteData, baseVolume);
    const totalVolume = baseVolume + increment;
    totalVolumeCache.set(figi, totalVolume);
    quoteData.totalVolume = totalVolume;
}

function updateTotalVolume() {
    let total = 0;
    quotes.forEach((quote, figi) => {
        const cachedTotal = totalVolumeCache.get(figi);
        if (cachedTotal !== undefined) {
            total += cachedTotal;
        } else {
            const baseVolume = ensureBaseVolume(figi, quote);
            const increment = getIncrementalVolume(figi);
            total += baseVolume + increment;
        }
    });
    totalVolume.textContent = total.toLocaleString();
}

// --- Индексы ---
// Функции для работы с индексами вынесены в indices-bar.js
// initializeIndicesBar, createIndexElement, updateIndicesBar, updateIndicesFromServer, loadIndexPrices вынесены в indices-bar.js

function loadClosePricesForQuote(quoteData) {
    fetch(`/api/price-cache/last-close-price?figi=${quoteData.figi}`)
        .then(response => response.ok ? response.json() : null)
        .then(data => {
            const price = data && (data.closePrice !== undefined ? data.closePrice : data);
            if (price && price > 0) {
                quoteData.closePriceOS = price;
                quoteData.closePrice = price;
                quotes.set(quoteData.figi, quoteData);
                // Обновляем индекс, если это инструмент из строки индексов
                updateIndicesBar(quoteData);
                updateFuturesComparisons();
            }
        })
        .catch(error => {
            console.error('Ошибка при загрузке цены закрытия для', quoteData.figi, error);
        });
}

function loadEveningSessionPriceForQuote(quoteData) {
    fetch(`/api/price-cache/prices/${quoteData.figi}`)
        .then(response => {
            if (!response.ok) {
                return null;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.prices && data.prices.eveningSessionPrice) {
                const eveningPrice = data.prices.eveningSessionPrice;
                if (eveningPrice && eveningPrice > 0) {
                    quoteData.closePriceVS = eveningPrice;
                    quotes.set(quoteData.figi, quoteData);
                    // Обновляем индекс, если это инструмент из строки индексов
                    updateIndicesBar(quoteData);
                    updateFuturesComparisons();
                }
            }
        })
        .catch(error => {
            // Тихо игнорируем ошибки, так как не все инструменты могут иметь цену вечерней сессии
        });
}

/**
 * Предзагрузка всех пар при загрузке страницы
 */
async function loadAllPairsOnPageLoad() {
    try {
        console.log('=== Loading all pairs on page load ===');

        // 0. Загружаем ключевую ставку
        await loadKeyRate();

        // 1. Загружаем данные о фьючерсах, если еще не загружены
        if (futuresDataCache.size === 0) {
            console.log('Step 1: Loading futures data...');
            await loadFuturesData();
            // Проверяем, что кэш действительно заполнен
            if (futuresDataCache.size === 0) {
                console.error('Failed to load futures data: cache is still empty');
                return;
            }
            console.log(`Step 1 completed: Futures cache size = ${futuresDataCache.size}`);
        } else {
            console.log(`Step 1 skipped: Futures cache already loaded (size = ${futuresDataCache.size})`);
        }

        // 2. Загружаем текущие цены для всех инструментов (включает и цены, и имена)
        console.log('Step 2: Loading current prices...');
        const pricesResponse = await fetch('/api/scanner/current-prices');
        if (!pricesResponse.ok) {
            console.error('Failed to load current prices:', pricesResponse.status, pricesResponse.statusText);
            return;
        }
        const pricesData = await pricesResponse.json();
        const prices = pricesData.prices || {};
        const instrumentNames = pricesData.instrumentNames || {};

        console.log(`Step 2 completed: Loaded prices for ${Object.keys(prices).length} instruments`);
        console.log(`Sample prices:`, Object.keys(prices).slice(0, 5).map(figi => ({
            figi,
            ticker: instrumentNames[figi] || figi,
            price: prices[figi]
        })));

        // 3. Создаем объекты quote для каждого инструмента и добавляем в quotes Map
        console.log('Step 3: Creating quote objects from prices...');
        let loadedCount = 0;
        let skippedCount = 0;
        Object.keys(prices).forEach(figi => {
            // Пропускаем, если уже есть в quotes
            if (quotes.has(figi)) {
                skippedCount++;
                return;
            }

            const ticker = instrumentNames[figi] || figi;
            const price = prices[figi] || 0;

            // Создаем объект quote
            const quoteData = {
                figi: figi,
                ticker: ticker,
                currentPrice: price,
                volume: 0,
                timestamp: new Date().toISOString()
            };

            // Добавляем в quotes Map
            quotes.set(figi, quoteData);
            loadedCount++;
        });

        console.log(`Step 3a completed: Preloaded ${loadedCount} quotes from prices, skipped ${skippedCount} (already exists)`);

        // 3b. Добавляем фьючерсы из кэша, которых нет в quotes (даже если для них нет цен)
        console.log('Step 3b: Adding futures from cache that are not in quotes...');
        let futuresAddedCount = 0;
        futuresDataCache.forEach((futuresData, figi) => {
            if (!quotes.has(figi)) {
                const ticker = futuresData.ticker || figi;
                const quoteData = {
                    figi: figi,
                    ticker: ticker,
                    currentPrice: 0, // Нет цены, будет обновлено через WebSocket
                    volume: 0,
                    timestamp: new Date().toISOString()
                };
                quotes.set(figi, quoteData);
                futuresAddedCount++;
            }
        });

        console.log(`Step 3b completed: Added ${futuresAddedCount} futures from cache`);
        console.log(`Total quotes in Map: ${quotes.size}`);

        // 4. Проверяем, что у нас есть и акции, и фьючерсы
        let stocksCount = 0;
        let futuresCount = 0;
        quotes.forEach((quote, figi) => {
            if (isStock(quote.ticker, figi)) {
                stocksCount++;
            } else if (futuresDataCache.has(figi)) {
                futuresCount++;
            }
        });
        console.log(`Step 4: Found ${stocksCount} stocks and ${futuresCount} futures in quotes`);

        // 5. Обновляем счетчик активных инструментов
        if (activeInstruments) {
            activeInstruments.textContent = quotes.size;
        }

        // 6. Проверяем кэш фьючерсов перед обновлением сравнений
        if (futuresDataCache.size === 0) {
            console.error('Futures cache is empty, cannot update comparisons');
            return;
        }

        // 7. Обновляем сравнения фьючерсов
        console.log('Step 5: Updating futures comparisons...');
        updateFuturesComparisons();

        console.log('=== All pairs preloaded successfully ===');
    } catch (error) {
        console.error('Error loading all pairs on page load:', error);
        console.error('Error stack:', error.stack);
    }
}

function loadClosePricesForAllQuotes() {
    quotes.forEach((quoteData, figi) => {
        if (!quoteData.closePriceOS && !quoteData.closePrice) {
            loadClosePricesForQuote(quoteData);
        }
        if (!quoteData.closePriceVS) {
            loadEveningSessionPriceForQuote(quoteData);
        }
    });
}

/**
 * Загрузить ключевую ставку с сервера
 */
async function loadKeyRate() {
    try {
        const response = await fetch('/api/scanner/futures/key-rate');
        if (response.ok) {
            const data = await response.json();
            if (data.keyRate !== undefined) {
                keyRate = data.keyRate;
                console.log(`Key rate loaded: ${keyRate}%`);
            }
        }
    } catch (error) {
        console.error('Error loading key rate:', error);
        // Используем значение по умолчанию
    }
}

// Сессия - сканер фьючерсов всегда активен
async function updateScannerStatus() {
    let isTestMode = false;

    try {
        const testModeResp = await fetch('/api/scanner/test-mode').catch(() => null);

        if (testModeResp && testModeResp.ok) {
            const data = await testModeResp.json();
            isTestMode = !!data.testModeFutures;
        }
    } catch (error) {
        console.warn('Не удалось получить статус тестового режима:', error);
    }

    // Загружаем ключевую ставку из статуса
    try {
        const statusResp = await fetch('/api/scanner/futures/status').catch(() => null);
        if (statusResp && statusResp.ok) {
            const data = await statusResp.json();
            if (data.keyRate !== undefined) {
                keyRate = data.keyRate;
            }
        }
    } catch (error) {
        console.warn('Не удалось загрузить ключевую ставку:', error);
    }

    // Сканер фьючерсов всегда активен (работает в любое время, включая выходные)
    isSessionActive = true;
    isTestModeGlobal = isTestMode;

    const statusEl = document.getElementById('scannerStatus');
    if (statusEl) {
        if (isTestMode) {
            statusEl.textContent = 'Тестовый режим';
            statusEl.style.color = '#1976d2';
        } else {
            statusEl.textContent = 'Активен';
            statusEl.style.color = '#2e7d32';
        }
    }
}

// Исторические объемы
async function loadHistoryVolumeData() {
    try {
        const response = await fetch('/api/price-cache/volumes');
        if (!response.ok) return null;
        const data = await response.json();
        return {
            morningVolumes: data.morningVolumes || {},
            todayVolumes: data.todayVolumes || {}
        };
    } catch (error) {
        console.error('Ошибка при загрузке данных объемов:', error);
        return null;
    }
}

async function initializeVolumeData() {
    const data = await loadHistoryVolumeData();
    if (data) {
        historyVolumeData = { morningVolumes: data.morningVolumes || {} };
        todayVolumeData = { volumes: data.todayVolumes || {} };
    }
}

// Модалка управления индексами
// toggleIndexManagement, loadCurrentIndices, displayCurrentIndices, addIndex, removeIndex вынесены в indices-bar.js

// Обработчики
connectBtn.addEventListener('click', connect);
disconnectBtn.addEventListener('click', disconnect);
document.getElementById('stockNearSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('stockNearSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('stockNearMaxResults').addEventListener('change', updateSortingSettings);
document.getElementById('stockFarSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('stockFarSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('stockFarMaxResults').addEventListener('change', updateSortingSettings);
document.getElementById('nearFarSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('nearFarSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('nearFarMaxResults').addEventListener('change', updateSortingSettings);

setInterval(updateScannerStatus, 60000);
updateScannerStatus();
// Инициализация модуля индексов
// Используем общий endpoint для индексов (общий для всех сканеров)
initIndicesBar({
    apiEndpoint: '/api/scanner',
    quotesMap: quotes,
    formatPrice: formatPrice,
    formatPercent: (percent) => {
        if (percent === null || percent === undefined) return '0.00';
        const num = Number(percent);
        if (!Number.isFinite(num)) return '0.00';
        if (Math.abs(num) < 0.01 && num !== 0) return num.toFixed(4);
        return num.toFixed(2);
    },
    lastUpdateTime: lastUpdateTime,
    onIndexUpdate: (indexInfo, quoteData) => {
        // Callback при обновлении индекса (если нужен)
    }
});

initializeIndicesBar();
initializeVolumeData();
updateIndicesFromServer();
loadIndexPrices();
setTimeout(() => { loadClosePricesForAllQuotes(); }, 2000);

// Предзагрузка всех пар при загрузке страницы
// Ждем полной загрузки DOM и других инициализаций
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        setTimeout(() => { loadAllPairsOnPageLoad(); }, 1500);
    });
} else {
    // DOM уже загружен
    setTimeout(() => { loadAllPairsOnPageLoad(); }, 1500);
}

// Закрытие модального окна при клике вне его
window.onclick = function (event) {
    const modal = document.getElementById('indexManagementModal');
    if (event.target === modal) {
        modal.style.display = 'none';
    }
};

