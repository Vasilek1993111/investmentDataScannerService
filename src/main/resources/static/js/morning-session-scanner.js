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
let gainers = [];
let losers = [];

// Глобальные переменные для кэширования данных
let historyVolumeData = null;
let todayVolumeData = null;
let updateCount = 0;
let lastUpdateTime = null;
let updateTimer = null;

// Индексы для полоски
let indices = new Map();
let INDICES_CONFIG = [
  { figi: 'BBG00KDWPPW3', name: 'IMOEX2', displayName: 'IMOEX2' }
];

// Настройки сортировки
let gainersSortBy = 'changeOS';
let gainersSortOrder = 'desc';
let gainersMaxResults = 15;
let losersSortBy = 'changeOS';
let losersSortOrder = 'desc';
let losersMaxResults = 15;

// Время утренней сессии (07:00-09:59 МСК)
const MORNING_SESSION_START_HOUR = 6;
const MORNING_SESSION_START_MINUTE = 50;
const MORNING_SESSION_END_HOUR = 9;
const MORNING_SESSION_END_MINUTE = 49;
const MORNING_SESSION_END_SECOND = 59;

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

      gainersTableBody.innerHTML = '<tr><td colspan="11" class="no-data">Нет данных</td></tr>';
      losersTableBody.innerHTML = '<tr><td colspan="11" class="no-data">Нет данных</td></tr>';
      quotes.clear();
      gainers = [];
      losers = [];
      updateCount = 0;

      initializeIndicesBar();

      updateTimer = setInterval(() => {
        updateRate.textContent = updateCount + '/сек';
        updateCount = 0;
      }, 1000);

      updateSessionStatus();
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
  quotes.set(figi, quoteData);
  updateCount++;
  lastUpdateTime = new Date();

  activeInstruments.textContent = quotes.size;
  updateTotalVolume();
  lastUpdate.textContent = lastUpdateTime.toLocaleTimeString();

  updateIndicesBar(quoteData);
  updateTopLists();
}

function updateTotalVolume() {
  let total = 0;
  quotes.forEach(quote => {
    total += quote.totalVolume || 0;
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

function updateTopLists() {
  const quotesArray = Array.from(quotes.values());
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
  const gainersResultsText = `Топ-${gainersMaxResults}`;
  gainersTitle.textContent = `🚀 ${gainersResultsText} растущих (${gainersSortText}, ${gainersOrderText})`;

  let losersSortText = '';
  if (losersSortOrder === 'volume_desc' || losersSortOrder === 'volume_asc') {
    losersSortText = 'по объему';
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

function sortQuotesAdvanced(quotes, sortBy, sortOrder) {
  return quotes.sort((a, b) => {
    let valueA, valueB;
    if (sortOrder === 'volume_desc' || sortOrder === 'volume_asc') {
      valueA = a.totalVolume || 0;
      valueB = b.totalVolume || 0;
      return sortOrder === 'volume_desc' ? valueB - valueA : valueA - valueB;
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
  return `${priceStr} <span style=\"font-size: 0.8em; color: #666;\">(${quantityStr})</span>`;
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

// --- Индексы API/загрузка ---
function updateIndicesFromServer() {
  fetch('/api/scanner/morning-scanner/indices')
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
  fetch('/api/scanner/morning-scanner/indices/prices')
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
        updateTopLists();
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
async function updateSessionStatus() {
  const now = new Date();
  const moscowTime = new Date(now.getTime() + 3 * 60 * 60 * 1000);
  const currentHour = moscowTime.getHours();
  const currentMinute = moscowTime.getMinutes();
  const currentSecond = moscowTime.getSecond();

  let isTestMode = false;
  try {
    const response = await fetch('/api/morning-scanner/test-mode');
    if (response.ok) {
      const data = await response.json();
      isTestMode = data.testModeEnabled || false;
    }
  } catch (error) {
    console.warn('Не удалось получить статус тестового режима:', error);
  }

  let isSessionActive = false;
  if (isTestMode) isSessionActive = true;
  else if (currentHour > MORNING_SESSION_START_HOUR && currentHour < MORNING_SESSION_END_HOUR) isSessionActive = true;
  else if (currentHour === MORNING_SESSION_START_HOUR && currentMinute >= MORNING_SESSION_START_MINUTE) isSessionActive = true;
  else if (currentHour === MORNING_SESSION_END_HOUR && (currentMinute < MORNING_SESSION_END_MINUTE || (currentMinute === MORNING_SESSION_END_MINUTE && currentSecond <= MORNING_SESSION_END_SECOND))) isSessionActive = true;

  if (!isSessionActive && !isTestMode && websocket && websocket.readyState === WebSocket.OPEN) {
    disconnect();
  }
}

// Исторические объемы
async function loadHistoryVolumeData() {
  try {
    const response = await fetch('/api/price-cache/volumes');
    if (!response.ok) return null;
    const data = await response.json();
    if (data.avgVolumesPerDay) {
      data.weekendExchangeAvgVolumesPerDay = data.avgVolumesPerDay.weekendExchangeAvgVolumesPerDay || {};
    }
    return data;
  } catch (error) {
    console.error('Ошибка при загрузке исторических данных:', error);
    return null;
  }
}

function isWeekend() {
  const now = new Date();
  const dayOfWeek = now.getDay();
  return dayOfWeek === 0 || dayOfWeek === 6;
}

async function initializeVolumeData() {
  const data = await loadHistoryVolumeData();
  if (data) {
    historyVolumeData = data;
    if (isWeekend() && data.todayVolumes) {
      todayVolumeData = { volumes: data.todayVolumes };
    }
  }
}

function getDisplayVolume(figi) {
  if (isWeekend() && todayVolumeData && todayVolumeData.volumes) {
    return todayVolumeData.volumes[figi] || 0;
  }
  return 0;
}

function getAvgVolumeFromHistory(figi) {
  if (historyVolumeData && historyVolumeData.weekendExchangeAvgVolumesPerDay) {
    return historyVolumeData.weekendExchangeAvgVolumesPerDay[figi] || 0;
  }
  return 0;
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
  fetch('/api/scanner/morning-scanner/indices')
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
  fetch('/api/scanner/morning-scanner/indices/add', {
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
  fetch('/api/scanner/morning-scanner/indices/remove', {
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
document.getElementById('gainersSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('gainersSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('gainersMaxResults').addEventListener('change', updateSortingSettings);
document.getElementById('losersSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('losersSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('losersMaxResults').addEventListener('change', updateSortingSettings);

setInterval(updateSessionStatus, 60000);
updateSessionStatus();
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


