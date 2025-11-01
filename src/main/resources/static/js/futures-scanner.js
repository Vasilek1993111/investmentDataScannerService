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
let previousValues = new Map(); // хранение предыдущих значений для подсветки изменений

// Индексы для полоски
let indices = new Map();
let INDICES_CONFIG = [
    { figi: 'BBG00KDWPPW3', name: 'IMOEX2', displayName: 'IMOEX2' }
];

// Настройки сортировки
let stockNearSortBy = 'spread';
let stockNearSortOrder = 'desc';
let stockNearMaxResults = 15;
let stockFarSortBy = 'spread';
let stockFarSortOrder = 'desc';
let stockFarMaxResults = 15;
let nearFarSortBy = 'spread';
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
 * Рассчитать количество дней до экспирации фьючерса
 * @param {string} figi - FIGI фьючерса
 * @returns {string} - Строка с количеством дней или "N/A"
 */
function calculateDaysToExpiration(figi) {
    const futuresData = futuresDataCache.get(figi);
    if (!futuresData || !futuresData.expirationDate) {
        return 'N/A';
    }

    try {
        const expirationDate = new Date(futuresData.expirationDate);
        const today = new Date();

        // Устанавливаем время на начало дня для корректного расчета
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
 * Форматировать дату экспирации
 * @param {string} figi - FIGI фьючерса
 * @returns {string} - Отформатированная дата или "N/A"
 */
function formatExpirationDate(figi) {
    const futuresData = futuresDataCache.get(figi);
    if (!futuresData || !futuresData.expirationDate) {
        return 'N/A';
    }

    try {
        const expirationDate = new Date(futuresData.expirationDate);
        return expirationDate.toLocaleDateString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    } catch (error) {
        console.error('Error formatting expiration date:', error);
        return 'N/A';
    }
}

/**
 * Загрузить данные о фьючерсах с сервера
 */
async function loadFuturesData() {
    try {
        const response = await fetch('/api/scanner/futures');
        if (!response.ok) {
            console.warn('Failed to load futures data:', response.status);
            return;
        }
        const data = await response.json();

        // Заполняем кэш данными о фьючерсах
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
        }
    } catch (error) {
        console.error('Error loading futures data:', error);
    }
}

function connect() {
    if (isConnected) return;
    try {
        const port = getWebSocketPort();
        websocket = new WebSocket(`ws://localhost:${port}/ws/quotes`);

        websocket.onopen = function () {
            isConnected = true;
            connectBtn.disabled = true;
            disconnectBtn.disabled = false;
            connectionStatus.textContent = 'Подключено';
            connectionStatus.className = 'status connected';

            stockNearFuturesTableBody.innerHTML = '<tr><td colspan="8" class="no-data">Нет данных</td></tr>';
            stockFarFuturesTableBody.innerHTML = '<tr><td colspan="8" class="no-data">Нет данных</td></tr>';
            nearFarFuturesTableBody.innerHTML = '<tr><td colspan="8" class="no-data">Нет данных</td></tr>';
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
                updateQuote(quoteData);
            } catch (error) {
                console.error('Error parsing quote data:', error);
            }
        };

        websocket.onclose = function () {
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
    if (websocket) websocket.close();
}

function updateQuote(quoteData) {
    const figi = quoteData.figi;
    if (!quoteData.closePriceOS && !quoteData.closePrice) {
        loadClosePricesForQuote(quoteData);
    }
    // Обновляем кэши объемов с учетом активности сессии/теста
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

function updateTotalVolume() {
    let total = 0;
    quotes.forEach((quote, figi) => {
        const cached = totalVolumeCache.get(figi);
        if (cached !== undefined) total += cached; else total += quote.totalVolume || 0;
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

function updateFuturesComparisons() {
    const quotesArray = Array.from(quotes.values());

    // Группируем инструменты по базовому тикеру
    const instrumentGroups = new Map();

    quotesArray.forEach(quote => {
        const baseTicker = getBaseTicker(quote.ticker);
        if (!baseTicker) return;

        if (!instrumentGroups.has(baseTicker)) {
            instrumentGroups.set(baseTicker, {
                stock: null,
                nearFutures: null,
                farFutures: null
            });
        }

        const group = instrumentGroups.get(baseTicker);

        if (isStock(quote.ticker)) {
            group.stock = quote;
        } else if (isNearFutures(quote.ticker)) {
            group.nearFutures = quote;
        } else if (isFarFutures(quote.ticker)) {
            group.farFutures = quote;
        }
    });

    // Создаем сравнения
    const stockNearComparisons = [];
    const stockFarComparisons = [];
    const nearFarComparisons = [];

    instrumentGroups.forEach((group, baseTicker) => {
        // Акция vs Ближний фьючерс
        if (group.stock && group.nearFutures) {
            stockNearComparisons.push(createComparison(baseTicker, group.stock, group.nearFutures));
        }

        // Акция vs Дальний фьючерс
        if (group.stock && group.farFutures) {
            stockFarComparisons.push(createComparison(baseTicker, group.stock, group.farFutures));
        }

        // Ближний vs Дальний фьючерс
        if (group.nearFutures && group.farFutures) {
            nearFarComparisons.push(createFuturesComparison(baseTicker, group.nearFutures, group.farFutures));
        }
    });

    // Сортируем и обновляем таблицы
    stockNearFutures = sortComparisons([...stockNearComparisons], stockNearSortBy, stockNearSortOrder).slice(0, stockNearMaxResults);
    stockFarFutures = sortComparisons([...stockFarComparisons], stockFarSortBy, stockFarSortOrder).slice(0, stockFarMaxResults);
    nearFarFutures = sortComparisons([...nearFarComparisons], nearFarSortBy, nearFarSortOrder).slice(0, nearFarMaxResults);

    updateStockNearFuturesTable();
    updateStockFarFuturesTable();
    updateNearFarFuturesTable();
}

function getBaseTicker(ticker) {
    if (!ticker) return null;

    // Убираем суффиксы фьючерсов Z5 и H6
    if (ticker.endsWith('Z5')) {
        return ticker.slice(0, -2);
    }
    if (ticker.endsWith('H6')) {
        return ticker.slice(0, -2);
    }

    // Для других фьючерсов убираем последний символ
    const futuresSuffixes = ['F', 'G', 'H', 'J', 'K', 'M', 'N', 'Q', 'U', 'V', 'X', 'Z'];
    for (const suffix of futuresSuffixes) {
        if (ticker.endsWith(suffix)) {
            return ticker.slice(0, -1);
        }
    }

    return ticker;
}

function isStock(ticker) {
    if (!ticker) return false;
    // Акции - это инструменты, которые не заканчиваются на Z5 или H6
    return !ticker.endsWith('Z5') && !ticker.endsWith('H6');
}

function isNearFutures(ticker) {
    if (!ticker) return false;
    // Ближние фьючерсы - суффикс Z5 (декабрь 2025)
    return ticker.endsWith('Z5');
}

function isFarFutures(ticker) {
    if (!ticker) return false;
    // Дальние фьючерсы - суффикс H6 (март 2026)
    return ticker.endsWith('H6');
}

function getFuturesLotSize(ticker) {
    // Лотность фьючерсов (количество акций в одном фьючерсе)
    // По умолчанию 100, но можно настроить для конкретных инструментов
    const lotSizes = {
        // Примеры настройки лотности для конкретных фьючерсов
        'SRZ5': 100,  // SBER ближний
        'SRH6': 100,  // SBER дальний
        'GZU6Z5': 100, // GAZP ближний
        'GZU6H6': 100, // GAZP дальний
        // Добавьте другие фьючерсы по необходимости
    };

    return lotSizes[ticker] || 100; // По умолчанию 100
}

function createComparison(baseTicker, instrument1, instrument2) {
    // Определяем какая акция, а какой фьючерс
    const stock = isStock(instrument1.ticker) ? instrument1 : instrument2;
    const futures = isStock(instrument1.ticker) ? instrument2 : instrument1;

    // Получаем лотность фьючерса (по умолчанию 100, можно настроить)
    const lotSize = getFuturesLotSize(futures.ticker);

    // Рассчитываем спред % по формуле: (цена фьючерса / (цена акции * лотность)) * 100
    const stockPrice = stock.currentPrice;
    const futuresPrice = futures.currentPrice;
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
    // Для сравнения фьючерсов используем простой процентный спред
    const nearPrice = nearFutures.currentPrice;
    const farPrice = farFutures.currentPrice;
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
                valueA = Math.max(a.stockVolume, a.futuresVolume);
                valueB = Math.max(b.stockVolume, b.futuresVolume);
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
function getPriceChangeClass(priceChange) {
    if (!priceChange) return 'price-neutral';
    const change = parseFloat(priceChange);
    if (change > 0) return 'price-up';
    if (change < 0) return 'price-down';
    return 'price-neutral';
}

function formatPrice(price) {
    if (!price) return '0';
    const num = parseFloat(price);
    if (num < 0.01 && num > 0) return num.toFixed(6);
    return num.toFixed(4);
}

function formatPercent(percent) {
    if (!percent) return '0.00%';
    const num = parseFloat(percent);
    if (Math.abs(num) < 0.01 && num !== 0) return num.toFixed(4) + '%';
    return num.toFixed(2) + '%';
}

function formatPercentValue(percentValue) {
    if (percentValue === null || percentValue === undefined) return '--';
    const num = Number(percentValue);
    if (!Number.isFinite(num)) return '--';
    return num.toFixed(2) + '%';
}

function formatAvgVolume(avgVolume) {
    if (!avgVolume) return '-';
    const num = parseFloat(avgVolume);
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
    return num.toFixed(0);
}

function formatTime(timestamp) {
    if (!timestamp) return '--:--:--';
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
}

function formatVolume(volume) {
    if (volume === null || volume === undefined) return '--';
    const v = Number(volume);
    if (!Number.isFinite(v)) return '--';
    return Math.round(v).toLocaleString();
}

function formatSpread(spread) {
    if (spread === null || spread === undefined) return '--';
    const num = Number(spread);
    if (!Number.isFinite(num)) return '--';
    if (Math.abs(num) < 0.01 && num !== 0) return num.toFixed(6);
    return num.toFixed(4);
}

function formatSpreadPercent(spreadPercent) {
    if (spreadPercent === null || spreadPercent === undefined) return '--';
    const num = Number(spreadPercent);
    if (!Number.isFinite(num)) return '--';
    if (Math.abs(num) < 0.01 && num !== 0) return num.toFixed(4) + '%';
    return num.toFixed(2) + '%';
}

// Подсветка изменения значения: сравнение с предыдущим сохраненным значением
function flashValueChange(cell, key, newValue, options) {
    const onlyUp = options && options.onlyUp === true;
    if (newValue === null || newValue === undefined || Number.isNaN(newValue)) return;
    const prevStore = previousValues.get(key) || {};
    const prev = prevStore[key];
    if (typeof prev === 'number' && !Number.isNaN(prev)) {
        if (newValue > prev) {
            cell.classList.remove('price-down');
            cell.classList.add('price-up');
        } else if (!onlyUp && newValue < prev) {
            cell.classList.remove('price-up');
            cell.classList.add('price-down');
        }
        setTimeout(() => {
            cell.classList.remove('price-up', 'price-down');
        }, 2000);
    }
    prevStore[key] = newValue;
    previousValues.set(key, prevStore);
}

// --- Индексы API/загрузка ---
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
        .then(response => (response.ok ? response.json() : null))
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

// Сессия
async function updateScannerStatus() {
    const now = new Date();
    const moscowTime = new Date(now.getTime() + 3 * 60 * 60 * 1000);

    let isTestMode = false;
    let isScannerActiveServer = null;

    try {
        const [testModeResp, sessionResp] = await Promise.all([
            fetch('/api/scanner/test-mode').catch(() => null),
            fetch('/api/scanner/futures-scanner/is-active').catch(() => null)
        ]);

        if (testModeResp && testModeResp.ok) {
            const data = await testModeResp.json();
            isTestMode = !!data.testModeEnabled;
        }
        if (sessionResp && sessionResp.ok) {
            const data = await sessionResp.json();
            isScannerActiveServer = !!data.isActive;
        }
    } catch (error) {
        console.warn('Не удалось получить статус окружения/сессии:', error);
    }

    // Клиентская проверка как фоллбек
    let isScannerActiveClient = true; // Всегда активен для фьючерсного сканера

    isSessionActive = isTestMode || (isScannerActiveServer !== null ? isScannerActiveServer : isScannerActiveClient);
    isTestModeGlobal = isTestMode;

    if (!isSessionActive && !isTestMode && websocket && websocket.readyState === WebSocket.OPEN) {
        disconnect();
    }

    // Отрисуем статус в карточке
    const statusEl = document.getElementById('scannerStatus');
    if (statusEl) {
        if (isTestMode) {
            statusEl.textContent = 'Тестовый режим';
            statusEl.style.color = '#1976d2';
        } else if (isSessionActive) {
            statusEl.textContent = 'Активен';
            statusEl.style.color = '#2e7d32';
        } else {
            statusEl.textContent = 'Выключен';
            statusEl.style.color = '#f57c00';
        }
    }
}

// --- Рендер таблиц ---
function renderInstrumentCell(quote) {
    const shortBadge = quote && quote.shortEnabled
        ? '<span class="badge-short" title="Шорт доступен">S</span>'
        : '';
    const divBadge = quote && quote.hasDividend
        ? '<span class="badge-div" title="Дивидендное событие: последний день покупки — на день раньше заявленной даты">D</span>'
        : '';
    return `<strong>${quote.ticker || quote.figi}</strong>${shortBadge}${divBadge}`;
}

function getChangeClass(change) {
    if (change === null || change === undefined) return '';
    return change > 0 ? 'positive' : change < 0 ? 'negative' : '';
}

function updateStockNearFuturesTable() {
    const tbody = document.getElementById('stockNearFuturesTableBody');
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

        // Подсветка изменений по ключевым полям
        const cells = row.querySelectorAll('td');
        flashValueChange(cells[3], `${comparison.baseTicker}_stock_price`, Number(comparison.stockPrice));
        flashValueChange(cells[4], `${comparison.baseTicker}_futures_price`, Number(comparison.futuresPrice));
        flashValueChange(cells[7], `${comparison.baseTicker}_spread_percent`, comparison.spreadPercent);
    });
}

function updateStockFarFuturesTable() {
    const tbody = document.getElementById('stockFarFuturesTableBody');
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

        // Подсветка изменений по ключевым полям
        const cells = row.querySelectorAll('td');
        flashValueChange(cells[3], `${comparison.baseTicker}_stock_price`, Number(comparison.stockPrice));
        flashValueChange(cells[4], `${comparison.baseTicker}_futures_price`, Number(comparison.futuresPrice));
        flashValueChange(cells[7], `${comparison.baseTicker}_spread_percent`, comparison.spreadPercent);
    });
}

function updateNearFarFuturesTable() {
    const tbody = document.getElementById('nearFarFuturesTableBody');
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

        // Подсветка изменений по ключевым полям
        const cells = row.querySelectorAll('td');
        flashValueChange(cells[3], `${comparison.baseTicker}_near_futures_price`, Number(comparison.nearPrice));
        flashValueChange(cells[4], `${comparison.baseTicker}_far_futures_price`, Number(comparison.farPrice));
        flashValueChange(cells[7], `${comparison.baseTicker}_spread_percent`, comparison.spreadPercent);
    });
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
        // Исторический объем утренней сессии из materialized view morning_session_volume
        historyVolumeData = { morningVolumes: data.morningVolumes || {} };
        // Текущий общий дневной объем из today_volume_view.total_volume
        todayVolumeData = { volumes: data.todayVolumes || {} };
    }
}

