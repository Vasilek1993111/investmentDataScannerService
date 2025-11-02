// Элементы DOM
const connectBtn = document.getElementById('connectBtn');
const disconnectBtn = document.getElementById('disconnectBtn');
const connectionStatus = document.getElementById('connectionStatus');
const gainersTableBody = document.getElementById('gainersTableBody');
const losersTableBody = document.getElementById('losersTableBody');
const activeInstruments = document.getElementById('activeInstruments');
const totalVolume = document.getElementById('totalVolume');
const updateRate = document.getElementById('updateRate');
const lastUpdate = document.getElementById('lastUpdate');

// Элементы для полоски индексов
const indicesContainer = document.getElementById('indicesContainer');

// Фильтры (удалены, так как не используются в выходном сканере)

// Функция для определения порта WebSocket
function getWebSocketPort() {
    const currentPort = window.location.port;
    if (currentPort === '8088') {
        return '8088';
    } else if (currentPort === '8085') {
        return '8085';
    } else {
        return '8088';
    }
}

// Состояние
let websocket = null;
let isConnected = false;
let quotes = new Map();
let gainers = [];
let losers = [];
let updateCount = 0;
let lastUpdateTime = null;
let updateTimer = null;
let baseVolumeCache = new Map();
let incrementVolumeCache = new Map();
let totalVolumeCache = new Map();
let previousValues = new Map(); // хранение предыдущих значений для подсветки изменений

// Индексы для полоски (динамический список)
let indices = new Map();
let INDICES_CONFIG = [
    { name: 'IMOEX2', displayName: 'IMOEX2' }
];

// Настройки сортировки
let gainersSortBy = 'changeOS';
let gainersSortOrder = 'desc';
let gainersMaxResults = 15;
let losersSortBy = 'changeOS';
let losersSortOrder = 'desc';
let losersMaxResults = 15;

const WEEKEND_MODE = true;

// Рендер названия инструмента с бейджем шорта
function renderInstrumentCell(quote) {
    const shortBadge = quote && quote.shortEnabled
        ? '<span class="badge-short" title="Шорт доступен">S</span>'
        : '';
    const divBadge = quote && quote.hasDividend
        ? '<span class="badge-div" title="Дивидендное событие: последний день покупки — на день раньше заявленной даты">D</span>'
        : '';
    return `<strong>${quote.ticker || quote.figi}</strong>${shortBadge}${divBadge}`;
}

