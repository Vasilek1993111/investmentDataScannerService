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

// Индексы для полоски
let indices = new Map();
let INDICES_CONFIG = [
    { figi: 'BBG00KDWPPW3', name: 'IMOEX2', displayName: 'IMOEX2' }
];

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
            data.futures.forEach(future => {
                futuresDataCache.set(future.figi, {
                    figi: future.figi,
                    ticker: future.ticker,
                    expirationDate: future.expirationDate,
                    basicAsset: future.basicAsset,
                    assetType: future.assetType
                });
            });
            console.log(`Loaded ${data.futures.length} futures with expiration data`);
            console.log(`Futures cache size: ${futuresDataCache.size}`);

            // Выводим примеры для отладки
            if (data.futures.length > 0) {
                const sample = data.futures[0];
                console.log('Sample future data:', {
                    figi: sample.figi,
                    ticker: sample.ticker,
                    basicAsset: sample.basicAsset,
                    expirationDate: sample.expirationDate
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
 * Рассчитать количество дней до экспирации фьючерса
 */
function calculateDaysToExpiration(figi) {
    const futuresData = futuresDataCache.get(figi);
    if (!futuresData || !futuresData.expirationDate) {
        return 'N/A';
    }

    try {
        const expirationDate = new Date(futuresData.expirationDate);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        expirationDate.setHours(0, 0, 0, 0);

        const diffTime = expirationDate - today;
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        if (diffDays < 0) {
            return `Истек (${Math.abs(diffDays)} дн. назад)`;
        } else if (diffDays === 0) {
            return 'Сегодня';
        } else {
            return `${diffDays} дн.`;
        }
    } catch (error) {
        console.error('Error calculating expiration days:', error);
        return 'N/A';
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
    const futuresForAsset = [];

    quotes.forEach((quote, figi) => {
        if (!futuresDataCache.has(figi)) return;

        const baseTickerFromQuote = getBaseTicker(quote.ticker, figi);
        if (baseTickerFromQuote === baseTicker) {
            const quarter = getExpirationQuarter(figi);
            if (quarter) {
                futuresForAsset.push({
                    figi,
                    quarter,
                    expirationDate: quarter.date
                });
            }
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
 * Получить лотность фьючерса
 */
function getFuturesLotSize(ticker) {
    // По умолчанию 100
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

            initializeIndicesBar();

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

    if (!quoteData.closePriceOS && !quoteData.closePrice) {
        loadClosePricesForQuote(quoteData);
    }

    updateVolumeDataForQuote(quoteData);
    quotes.set(figi, quoteData);

    updateCount++;
    lastUpdateTime = new Date();

    activeInstruments.textContent = quotes.size;
    updateTotalVolume();
    lastUpdate.textContent = lastUpdateTime.toLocaleTimeString();

    updateIndicesBar(quoteData);
    updateFuturesComparisons();
}

function updateFuturesComparisons() {
    const quotesArray = Array.from(quotes.values());
    console.log(`updateFuturesComparisons: processing ${quotesArray.length} quotes, futures cache size: ${futuresDataCache.size}`);

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
                    console.log(`Found near futures for ${baseTicker}: ${nearQuote?.ticker} (${futuresInfo.nearFutures})`);
                }
                if (futuresInfo.farFutures) {
                    const farQuote = quotes.get(futuresInfo.farFutures);
                    console.log(`Found far futures for ${baseTicker}: ${farQuote?.ticker} (${futuresInfo.farFutures})`);
                }
            }
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
    const lotSize = getFuturesLotSize(futures.ticker);
    const stockPrice = stock.currentPrice || 0;
    const futuresPrice = futures.currentPrice || 0;

    // Спред % = ((фьючерс / (акция * лотность)) - 1) * 100
    const spreadPercent = stockPrice > 0 && lotSize > 0 ?
        ((futuresPrice / (stockPrice * lotSize)) - 1) * 100 : 0;

    return {
        baseTicker,
        stock,
        futures,
        lotSize,
        stockPrice,
        futuresPrice,
        spreadPercent,
        stockVolume: getDisplayVolume(stock),
        futuresVolume: getDisplayVolume(futures),
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

    return {
        baseTicker,
        nearFutures,
        farFutures,
        lotSize: getFuturesLotSize(nearFutures.ticker),
        nearPrice,
        farPrice,
        spreadPercent,
        nearVolume: getDisplayVolume(nearFutures),
        farVolume: getDisplayVolume(farFutures),
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
        tbody.innerHTML = '<tr><td colspan="10" class="no-data">Нет данных</td></tr>';
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
            <td class="${getChangeClass(comparison.spreadPercent)}">${formatSpreadPercent(comparison.spreadPercent)}</td>
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
        tbody.innerHTML = '<tr><td colspan="10" class="no-data">Нет данных</td></tr>';
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
            <td class="${getChangeClass(comparison.spreadPercent)}">${formatSpreadPercent(comparison.spreadPercent)}</td>
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
        tbody.innerHTML = '<tr><td colspan="10" class="no-data">Нет данных</td></tr>';
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
            <td class="${getChangeClass(comparison.spreadPercent)}">${formatSpreadPercent(comparison.spreadPercent)}</td>
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
function initializeIndicesBar() {
    indicesContainer.innerHTML = '';
    INDICES_CONFIG.forEach(config => {
        const indexElement = createIndexElement(config);
        indicesContainer.appendChild(indexElement);
        indices.set(config.figi, { ...config, element: indexElement, data: null });
    });
}

function createIndexElement(config) {
    const div = document.createElement('div');
    div.className = 'index-item';
    div.id = `index-${config.figi}`;
    div.innerHTML = `
        <div class="index-name">${config.displayName}</div>
        <div class="index-prices">
            <div class="index-os-price">ОС: --</div>
            <div class="index-evening-price">ВС: --</div>
        </div>
        <div class="index-current">--</div>
        <div class="index-change neutral">--</div>
        <div class="index-time">--:--</div>
    `;
    return div;
}

function updateIndicesBar(quoteData) {
    const figi = quoteData.figi;
    const indexInfo = indices.get(figi);
    if (!indexInfo) return;

    const element = indexInfo.element;
    const currentElement = element.querySelector('.index-current');
    const changeElement = element.querySelector('.index-change');
    const timeElement = element.querySelector('.index-time');

    if (!quoteData.currentPrice) {
        currentElement.textContent = '--';
        changeElement.textContent = '--';
        changeElement.className = 'index-change neutral';
        timeElement.textContent = '--:--';
        return;
    }

    const previousPrice = indexInfo.data ? indexInfo.data.currentPrice : null;
    const currentPrice = quoteData.currentPrice;
    currentElement.textContent = formatPrice(quoteData.currentPrice);

    const priceOS = quoteData.closePriceOS || quoteData.closePrice;
    if (priceOS && priceOS > 0) {
        const change = quoteData.currentPrice - priceOS;
        const changePercent = (change / priceOS) * 100;
        const changeClass = changePercent > 0 ? 'positive' : changePercent < 0 ? 'negative' : 'neutral';
        const changeText = changePercent >= 0 ? `+${formatPercent(changePercent)}` : formatPercent(changePercent);
        changeElement.textContent = `(${changeText})`;
        changeElement.className = `index-change ${changeClass}`;
    } else {
        changeElement.textContent = '--';
        changeElement.className = 'index-change neutral';
    }

    if (previousPrice && previousPrice !== currentPrice) {
        currentElement.classList.remove('price-up', 'price-down');
        if (currentPrice > previousPrice) currentElement.classList.add('price-up');
        else if (currentPrice < previousPrice) currentElement.classList.add('price-down');
        setTimeout(() => currentElement.classList.remove('price-up', 'price-down'), 2000);
    }

    if (quoteData.timestamp) {
        const date = new Date(quoteData.timestamp);
        timeElement.textContent = date.toLocaleTimeString().slice(0, 5);
    } else {
        timeElement.textContent = lastUpdateTime ? lastUpdateTime.toLocaleTimeString().slice(0, 5) : '--:--';
    }

    indexInfo.data = quoteData;
}

// --- API индексов ---
function updateIndicesFromServer() {
    fetch('/api/scanner/futures-scanner/indices')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                INDICES_CONFIG = data.indices.map(index => ({
                    figi: index.figi || index.name,
                    name: index.name,
                    displayName: index.displayName
                }));
                const indicesContainer = document.getElementById('indicesContainer');
                indicesContainer.innerHTML = '';
                indices.clear();
                INDICES_CONFIG.forEach(config => {
                    const indexElement = createIndexElement(config);
                    indicesContainer.appendChild(indexElement);
                    indices.set(config.figi, { ...config, element: indexElement, data: null });
                });
                loadIndexPrices();
            }
        })
        .catch(error => {
            console.error('Ошибка при обновлении индексов:', error);
        });
}

function loadIndexPrices() {
    fetch('/api/scanner/futures-scanner/indices/prices')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                Object.values(data.prices).forEach(priceData => {
                    const figi = priceData.figi;
                    const indexInfo = indices.get(figi);
                    if (indexInfo) {
                        const element = indexInfo.element;
                        const osPriceElement = element.querySelector('.index-os-price');
                        const eveningPriceElement = element.querySelector('.index-evening-price');
                        if (osPriceElement) osPriceElement.textContent = `ОС: ${priceData.closePriceOS ? formatPrice(priceData.closePriceOS) : '--'}`;
                        if (eveningPriceElement) eveningPriceElement.textContent = `ВС: ${priceData.closePriceEvening ? formatPrice(priceData.closePriceEvening) : '--'}`;
                    }
                });
            }
        })
        .catch(error => {
            console.error('Ошибка при загрузке цен закрытия:', error);
        });
}

function loadClosePricesForQuote(quoteData) {
    fetch(`/api/price-cache/last-close-price?figi=${quoteData.figi}`)
        .then(response => response.ok ? response.json() : null)
        .then(price => {
            if (price && price > 0) {
                quoteData.closePriceOS = price;
                quoteData.closePrice = price;
                quotes.set(quoteData.figi, quoteData);
                updateFuturesComparisons();
            }
        })
        .catch(error => {
            console.error('Ошибка при загрузке цены закрытия для', quoteData.figi, error);
        });
}

function loadClosePricesForAllQuotes() {
    quotes.forEach((quoteData, figi) => {
        if (!quoteData.closePriceOS && !quoteData.closePrice) {
            loadClosePricesForQuote(quoteData);
        }
    });
}

// Сессия - сканер фьючерсов всегда активен
async function updateScannerStatus() {
    let isTestMode = false;

    try {
        const testModeResp = await fetch('/api/scanner/test-mode').catch(() => null);

        if (testModeResp && testModeResp.ok) {
            const data = await testModeResp.json();
            isTestMode = !!data.testModeEnabled;
        }
    } catch (error) {
        console.warn('Не удалось получить статус тестового режима:', error);
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
function toggleIndexManagement() {
    const modal = document.getElementById('indexManagementModal');
    if (modal.style.display === 'none') {
        modal.style.display = 'flex';
        loadCurrentIndices();
    } else {
        modal.style.display = 'none';
    }
}

function loadCurrentIndices() {
    fetch('/api/scanner/futures-scanner/indices')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                displayCurrentIndices(data.indices);
            }
        })
        .catch(error => {
            console.error('Ошибка при загрузке индексов:', error);
        });
}

function displayCurrentIndices(indicesList) {
    const container = document.getElementById('currentIndicesList');
    container.innerHTML = '';
    indicesList.forEach(index => {
        const indexItem = document.createElement('div');
        indexItem.className = 'index-item-manage';
        indexItem.innerHTML = `
            <div class="index-info">
                <div class="index-name">${index.displayName}</div>
                <div class="index-figi">${index.name}</div>
            </div>
            <button class="btn-remove" onclick="removeIndex('${index.name}')">Удалить</button>
        `;
        container.appendChild(indexItem);
    });
}

function addIndex() {
    const name = document.getElementById('newIndexTicker').value.trim();
    const displayName = document.getElementById('newIndexDisplayName').value.trim() || name;
    if (!name) {
        alert('Пожалуйста, заполните Ticker');
        return;
    }
    fetch('/api/scanner/futures-scanner/indices/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, displayName })
    })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                document.getElementById('newIndexTicker').value = '';
                document.getElementById('newIndexDisplayName').value = '';
                loadCurrentIndices();
                updateIndicesFromServer();
                setTimeout(() => { loadIndexPrices(); }, 500);
                alert('Индекс успешно добавлен!');
            } else {
                alert('Ошибка: ' + data.message);
            }
        })
        .catch(error => {
            console.error('Ошибка при добавлении индекса:', error);
            alert('Ошибка при добавлении индекса');
        });
}

function removeIndex(name) {
    if (!confirm(`Вы уверены, что хотите удалить индекс "${name}"?`)) return;
    fetch('/api/scanner/futures-scanner/indices/remove', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name })
    })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                loadCurrentIndices();
                updateIndicesFromServer();
                alert('Индекс успешно удален!');
            } else {
                alert('Ошибка: ' + data.message);
            }
        })
        .catch(error => {
            console.error('Ошибка при удалении индекса:', error);
            alert('Ошибка при удалении индекса');
        });
}

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
initializeIndicesBar();
initializeVolumeData();
updateIndicesFromServer();
loadIndexPrices();
setTimeout(() => { loadClosePricesForAllQuotes(); }, 2000);

// Закрытие модального окна при клике вне его
window.onclick = function (event) {
    const modal = document.getElementById('indexManagementModal');
    if (event.target === modal) {
        modal.style.display = 'none';
    }
};