function getDisplayVolume(input) {
    const figi = typeof input === 'string' ? input : input.figi;
    if (totalVolumeCache.has(figi)) return totalVolumeCache.get(figi) || 0;
    const base = ensureBaseVolume(figi, quotes.get(figi) || {});
    const inc = getIncrementalVolume(figi);
    const total = base + inc;
    totalVolumeCache.set(figi, total);
    return total;
}

function getAvgVolumeFromHistory(figi) {
    if (historyVolumeData && historyVolumeData.morningVolumes) {
        return historyVolumeData.morningVolumes[figi] || 0;
    }
    return 0;
}

// Кэширование объемов
function ensureBaseVolume(figi, quoteData) {
    if (baseVolumeCache.has(figi)) return baseVolumeCache.get(figi);
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
    if (!isSessionActive && !isTestModeGlobal) {
        const last = incrementVolumeCache.get(figi) || 0;
        totalVolumeCache.set(figi, baseVolume + last);
        return last;
    }
    const serverTotal = (quoteData && typeof quoteData.totalVolume === 'number') ? quoteData.totalVolume : null;
    if (serverTotal !== null && serverTotal >= baseVolume) {
        const inc = serverTotal - baseVolume;
        incrementVolumeCache.set(figi, inc);
        totalVolumeCache.set(figi, serverTotal);
        return inc;
    }
    const lastInc = incrementVolumeCache.get(figi) || 0;
    const newVol = (quoteData && typeof quoteData.volume === 'number') ? quoteData.volume : 0;
    if (newVol > 0) {
        const upd = lastInc + newVol;
        incrementVolumeCache.set(figi, upd);
        totalVolumeCache.set(figi, baseVolume + upd);
        return upd;
    }
    incrementVolumeCache.set(figi, lastInc);
    totalVolumeCache.set(figi, baseVolume + lastInc);
    return lastInc;
}

function getIncrementalVolume(figi) { return incrementVolumeCache.get(figi) || 0; }

function updateVolumeDataForQuote(quoteData) {
    const figi = quoteData.figi;
    const base = ensureBaseVolume(figi, quoteData);
    const inc = updateIncrementalVolume(figi, quoteData, base);
    const total = base + inc;
    totalVolumeCache.set(figi, total);
    quoteData.totalVolume = total;
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
updateIndicesFromServer();
loadIndexPrices();
setTimeout(() => { loadClosePricesForAllQuotes(); }, 2000);
initializeVolumeData();

// Закрытие модального окна при клике вне его
window.onclick = function (event) {
    const modal = document.getElementById('indexManagementModal');
    if (event.target === modal) {
        modal.style.display = 'none';
    }
};