function connect() {
    if (isConnected) return;

    try {
        const port = getWebSocketPort();
        websocket = new WebSocket(`ws://localhost:${port}/ws/quotes`);

        websocket.onopen = function () {
            console.log('WebSocket connected successfully');
            isConnected = true;
            connectBtn.disabled = true;
            disconnectBtn.disabled = false;
            connectionStatus.textContent = 'Подключено';
            connectionStatus.className = 'status connected';

            // Очищаем таблицы и состояние
            gainersTableBody.innerHTML = '<tr><td colspan="12" class="no-data">Нет данных</td></tr>';
            losersTableBody.innerHTML = '<tr><td colspan="12" class="no-data">Нет данных</td></tr>';
            quotes.clear();
            baseVolumeCache.clear();
            incrementVolumeCache.clear();
            totalVolumeCache.clear();
            gainers = [];
            losers = [];
            updateCount = 0;

            // Инициализируем полоску индексов
            initializeIndicesBar();

            // Таймер скорости обновлений
            updateTimer = setInterval(() => {
                updateRate.textContent = updateCount + '/сек';
                updateCount = 0;
            }, 1000);

            updateWeekendStatus();
        };

        websocket.onmessage = function (event) {
            try {
                console.log('WebSocket received data:', event.data);
                const quoteData = JSON.parse(event.data);
                console.log('Parsed quote data:', quoteData);
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
            console.log('WebSocket error details:', error);
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
    const indexInfo = indices.get(figi) || (quoteData.ticker ? indices.get(quoteData.ticker) : null);
    if (indexInfo) {
        // Если это индекс и у него еще нет цен закрытия, загружаем их
        if (!indexInfo.closePriceOS || !indexInfo.closePriceEvening) {
            loadIndexPricesForSingleIndex(indexInfo, figi);
        }
    }

    updateIndicesBar(quoteData);
    updateTopLists();
}

/**
 * Загрузить цены закрытия для одного индекса
 */
function loadIndexPricesForSingleIndex(indexInfo, figi) {
    if (!figi) {
        // Пытаемся найти FIGI по тикеру
        figi = findFigiByTicker(indexInfo.name);
        if (!figi) return;
    }

    // Загружаем цены из кэша
    fetch(`/api/price-cache/prices/${figi}`)
        .then(response => {
            if (!response.ok) {
                return null;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.prices) {
                const element = indexInfo.element;
                const osPriceElement = element.querySelector('.index-os-price');
                const eveningPriceElement = element.querySelector('.index-evening-price');

                const closePrice = data.prices.closePrice;
                const eveningSessionPrice = data.prices.eveningSessionPrice;

                // Обновляем цены в DOM
                if (closePrice && closePrice > 0 && (!indexInfo.closePriceOS || indexInfo.closePriceOS !== closePrice)) {
                    if (osPriceElement) osPriceElement.textContent = `ОС: ${formatPrice(closePrice)}`;
                    indexInfo.closePriceOS = closePrice;
                    // Обновляем котировку, если она есть
                    if (indexInfo.data) {
                        indexInfo.data.closePriceOS = closePrice;
                        indexInfo.data.closePrice = closePrice;
                        updateIndicesBar(indexInfo.data);
                    }
                }

                if (eveningSessionPrice && eveningSessionPrice > 0 && (!indexInfo.closePriceEvening || indexInfo.closePriceEvening !== eveningSessionPrice)) {
                    if (eveningPriceElement) eveningPriceElement.textContent = `ВС: ${formatPrice(eveningSessionPrice)}`;
                    indexInfo.closePriceEvening = eveningSessionPrice;
                    // Обновляем котировку, если она есть
                    if (indexInfo.data) {
                        indexInfo.data.closePriceVS = eveningSessionPrice;
                        updateIndicesBar(indexInfo.data);
                    }
                }
            }
        })
        .catch(error => {
            // Тихо игнорируем ошибки
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

    // Получаем исторический объем для выходного дня
    if (historyVolumeData && historyVolumeData.weekendExchangeAvgVolumesPerDay) {
        const historicalVolume = historyVolumeData.weekendExchangeAvgVolumesPerDay[figi];
        if (historicalVolume !== undefined && historicalVolume !== null) {
            quoteData.avgVolumeWeekend = Number(historicalVolume) || 0;
        } else {
            // Если для данного FIGI нет данных, используем функцию getAvgVolumeFromHistory как фоллбек
            quoteData.avgVolumeWeekend = getAvgVolumeFromHistory(figi);
        }
    } else {
        // Если исторические данные еще не загружены, используем функцию getAvgVolumeFromHistory
        quoteData.avgVolumeWeekend = getAvgVolumeFromHistory(figi);
    }
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
    indices.clear();
    INDICES_CONFIG.forEach(config => {
        const indexElement = createIndexElement(config);
        indicesContainer.appendChild(indexElement);
        // Сохраняем по FIGI и по name/ticker для обратной совместимости
        const indexInfo = { ...config, element: indexElement, data: null, closePriceOS: null, closePriceEvening: null };
        if (config.figi) {
            indices.set(config.figi, indexInfo);
        }
        if (config.name) {
            indices.set(config.name, indexInfo);
        }
    });
}

function createIndexElement(config) {
    const div = document.createElement('div');
    div.className = 'index-item';
    div.id = `index-${config.name}`;
    div.innerHTML = `
        <div class="index-name">${config.displayName}</div>
        <div class="index-prices">
            <div class="index-os-price">ОС: --</div>
            <div class="index-evening-price">ВС: --</div>
        </div>
        <div class="index-current">--</div>
        <div class="index-change-os neutral">--</div>
        <div class="index-change-vs neutral">--</div>
        <div class="index-time">--:--</div>
    `;
    return div;
}

function updateIndicesBar(quoteData) {
    const figi = quoteData.figi;
    const ticker = quoteData.ticker;

    // Ищем индекс по FIGI или по тикеру (для обратной совместимости)
    let indexInfo = indices.get(figi);
    if (!indexInfo && ticker) {
        indexInfo = indices.get(ticker);
        // Если нашли по тикеру, но нет FIGI в конфиге, сохраняем FIGI
        if (indexInfo && !indexInfo.figi) {
            indexInfo.figi = figi;
            indices.set(figi, indexInfo);
        }
    }
    if (!indexInfo) return;

    const element = indexInfo.element;
    const currentElement = element.querySelector('.index-current');
    const changeOsElement = element.querySelector('.index-change-os');
    const changeVsElement = element.querySelector('.index-change-vs');
    const timeElement = element.querySelector('.index-time');
    const osPriceElement = element.querySelector('.index-os-price');
    const eveningPriceElement = element.querySelector('.index-evening-price');

    if (!quoteData.currentPrice) {
        currentElement.textContent = '--';
        if (changeOsElement) {
            changeOsElement.textContent = '--';
            changeOsElement.className = 'index-change-os neutral';
        }
        if (changeVsElement) {
            changeVsElement.textContent = '--';
            changeVsElement.className = 'index-change-vs neutral';
        }
        timeElement.textContent = '--:--';
        return;
    }

    const previousPrice = indexInfo.data ? indexInfo.data.currentPrice : null;
    const currentPrice = quoteData.currentPrice;

    currentElement.textContent = formatPrice(quoteData.currentPrice);

    // Изменение от ОС (сверху)
    const priceOS = quoteData.closePriceOS || indexInfo.closePriceOS || quoteData.closePrice;
    if (priceOS && priceOS > 0) {
        // Обновляем отображение цены ОС, если она изменилась
        if (quoteData.closePriceOS) {
            const newPriceOS = Number(quoteData.closePriceOS);
            const oldPriceOS = Number(indexInfo.closePriceOS) || 0;
            if (newPriceOS > 0 && Math.abs(newPriceOS - oldPriceOS) > 0.0001) {
                indexInfo.closePriceOS = newPriceOS;
                if (osPriceElement) osPriceElement.textContent = `ОС: ${formatPrice(newPriceOS)}`;
            }
        } else if (!indexInfo.closePriceOS) {
            const priceOSNum = Number(priceOS);
            if (priceOSNum > 0) {
                indexInfo.closePriceOS = priceOSNum;
                if (osPriceElement) osPriceElement.textContent = `ОС: ${formatPrice(priceOSNum)}`;
            }
        }

        const change = quoteData.currentPrice - priceOS;
        const changePercent = (change / priceOS) * 100;
        const changeClass = changePercent > 0 ? 'positive' : changePercent < 0 ? 'negative' : 'neutral';
        const changeText = changePercent >= 0 ? `+${formatPercent(changePercent)}` : formatPercent(changePercent);
        if (changeOsElement) {
            changeOsElement.textContent = changeText;
            changeOsElement.className = `index-change-os ${changeClass}`;
        }
    } else {
        if (changeOsElement) {
            changeOsElement.textContent = '--';
            changeOsElement.className = 'index-change-os neutral';
        }
    }

    // Изменение от ВС (снизу)
    const priceVS = quoteData.closePriceVS || indexInfo.closePriceEvening;
    if (priceVS && priceVS > 0) {
        // Обновляем отображение цены ВС, если она изменилась
        if (quoteData.closePriceVS) {
            const newPriceVS = Number(quoteData.closePriceVS);
            const oldPriceVS = Number(indexInfo.closePriceEvening) || 0;
            if (newPriceVS > 0 && Math.abs(newPriceVS - oldPriceVS) > 0.0001) {
                indexInfo.closePriceEvening = newPriceVS;
                if (eveningPriceElement) eveningPriceElement.textContent = `ВС: ${formatPrice(newPriceVS)}`;
            }
        } else if (!indexInfo.closePriceEvening) {
            const priceVSNum = Number(priceVS);
            if (priceVSNum > 0) {
                indexInfo.closePriceEvening = priceVSNum;
                if (eveningPriceElement) eveningPriceElement.textContent = `ВС: ${formatPrice(priceVSNum)}`;
            }
        }

        const change = quoteData.currentPrice - priceVS;
        const changePercent = (change / priceVS) * 100;
        const changeClass = changePercent > 0 ? 'positive' : changePercent < 0 ? 'negative' : 'neutral';
        const changeText = changePercent >= 0 ? `+${formatPercent(changePercent)}` : formatPercent(changePercent);
        if (changeVsElement) {
            changeVsElement.textContent = changeText;
            changeVsElement.className = `index-change-vs ${changeClass}`;
        }
    } else {
        if (changeVsElement) {
            changeVsElement.textContent = '--';
            changeVsElement.className = 'index-change-vs neutral';
        }
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

    // Сохраняем котировку с учетом уже существующих цен закрытия
    // Используем более свежее значение: если в котировке есть значение, используем его, иначе используем сохраненное
    if (!quoteData.closePriceOS && indexInfo.closePriceOS) {
        quoteData.closePriceOS = indexInfo.closePriceOS;
        quoteData.closePrice = indexInfo.closePriceOS;
    }
    if (!quoteData.closePriceVS && indexInfo.closePriceEvening) {
        quoteData.closePriceVS = indexInfo.closePriceEvening;
    }
    // Также обновляем котировку свежими значениями из indexInfo, если они были обновлены выше
    if (indexInfo.closePriceOS && !quoteData.closePriceOS) {
        quoteData.closePriceOS = indexInfo.closePriceOS;
        quoteData.closePrice = indexInfo.closePriceOS;
    }
    if (indexInfo.closePriceEvening && !quoteData.closePriceVS) {
        quoteData.closePriceVS = indexInfo.closePriceEvening;
    }
    indexInfo.data = quoteData;
}

function updateTopLists() {
    const quotesArray = Array.from(quotes.values());

    // Обновляем исторический объем для всех котировок перед обработкой
    quotesArray.forEach(quote => {
        updateVolumeDataForQuote(quote);
    });

    const validQuotes = quotesArray.filter(quote => {
        const hasOSPrice = quote.closePriceOS && quote.closePriceOS > 0;
        const hasClosePrice = quote.closePrice && quote.closePrice > 0;
        const hasCurrentPrice = quote.currentPrice && quote.currentPrice > 0;
        return (hasOSPrice || hasClosePrice) && hasCurrentPrice;
    });

    const gainersQuotes = validQuotes.filter(quote => {
        const priceOS = quote.closePriceOS || quote.closePrice;
        if (!priceOS || priceOS <= 0) return false;
        const change = ((quote.currentPrice - priceOS) / priceOS) * 100;
        return change > 0;
    });

    const losersQuotes = validQuotes.filter(quote => {
        const priceOS = quote.closePriceOS || quote.closePrice;
        if (!priceOS || priceOS <= 0) return false;
        const change = ((quote.currentPrice - priceOS) / priceOS) * 100;
        return change < 0;
    });

    gainers = sortQuotesAdvanced([...gainersQuotes], gainersSortBy, gainersSortOrder).slice(0, gainersMaxResults);
    losers = sortLosersQuotes([...losersQuotes], losersSortBy, losersSortOrder).slice(0, losersMaxResults);

    updateGainersTable();
    updateLosersTable();
}

// --- Сортировки ---
function updateSortingSettings() {
    gainersSortBy = document.getElementById('gainersSortBy').value;
    gainersSortOrder = document.getElementById('gainersSortOrder').value;
    gainersMaxResults = parseInt(document.getElementById('gainersMaxResults').value);

    losersSortBy = document.getElementById('losersSortBy').value;
    losersSortOrder = document.getElementById('losersSortOrder').value;
    losersMaxResults = parseInt(document.getElementById('losersMaxResults').value);

    updateTableTitles();
    updateTopLists();
}

function updateTableTitles() {
    const gainersTitle = document.getElementById('gainersTitle');
    const losersTitle = document.getElementById('losersTitle');

    let gainersSortText = '';
    if (gainersSortOrder === 'volume_desc' || gainersSortOrder === 'volume_asc') {
        gainersSortText = 'по объему';
    } else if (gainersSortOrder === 'spread_desc') {
        gainersSortText = 'по спреду';
    } else if (gainersSortOrder === 'volume_excess_desc') {
        gainersSortText = 'по превышению исторического объема';
    } else {
        switch (gainersSortBy) {
            case 'changeOS':
                gainersSortText = 'относительно ОС';
                break;
            case 'priceVS':
                gainersSortText = 'по изменению от ВС';
                break;
        }
    }
    let gainersOrderText = '';
    if (gainersSortOrder === 'desc') gainersOrderText = 'самые растущие';
    else if (gainersSortOrder === 'asc') gainersOrderText = 'менее растущие';
    else if (gainersSortOrder === 'volume_desc') gainersOrderText = 'наибольший объем';
    else if (gainersSortOrder === 'volume_asc') gainersOrderText = 'наименьший объем';
    else if (gainersSortOrder === 'spread_desc') gainersOrderText = 'наибольший спред';
    else if (gainersSortOrder === 'volume_excess_desc') gainersOrderText = 'максимальное превышение';
    const gainersResultsText = `Топ-${gainersMaxResults}`;
    gainersTitle.textContent = `🚀 ${gainersResultsText} растущих (${gainersSortText}, ${gainersOrderText})`;

    let losersSortText = '';
    if (losersSortOrder === 'volume_desc' || losersSortOrder === 'volume_asc') {
        losersSortText = 'по объему';
    } else if (losersSortOrder === 'spread_desc') {
        losersSortText = 'по спреду';
    } else if (losersSortOrder === 'volume_excess_desc') {
        losersSortText = 'по превышению исторического объема';
    } else {
        switch (losersSortBy) {
            case 'changeOS':
                losersSortText = 'относительно ОС';
                break;
            case 'priceVS':
                losersSortText = 'по изменению от ВС';
                break;
        }
    }
    let losersOrderText = '';
    if (losersSortOrder === 'desc') losersOrderText = 'самые падающие';
    else if (losersSortOrder === 'asc') losersOrderText = 'менее падающие';
    else if (losersSortOrder === 'volume_desc') losersOrderText = 'наибольший объем';
    else if (losersSortOrder === 'volume_asc') losersOrderText = 'наименьший объем';
    else if (losersSortOrder === 'spread_desc') losersOrderText = 'наибольший спред';
    else if (losersSortOrder === 'volume_excess_desc') losersOrderText = 'максимальное превышение';
    const losersResultsText = `Топ-${losersMaxResults}`;
    losersTitle.textContent = `📉 ${losersResultsText} падающих (${losersSortText}, ${losersOrderText})`;
}

function sortQuotes(quotes, sortBy, sortOrder) {
    return quotes.sort((a, b) => {
        let valueA, valueB;
        switch (sortBy) {
            case 'changeOS':
                const priceOS_A = a.closePriceOS || a.closePrice;
                const priceOS_B = b.closePriceOS || b.closePrice;
                valueA = priceOS_A ? ((a.currentPrice - priceOS_A) / priceOS_A) * 100 : 0;
                valueB = priceOS_B ? ((b.currentPrice - priceOS_B) / priceOS_B) * 100 : 0;
                break;
            case 'priceVS':
                const priceVS_A = a.closePriceVS || a.closePrice;
                const priceVS_B = b.closePriceVS || b.closePrice;
                valueA = priceVS_A && priceVS_A > 0 ? ((a.currentPrice - priceVS_A) / priceVS_A) * 100 : 0;
                valueB = priceVS_B && priceVS_B > 0 ? ((b.currentPrice - priceVS_B) / priceVS_B) * 100 : 0;
                break;
            default:
                valueA = 0;
                valueB = 0;
        }
        return sortOrder === 'desc' ? valueB - valueA : valueA - valueB;
    });
}

function sortLosersQuotes(quotes, sortBy, sortOrder) {
    return quotes.sort((a, b) => {
        let valueA, valueB;
        if (sortOrder === 'volume_desc' || sortOrder === 'volume_asc') {
            valueA = a.totalVolume || 0;
            valueB = b.totalVolume || 0;
            return sortOrder === 'volume_desc' ? valueB - valueA : valueA - valueB;
        }
        if (sortOrder === 'volume_excess_desc') {
            const currentVolumeA = a.totalVolume || 0;
            const historicalVolumeA = getAvgVolumeFromHistory(a.figi) || 0;
            const excessA = historicalVolumeA > 0 ? (currentVolumeA / historicalVolumeA) : 0;
            const currentVolumeB = b.totalVolume || 0;
            const historicalVolumeB = getAvgVolumeFromHistory(b.figi) || 0;
            const excessB = historicalVolumeB > 0 ? (currentVolumeB / historicalVolumeB) : 0;
            return excessB - excessA;
        }
        if (sortOrder === 'spread_desc') {
            const spreadA = calculateSpreadPercent(a.bestBid, a.bestAsk, a.currentPrice) || 0;
            const spreadB = calculateSpreadPercent(b.bestBid, b.bestAsk, b.currentPrice) || 0;
            return spreadB - spreadA;
        }
        switch (sortBy) {
            case 'changeOS':
                const priceOS_A = a.closePriceOS || a.closePrice;
                const priceOS_B = b.closePriceOS || b.closePrice;
                valueA = priceOS_A ? ((a.currentPrice - priceOS_A) / priceOS_A) * 100 : 0;
                valueB = priceOS_B ? ((b.currentPrice - priceOS_B) / priceOS_B) * 100 : 0;
                break;
            case 'priceVS':
                const priceVS_A = a.closePriceVS || a.closePrice;
                const priceVS_B = b.closePriceVS || b.closePrice;
                valueA = priceVS_A && priceVS_A > 0 ? ((a.currentPrice - priceVS_A) / priceVS_A) * 100 : 0;
                valueB = priceVS_B && priceVS_B > 0 ? ((b.currentPrice - priceVS_B) / priceVS_B) * 100 : 0;
                break;
            default:
                valueA = 0;
                valueB = 0;
        }
        // Специальная обработка для случая "Изменение от ВС %" + "Сначала самые падающие"
        // Для падающих бумаг (отрицательные значения) при сортировке "desc" нужно сначала показывать самые отрицательные
        // Используем сравнение по абсолютным значениям для корректной сортировки падающих
        if (sortBy === 'priceVS' && sortOrder === 'desc') {
            // Для падающих: сравниваем абсолютные значения, но сохраняем знак
            // valueA = -15% (более падающий), valueB = -5% (менее падающий)
            // Math.abs(valueA) = 15, Math.abs(valueB) = 5
            // Math.abs(valueB) - Math.abs(valueA) = 5 - 15 = -10 (отрицательное) → a перед b → правильно
            return Math.abs(valueB) - Math.abs(valueA);
        }
        // Специальная обработка для случая "Изменение от ВС %" + "Сначала менее падающие"
        // Для падающих бумаг (отрицательные значения) при сортировке "asc" нужно сначала показывать менее отрицательные
        if (sortBy === 'priceVS' && sortOrder === 'asc') {
            // Для падающих: сравниваем абсолютные значения
            // valueA = -15% (более падающий), valueB = -5% (менее падающий)
            // Math.abs(valueA) = 15, Math.abs(valueB) = 5
            // Math.abs(valueA) - Math.abs(valueB) = 15 - 5 = 10 (положительное) → b перед a → правильно
            return Math.abs(valueA) - Math.abs(valueB);
        }
        return sortOrder === 'desc' ? valueA - valueB : valueB - valueA;
    });
}

function sortQuotesAdvanced(quotes, sortBy, sortOrder) {
    return quotes.sort((a, b) => {
        let valueA, valueB;
        if (sortOrder === 'volume_desc' || sortOrder === 'volume_asc') {
            valueA = a.totalVolume || 0;
            valueB = b.totalVolume || 0;
            return sortOrder === 'volume_desc' ? valueB - valueA : valueA - valueB;
        }
        if (sortOrder === 'volume_excess_desc') {
            const currentVolumeA = a.totalVolume || 0;
            const historicalVolumeA = getAvgVolumeFromHistory(a.figi) || 0;
            const excessA = historicalVolumeA > 0 ? (currentVolumeA / historicalVolumeA) : 0;
            const currentVolumeB = b.totalVolume || 0;
            const historicalVolumeB = getAvgVolumeFromHistory(b.figi) || 0;
            const excessB = historicalVolumeB > 0 ? (currentVolumeB / historicalVolumeB) : 0;
            return excessB - excessA;
        }
        if (sortOrder === 'spread_desc') {
            const spreadA = calculateSpreadPercent(a.bestBid, a.bestAsk, a.currentPrice) || 0;
            const spreadB = calculateSpreadPercent(b.bestBid, b.bestAsk, b.currentPrice) || 0;
            return spreadB - spreadA;
        }
        switch (sortBy) {
            case 'changeOS':
                const priceOS_A = a.closePriceOS || a.closePrice;
                const priceOS_B = b.closePriceOS || b.closePrice;
                valueA = priceOS_A ? ((a.currentPrice - priceOS_A) / priceOS_A) * 100 : 0;
                valueB = priceOS_B ? ((b.currentPrice - priceOS_B) / priceOS_B) * 100 : 0;
                break;
            case 'priceVS':
                const priceVS_A = a.closePriceVS || a.closePrice;
                const priceVS_B = b.closePriceVS || b.closePrice;
                valueA = priceVS_A && priceVS_A > 0 ? ((a.currentPrice - priceVS_A) / priceVS_A) * 100 : 0;
                valueB = priceVS_B && priceVS_B > 0 ? ((b.currentPrice - priceVS_B) / priceVS_B) * 100 : 0;
                break;
            default:
                valueA = 0;
                valueB = 0;
        }
        return sortOrder === 'desc' ? valueB - valueA : valueA - valueB;
    });
}

// Возвращает исторический (средний) объем для FIGI из загруженных данных
function getAvgVolumeFromHistory(figi) {
    if (!historyVolumeData) return 0;
    // Пытаемся взять точечное среднее по инструменту, если есть
    if (historyVolumeData.weekendExchangeAvgVolumesPerDay
        && historyVolumeData.weekendExchangeAvgVolumesPerDay[figi] !== undefined) {
        return Number(historyVolumeData.weekendExchangeAvgVolumesPerDay[figi]) || 0;
    }
    // Фоллбек: если в исторических данных ничего нет, вернем 0
    return 0;
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

function formatAvgVolume(avgVolume) {
    if (!avgVolume) return '-';
    const num = parseFloat(avgVolume);
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
    return num.toFixed(0);
}

function calculatePriceChangePercent(currentPrice, referencePrice) {
    if (!currentPrice || !referencePrice || referencePrice <= 0) return 0;
    return ((currentPrice - referencePrice) / referencePrice) * 100;
}

function formatPriceWithPercent(price, currentPrice) {
    if (!price) return formatPrice(price);
    const percent = calculatePriceChangePercent(currentPrice, price);
    let percentStr;
    if (Math.abs(percent) < 0.01 && percent !== 0) {
        percentStr = percent >= 0 ? `+${percent.toFixed(4)}%` : `${percent.toFixed(4)}%`;
    } else {
        percentStr = percent >= 0 ? `+${percent.toFixed(2)}%` : `${percent.toFixed(2)}%`;
    }
    const percentClass = percent > 0 ? 'positive' : percent < 0 ? 'negative' : '';
    return `${formatPrice(price)} <span class="price-percent ${percentClass}">(${percentStr})</span>`;
}

function formatTime(timestamp) {
    if (!timestamp) return '--:--:--';
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
}

function formatPriceWithChange(price, currentPrice) {
    if (!price || !currentPrice) return formatPrice(price);
    const priceValue = parseFloat(price);
    const currentValue = parseFloat(currentPrice);
    const change = currentValue - priceValue;
    const changePercent = (change / priceValue) * 100;
    const changeClass = change > 0 ? 'price-up' : change < 0 ? 'price-down' : 'price-neutral';
    let changeText;
    if (Math.abs(changePercent) < 0.01 && changePercent !== 0) {
        changeText = change > 0 ? `+${changePercent.toFixed(4)}%` : `${changePercent.toFixed(4)}%`;
    } else {
        changeText = change > 0 ? `+${changePercent.toFixed(2)}%` : `${changePercent.toFixed(2)}%`;
    }
    return `${formatPrice(price)} <span class="${changeClass}" style="font-size: 0.8em;">(${changeText})</span>`;
}

function formatBidAsk(price, quantity) {
    if (!price || price === 0) return '<span style="color: #999;">--</span>';
    const priceStr = formatPrice(price);
    const quantityStr = quantity > 0 ? quantity.toLocaleString() : '0';
    return `${priceStr} <span style="font-size: 0.8em; color: #666;">(${quantityStr})</span>`;
}

function formatPriceChange(change) {
    if (!change || change === 0) return '<span style="color: #999;">--</span>';
    const changeValue = parseFloat(change);
    const changeClass = changeValue > 0 ? 'price-up' : changeValue < 0 ? 'price-down' : 'price-neutral';
    let changeText;
    if (Math.abs(changeValue) < 0.01 && changeValue !== 0) {
        changeText = changeValue > 0 ? `+${changeValue.toFixed(6)}` : changeValue.toFixed(6);
    } else {
        changeText = changeValue > 0 ? `+${changeValue.toFixed(4)}` : changeValue.toFixed(4);
    }
    return `<span class="${changeClass}">${changeText}</span>`;
}

function formatPriceChangePercent(percent) {
    if (!percent || percent === 0) return '<span style="color: #999;">--</span>';
    const percentValue = parseFloat(percent);
    const changeClass = percentValue > 0 ? 'price-up' : percentValue < 0 ? 'price-down' : 'price-neutral';
    let percentText;
    if (Math.abs(percentValue) < 0.01 && percentValue !== 0) {
        percentText = percentValue > 0 ? `+${percentValue.toFixed(4)}%` : `${percentValue.toFixed(4)}%`;
    } else {
        percentText = percentValue > 0 ? `+${percentValue.toFixed(2)}%` : `${percentValue.toFixed(2)}%`;
    }
    return `<span class="${changeClass}">${percentText}</span>`;
}

// --- API индексов ---
function updateIndicesFromServer() {
    fetch('/api/scanner/weekend-scanner/indices')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                INDICES_CONFIG = data.indices;
                const indicesContainer = document.getElementById('indicesContainer');

                // Сохраняем старые данные индексов перед очисткой
                const oldIndicesData = new Map();
                indices.forEach((value, key) => {
                    oldIndicesData.set(key, {
                        closePriceOS: value.closePriceOS,
                        closePriceEvening: value.closePriceEvening,
                        data: value.data
                    });
                });

                indicesContainer.innerHTML = '';
                indices.clear();
                INDICES_CONFIG.forEach(config => {
                    const indexElement = createIndexElement(config);
                    indicesContainer.appendChild(indexElement);
                    // Сохраняем по FIGI и по name/ticker для обратной совместимости
                    const indexInfo = { ...config, element: indexElement, data: null, closePriceOS: null, closePriceEvening: null };

                    // Восстанавливаем старые данные, если они были
                    const oldData = oldIndicesData.get(config.figi) || oldIndicesData.get(config.name);
                    if (oldData) {
                        indexInfo.closePriceOS = oldData.closePriceOS;
                        indexInfo.closePriceEvening = oldData.closePriceEvening;
                        indexInfo.data = oldData.data;

                        // Восстанавливаем отображение цен в DOM
                        const osPriceElement = indexElement.querySelector('.index-os-price');
                        const eveningPriceElement = indexElement.querySelector('.index-evening-price');
                        if (oldData.closePriceOS && osPriceElement) {
                            osPriceElement.textContent = `ОС: ${formatPrice(oldData.closePriceOS)}`;
                        }
                        if (oldData.closePriceEvening && eveningPriceElement) {
                            eveningPriceElement.textContent = `ВС: ${formatPrice(oldData.closePriceEvening)}`;
                        }
                    }

                    if (config.figi) {
                        indices.set(config.figi, indexInfo);
                    }
                    if (config.name) {
                        indices.set(config.name, indexInfo);
                    }

                    // Если есть котировка с текущей ценой, обновляем отображение после добавления в indices
                    if (oldData && oldData.data && oldData.data.currentPrice) {
                        // Используем setTimeout чтобы индекс точно был добавлен в Map
                        setTimeout(() => {
                            // Убеждаемся, что котировка содержит все необходимые данные
                            const quoteData = oldData.data;
                            if (!quoteData.closePriceOS && oldData.closePriceOS) {
                                quoteData.closePriceOS = oldData.closePriceOS;
                                quoteData.closePrice = oldData.closePriceOS;
                            }
                            if (!quoteData.closePriceVS && oldData.closePriceEvening) {
                                quoteData.closePriceVS = oldData.closePriceEvening;
                            }
                            updateIndicesBar(quoteData);
                        }, 0);
                    }
                });

                // Загружаем цены только для тех индексов, у которых их еще нет
                loadIndexPrices();
            }
        })
        .catch(error => {
            console.error('Ошибка при обновлении индексов:', error);
        });
}

/**
 * Найти FIGI по тикеру из приходящих котировок
 */
function findFigiByTicker(ticker) {
    if (!ticker) return null;

    // Ищем в текущих котировках
    for (const [figi, quote] of quotes.entries()) {
        if (quote.ticker === ticker || quote.ticker === ticker.toUpperCase()) {
            return figi;
        }
    }

    return null;
}

function loadIndexPrices() {
    // Сначала пытаемся загрузить через API сканера (для обратной совместимости)
    fetch('/api/scanner/weekend-scanner/indices/prices')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                Object.values(data.prices).forEach(priceData => {
                    const indexInfo = indices.get(priceData.name) || indices.get(priceData.figi);
                    if (indexInfo) {
                        // Обновляем только если цен еще нет
                        if (!indexInfo.closePriceOS && priceData.closePriceOS) {
                            const element = indexInfo.element;
                            const osPriceElement = element.querySelector('.index-os-price');
                            if (osPriceElement) osPriceElement.textContent = `ОС: ${formatPrice(priceData.closePriceOS)}`;
                            indexInfo.closePriceOS = priceData.closePriceOS;
                        }
                        if (!indexInfo.closePriceEvening && priceData.closePriceEvening) {
                            const element = indexInfo.element;
                            const eveningPriceElement = element.querySelector('.index-evening-price');
                            if (eveningPriceElement) eveningPriceElement.textContent = `ВС: ${formatPrice(priceData.closePriceEvening)}`;
                            indexInfo.closePriceEvening = priceData.closePriceEvening;
                        }
                    }
                });
            }
        })
        .catch(error => {
            console.error('Ошибка при загрузке цен закрытия через API сканера:', error);
        });

    // Затем загружаем цены из кэша для всех индексов по FIGI
    INDICES_CONFIG.forEach(config => {
        // Определяем FIGI: сначала из конфига, потом пытаемся найти по тикеру
        let figi = config.figi;
        if (!figi) {
            // Если FIGI нет, пытаемся найти по тикеру из приходящих котировок
            figi = findFigiByTicker(config.name);
            if (figi) {
                // Обновляем конфиг, чтобы сохранить найденный FIGI
                config.figi = figi;
                // Также обновляем в индексах
                const indexInfo = indices.get(config.name);
                if (indexInfo) {
                    indexInfo.figi = figi;
                    indices.set(figi, indexInfo);
                }
            }
        }

        // Если FIGI все еще нет, используем name как fallback
        if (!figi) {
            figi = config.name;
        }

        if (!figi) return;

        const indexInfo = indices.get(figi) || indices.get(config.name);
        if (!indexInfo) return;

        // Если цены уже загружены, пропускаем
        if (indexInfo.closePriceOS && indexInfo.closePriceEvening) {
            return;
        }

        // Загружаем цены из кэша
        fetch(`/api/price-cache/prices/${figi}`)
            .then(response => {
                if (!response.ok) {
                    // Если не удалось загрузить по FIGI, пытаемся найти FIGI по тикеру из котировок
                    if (response.status === 404 && !config.figi) {
                        const foundFigi = findFigiByTicker(config.name);
                        if (foundFigi && foundFigi !== figi) {
                            // Повторяем запрос с найденным FIGI
                            return fetch(`/api/price-cache/prices/${foundFigi}`).catch(() => null);
                        }
                    }
                    console.warn(`Failed to load prices for ${figi} (ticker: ${config.name}): ${response.status}`);
                    return null;
                }
                return response.json();
            })
            .then(data => {
                if (data && data.prices) {
                    const element = indexInfo.element;
                    const osPriceElement = element.querySelector('.index-os-price');
                    const eveningPriceElement = element.querySelector('.index-evening-price');

                    const closePrice = data.prices.closePrice;
                    const eveningSessionPrice = data.prices.eveningSessionPrice;

                    // Обновляем цены в DOM только если их еще нет
                    if (closePrice && closePrice > 0 && !indexInfo.closePriceOS) {
                        if (osPriceElement) osPriceElement.textContent = `ОС: ${formatPrice(closePrice)}`;
                        indexInfo.closePriceOS = closePrice;
                        // Обновляем котировку, если она есть
                        if (indexInfo.data) {
                            indexInfo.data.closePriceOS = closePrice;
                            indexInfo.data.closePrice = closePrice;
                        }
                    }

                    if (eveningSessionPrice && eveningSessionPrice > 0 && !indexInfo.closePriceEvening) {
                        if (eveningPriceElement) eveningPriceElement.textContent = `ВС: ${formatPrice(eveningSessionPrice)}`;
                        indexInfo.closePriceEvening = eveningSessionPrice;
                        // Обновляем котировку, если она есть
                        if (indexInfo.data) {
                            indexInfo.data.closePriceVS = eveningSessionPrice;
                        }
                    }

                    // Обновляем отображение изменения, если есть текущая цена
                    if (indexInfo.data && indexInfo.data.currentPrice && closePrice) {
                        updateIndicesBar(indexInfo.data);
                    }
                }
            })
            .catch(error => {
                console.error(`Ошибка при загрузке цен из кэша для ${figi} (ticker: ${config.name}):`, error);
            });
    });
}

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
                updateTopLists();
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
                    updateTopLists();
                }
            }
        })
        .catch(error => {
            // Тихо игнорируем ошибки, так как не все инструменты могут иметь цену вечерней сессии
        });
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

function isWeekend() {
    const now = new Date();
    const moscowTime = new Date(now.getTime() + (3 * 60 * 60 * 1000));
    const dayOfWeek = moscowTime.getDay();
    return dayOfWeek === 0 || dayOfWeek === 6;
}

// Корректная проверка выходного дня по московскому времени через Intl API
function isWeekendMoscow() {
    try {
        const label = new Date().toLocaleDateString('en-US', { timeZone: 'Europe/Moscow', weekday: 'short' });
        return label === 'Sat' || label === 'Sun';
    } catch (e) {
        // Фоллбек: если браузер не поддерживает timeZone, используем локальную зону
        const day = new Date().getDay();
        return day === 0 || day === 6;
    }
}

async function loadHistoryVolumeData() {
    try {
        const response = await fetch('/api/price-cache/volumes');
        if (!response.ok) return null;
        const data = await response.json();

        // Поддерживаем разные варианты структуры данных
        if (data.avgVolumesPerDay && data.avgVolumesPerDay.weekendExchangeAvgVolumesPerDay) {
            data.weekendExchangeAvgVolumesPerDay = data.avgVolumesPerDay.weekendExchangeAvgVolumesPerDay;
        } else if (!data.weekendExchangeAvgVolumesPerDay) {
            // Если данных нет в ожидаемой структуре, создаем пустой объект
            data.weekendExchangeAvgVolumesPerDay = {};
        }

        return data;
    } catch (error) {
        console.error('Ошибка при загрузке исторических данных:', error);
        return null;
    }
}

// Глобальные кэши для объемов
let historyVolumeData = null;
let todayVolumeData = null;

async function initializeVolumeData() {
    const data = await loadHistoryVolumeData();
    if (data) {
        historyVolumeData = data;
        if (data.todayVolumes) {
            todayVolumeData = { volumes: data.todayVolumes };
        }
    }
    updateAllQuotesWithVolumeData();
}

function updateAllQuotesWithVolumeData() {
    quotes.forEach((quoteData, figi) => {
        updateVolumeDataForQuote(quoteData);
    });
    updateTopLists();
    updateTotalVolume();
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
    fetch('/api/scanner/weekend-scanner/indices')
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
    fetch('/api/scanner/weekend-scanner/indices/add', {
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
                // Даем время для обновления индексов, затем загружаем цены несколько раз с задержками
                setTimeout(() => {
                    loadIndexPrices();
                    // Повторная загрузка через 1 секунду для новых индексов
                    setTimeout(() => { loadIndexPrices(); }, 1000);
                    // Еще одна попытка через 3 секунды, если котировки уже приходят
                    setTimeout(() => { loadIndexPrices(); }, 3000);
                }, 500);
                alert('Индекс успешно добавлен! Цены будут загружены автоматически.');
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
    fetch('/api/scanner/weekend-scanner/indices/remove', {
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

async function updateWeekendStatus() {
    const weekendStatusElement = document.getElementById('weekendStatus');
    let isTestMode = false;
    let isWeekendActiveServer = null;

    try {
        const [testResp, sessionResp] = await Promise.all([
            fetch('/api/scanner/test-mode').catch(() => null),
            fetch('/api/scanner/weekend-scanner/is-weekend-session').catch(() => null)
        ]);

        if (testResp && testResp.ok) {
            const data = await testResp.json();
            isTestMode = !!data.testModeEnabled;
        }
        if (sessionResp && sessionResp.ok) {
            const data = await sessionResp.json();
            isWeekendActiveServer = !!data.isWeekendSession;
        }
    } catch (e) {
        console.warn('Не удалось получить статус выходного дня с сервера, используем локальную проверку', e);
    }

    // Локальный оверрайд тестового режима через URL/LocalStorage (для отладки)
    try {
        const params = new URLSearchParams(window.location.search);
        if (params.get('test') === '1' || localStorage.getItem('forceTestMode') === '1') {
            isTestMode = true;
        }
    } catch (e) { /* ignore */ }

    // Фоллбек: определяем по московскому времени на клиенте
    const isWeekendDay = isWeekendMoscow();
    const isActive = isTestMode || (isWeekendActiveServer !== null ? isWeekendActiveServer : isWeekendDay);

    if (!weekendStatusElement) return;
    if (isTestMode) {
        weekendStatusElement.textContent = 'Тестовый режим';
        weekendStatusElement.style.color = '#1976d2';
    } else if (isActive) {
        weekendStatusElement.textContent = 'Активен';
        weekendStatusElement.style.color = '#2e7d32';
    } else {
        weekendStatusElement.textContent = 'Выключен';
        weekendStatusElement.style.color = '#f57c00';
    }
}

// Инициализация
connectBtn.addEventListener('click', connect);
disconnectBtn.addEventListener('click', disconnect);
document.getElementById('gainersSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('gainersSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('gainersMaxResults').addEventListener('change', updateSortingSettings);
document.getElementById('losersSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('losersSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('losersMaxResults').addEventListener('change', updateSortingSettings);

initializeIndicesBar();
initializeVolumeData();
setInterval(updateWeekendStatus, 60000);
updateWeekendStatus();
updateIndicesFromServer();
loadIndexPrices();
setTimeout(() => { loadClosePricesForAllQuotes(); }, 2000);

// Функции для обновления таблиц
function updateGainersTable() {
    const tbody = document.getElementById('gainersTableBody');
    if (!tbody) return;

    if (gainers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="12" class="no-data">Нет данных</td></tr>';
        return;
    }

    tbody.innerHTML = '';
    gainers.forEach(quote => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${renderInstrumentCell(quote)}</td>
            <td>${formatPrice(quote.currentPrice)}</td>
            <td>${formatPrice(quote.closePriceOS || quote.closePrice)}</td>
            <td>${formatPrice(quote.closePriceVS)}</td>
            <td class="${getChangeClass(quote.closePriceChangePercent)}">${formatPercentValue(quote.closePriceChangePercent)}</td>
            <td>${formatBidAsk(quote.bestBid, quote.bestBidQuantity)}</td>
            <td>${formatBidAsk(quote.bestAsk, quote.bestAskQuantity)}</td>
            <td>${formatVolume(quote.totalVolume ?? quote.volume)}</td>
            <td>${formatVolume(quote.avgVolumeWeekend)}</td>
            <td>${formatPercentValue(calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice))}</td>
            <td>${formatTime(quote.timestamp)}</td>
        `;
        tbody.appendChild(row);

        // Подсветка изменений по ключевым полям (как в индексах)
        const cells = row.querySelectorAll('td');
        flashValueChange(cells[1], quote.figi, 'currentPrice', Number(quote.currentPrice)); // Текущая цена
        flashValueChange(cells[4], quote.figi, 'changeOS', Number(quote.closePriceChangePercent)); // Изменение от ОС %
        flashValueChange(cells[5], quote.figi, 'bestBid', Number(quote.bestBid)); // BID
        flashValueChange(cells[6], quote.figi, 'bestAsk', Number(quote.bestAsk)); // ASK
        const spreadPercent = calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice);
        if (spreadPercent !== null) {
            flashValueChange(cells[9], quote.figi, 'spread', spreadPercent); // Спред
        }
    });
}

function updateLosersTable() {
    const tbody = document.getElementById('losersTableBody');
    if (!tbody) return;

    if (losers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="12" class="no-data">Нет данных</td></tr>';
        return;
    }

    tbody.innerHTML = '';
    losers.forEach(quote => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${renderInstrumentCell(quote)}</td>
            <td>${formatPrice(quote.currentPrice)}</td>
            <td>${formatPrice(quote.closePriceOS || quote.closePrice)}</td>
            <td>${formatPrice(quote.closePriceVS)}</td>
            <td class="${getChangeClass(quote.closePriceChangePercent)}">${formatPercentValue(quote.closePriceChangePercent)}</td>
            <td>${formatBidAsk(quote.bestBid, quote.bestBidQuantity)}</td>
            <td>${formatBidAsk(quote.bestAsk, quote.bestAskQuantity)}</td>
            <td>${formatVolume(quote.totalVolume ?? quote.volume)}</td>
            <td>${formatVolume(quote.avgVolumeWeekend)}</td>
            <td>${formatPercentValue(calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice))}</td>
            <td>${formatTime(quote.timestamp)}</td>
        `;
        tbody.appendChild(row);

        // Подсветка изменений по ключевым полям (как в индексах)
        const cells = row.querySelectorAll('td');
        flashValueChange(cells[1], quote.figi, 'currentPrice', Number(quote.currentPrice)); // Текущая цена
        flashValueChange(cells[4], quote.figi, 'changeOS', Number(quote.closePriceChangePercent)); // Изменение от ОС %
        flashValueChange(cells[5], quote.figi, 'bestBid', Number(quote.bestBid)); // BID
        flashValueChange(cells[6], quote.figi, 'bestAsk', Number(quote.bestAsk)); // ASK
        const spreadPercent = calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice);
        if (spreadPercent !== null) {
            flashValueChange(cells[9], quote.figi, 'spread', spreadPercent); // Спред
        }
    });
}

// Вспомогательные функции форматирования
function formatPrice(price) {
    if (price === null || price === undefined) return '--';
    const num = Number(price);
    if (!isFinite(num)) return '--';
    if (num === 0) return '0';
    if (Math.abs(num) < 0.01) return num.toFixed(6); // очень мелкие цены
    if (Math.abs(num) < 1) return num.toFixed(4);    // больше значащих цифр для цен < 1
    return num.toFixed(2);
}

// Форматирование процентов, если значение уже в процентах (например, 1.23 -> 1.23%)
function formatPercentValue(percentValue) {
    if (percentValue === null || percentValue === undefined) return '--';
    return Number(percentValue).toFixed(2) + '%';
}

function formatLots(lots) {
    if (lots === null || lots === undefined) return '--';
    return Number(lots).toLocaleString();
}

function formatVolume(volume) {
    if (volume === null || volume === undefined) return '--';
    const v = Number(volume);
    if (!Number.isFinite(v)) return '--';
    return Math.round(v).toLocaleString();
}

function formatSpread(spread) {
    if (spread === null || spread === undefined) return '--';
    return Number(spread).toFixed(2);
}

// Расчет спреда по лучшему бид/аск
function calculateSpread(bestBid, bestAsk) {
    if (bestBid === null || bestBid === undefined || bestAsk === null || bestAsk === undefined) return null;
    return Number(bestAsk) - Number(bestBid);
}

// Процентный спред: (ask - bid) / basePrice * 100, где basePrice = currentPrice или mid
function calculateSpreadPercent(bestBid, bestAsk, currentPrice) {
    const bid = Number(bestBid);
    const ask = Number(bestAsk);
    if (!isFinite(bid) || !isFinite(ask) || bid <= 0 || ask <= 0) return null;
    const spreadAbs = ask - bid;
    let base = Number(currentPrice);
    if (!(base > 0)) {
        base = (ask + bid) / 2;
    }
    if (!(base > 0)) return null;
    return (spreadAbs / base) * 100;
}

// Подсветка изменения значения: сравнение с предыдущим сохраненным значением (как в индексах)
function flashValueChange(cell, figi, key, newValue, options) {
    const onlyUp = options && options.onlyUp === true;
    if (newValue === null || newValue === undefined || Number.isNaN(newValue)) return;
    const prevStore = previousValues.get(figi) || {};
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
    previousValues.set(figi, prevStore);
}

function formatTime(time) {
    if (!time) return '--';
    const date = new Date(time);
    return date.toLocaleTimeString('ru-RU');
}

function getChangeClass(change) {
    if (change === null || change === undefined) return '';
    return change > 0 ? 'positive' : change < 0 ? 'negative' : '';
}

// Закрытие модалки
window.onclick = function (event) {
    const modal = document.getElementById('indexManagementModal');
    if (event.target === modal) {
        modal.style.display = 'none';
    }
};


