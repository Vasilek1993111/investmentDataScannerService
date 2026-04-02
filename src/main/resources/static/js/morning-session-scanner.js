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
// Флаги и кэши объемов (как в weekend-сканере)
let isSessionActive = false;
let isTestModeGlobal = false;
let baseVolumeCache = new Map();
let incrementVolumeCache = new Map();
let totalVolumeCache = new Map();
let previousValues = new Map(); // хранение предыдущих значений для подсветки изменений

// Индексы для полоски (используются из indices-bar.js)
// Переменные indices и INDICES_CONFIG объявлены в indices-bar.js

// Настройки сортировки
let gainersSortBy = 'changeOS';
let gainersSortOrder = 'desc';
let gainersMaxResults = 15;
let losersSortBy = 'changeOS';
let losersSortOrder = 'desc';
let losersMaxResults = 15;

// Время утренней сессии (06:50:00–09:59:59 МСК)
const MORNING_SESSION_START_HOUR = 6;
const MORNING_SESSION_START_MINUTE = 50;
const MORNING_SESSION_END_HOUR = 9;
const MORNING_SESSION_END_MINUTE = 59;
const MORNING_SESSION_END_SECOND = 59;

function connect() {
  if (isConnected) return;
  try {
    websocket = new WebSocket(getWebSocketUrl('/ws/quotes'));

    websocket.onopen = function () {
      isConnected = true;
      connectBtn.disabled = true;
      disconnectBtn.disabled = false;
      connectionStatus.textContent = 'Подключено';
      connectionStatus.className = 'status connected';

      gainersTableBody.innerHTML = '<tr><td colspan="11" class="no-data">Нет данных</td></tr>';
      losersTableBody.innerHTML = '<tr><td colspan="11" class="no-data">Нет данных</td></tr>';
      quotes.clear();
      baseVolumeCache.clear();
      incrementVolumeCache.clear();
      totalVolumeCache.clear();
      previousValues.clear();
      gainers = [];
      losers = [];
      updateCount = 0;

      // Сохраняем данные индексов при подключении, чтобы не обнулять их
      initializeIndicesBar(true);

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

  // Сохраняем цену ВС из предыдущей котировки, если она уже была загружена
  // Цена ВС не обновляется в течение дня, только загружается один раз при первой загрузке
  const existingQuote = quotes.get(figi);
  if (existingQuote && existingQuote.closePriceVS) {
    quoteData.closePriceVS = existingQuote.closePriceVS;
    // Пересчитываем изменение от ВС % на основе текущей цены и сохраненной цены ВС
    if (quoteData.closePriceVS && quoteData.currentPrice) {
      quoteData.closePriceVSChangePercent = calculatePriceChangePercent(quoteData.currentPrice, quoteData.closePriceVS);
    }
  } else if (quoteData.closePriceVS && quoteData.currentPrice) {
    // Если цена ВС пришла с сервера, но изменение от ВС % не пришло или равно 0, пересчитываем
    if (!quoteData.closePriceVSChangePercent || quoteData.closePriceVSChangePercent === 0) {
      quoteData.closePriceVSChangePercent = calculatePriceChangePercent(quoteData.currentPrice, quoteData.closePriceVS);
    }
  }

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
  updateTopLists();
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
// Функции для работы с индексами вынесены в indices-bar.js

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

// Форматирование процентов, если значение уже в процентах (например, 1.23 -> 1.23%)
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

// Расчет спреда по лучшему бид/аск (в процентах относительно текущей/средней цены)
function calculateSpreadPercent(bestBid, bestAsk, currentPrice) {
  const bid = Number(bestBid);
  const ask = Number(bestAsk);
  if (!Number.isFinite(bid) || !Number.isFinite(ask) || bid <= 0 || ask <= 0) return null;
  const spreadAbs = ask - bid;
  let base = Number(currentPrice);
  if (!(base > 0)) {
    base = (ask + bid) / 2;
  }
  if (!(base > 0)) return null;
  return (spreadAbs / base) * 100;
}

// --- Индексы API/загрузка ---
// updateIndicesFromServer, loadIndexPrices вынесены в indices-bar.js

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
  const currentSecond = moscowTime.getSeconds();

  let isTestMode = false;
  let isMorningSessionServer = null;

  try {
    const [testModeResp, sessionResp] = await Promise.all([
      fetch('/api/scanner/test-mode').catch(() => null),
      fetch('/api/scanner/is-morning-session').catch(() => null)
    ]);

    if (testModeResp && testModeResp.ok) {
      const data = await testModeResp.json();
      isTestMode = !!data.testModeMorning;
    }
    if (sessionResp && sessionResp.ok) {
      const data = await sessionResp.json();
      isMorningSessionServer = !!data.isMorningSession;
    }
  } catch (error) {
    console.warn('Не удалось получить статус окружения/сессии:', error);
  }

  // Клиентская проверка времени МСК как фоллбек (только рабочие дни)
  let isMorningSessionClient = false;
  const day = moscowTime.getDay();
  const isWeekday = day >= 1 && day <= 5;
  if (isWeekday) {
    if (currentHour > MORNING_SESSION_START_HOUR && currentHour < MORNING_SESSION_END_HOUR) isMorningSessionClient = true;
    else if (currentHour === MORNING_SESSION_START_HOUR && currentMinute >= MORNING_SESSION_START_MINUTE) isMorningSessionClient = true;
    else if (currentHour === MORNING_SESSION_END_HOUR && (currentMinute <= MORNING_SESSION_END_MINUTE && currentSecond <= MORNING_SESSION_END_SECOND)) isMorningSessionClient = true;
  }

  isSessionActive = isTestMode || (isMorningSessionServer !== null ? isMorningSessionServer : isMorningSessionClient);
  isTestModeGlobal = isTestMode;

  if (!isSessionActive && !isTestMode && websocket && websocket.readyState === WebSocket.OPEN) {
    disconnect();
  }

  // Отрисуем статус в карточке
  const statusEl = document.getElementById('morningStatus');
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

function formatVolume(volume) {
  if (volume === null || volume === undefined) return '--';
  const v = Number(volume);
  if (!Number.isFinite(v)) return '--';
  return Math.round(v).toLocaleString();
}

function updateGainersTable() {
  const tbody = document.getElementById('gainersTableBody');
  if (!tbody) return;

  if (!gainers || gainers.length === 0) {
    tbody.innerHTML = '<tr><td colspan="13" class="no-data">Нет данных</td></tr>';
    return;
  }

  tbody.innerHTML = '';
  gainers.forEach(quote => {
    const priceOS = quote.closePriceOS || quote.closePrice;
    const changeOSPercent = quote.closePriceChangePercent !== undefined && quote.closePriceChangePercent !== null
      ? quote.closePriceChangePercent
      : calculatePriceChangePercent(quote.currentPrice, priceOS);
    // Всегда пересчитываем изменение от ВС %, так как текущая цена может обновляться
    const changeVSPercent = quote.closePriceVS && quote.currentPrice
      ? calculatePriceChangePercent(quote.currentPrice, quote.closePriceVS)
      : (quote.closePriceVSChangePercent !== undefined && quote.closePriceVSChangePercent !== null
        ? quote.closePriceVSChangePercent
        : null);
    const row = document.createElement('tr');
    const displayVolume = getDisplayVolume(quote);
    const histVolume = (typeof getAvgVolumeFromHistory === 'function') ? getAvgVolumeFromHistory(quote.figi) : 0;
    row.innerHTML = `
      <td>${renderInstrumentCell(quote)}</td>
      <td>${formatPrice(quote.currentPrice)}</td>
      <td>${formatPrice(quote.openPrice)}</td>
      <td>${formatPrice(priceOS)}</td>
      <td class="${getChangeClass(changeOSPercent)}">${formatPercent(changeOSPercent)}</td>
      <td>${formatPrice(quote.closePriceVS)}</td>
      <td class="${getChangeClass(changeVSPercent)}">${formatPercent(changeVSPercent)}</td>
      <td>${formatBidAsk(quote.bestBid, quote.bestBidQuantity)}</td>
      <td>${formatBidAsk(quote.bestAsk, quote.bestAskQuantity)}</td>
      <td>${formatVolume(displayVolume)}</td>
      <td>${formatAvgVolume(histVolume)}</td>
      <td>${formatPercentValue(calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice))}</td>
      <td>${formatTime(quote.timestamp)}</td>
    `;
    tbody.appendChild(row);

    // Подсветка изменений по ключевым полям (как в индексах)
    const cells = row.querySelectorAll('td');
    flashValueChange(cells[1], quote.figi, 'currentPrice', Number(quote.currentPrice)); // Текущая цена
    flashValueChange(cells[4], quote.figi, 'changeOS', changeOSPercent); // Изменение от ОС %
    flashValueChange(cells[6], quote.figi, 'changeVS', changeVSPercent); // Изменение от ВС %
    flashValueChange(cells[7], quote.figi, 'bestBid', Number(quote.bestBid)); // BID
    flashValueChange(cells[8], quote.figi, 'bestAsk', Number(quote.bestAsk)); // ASK
    const spreadPercent = calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice);
    if (spreadPercent !== null) {
      flashValueChange(cells[11], quote.figi, 'spread', spreadPercent); // Спред
    }
  });
}

function updateLosersTable() {
  const tbody = document.getElementById('losersTableBody');
  if (!tbody) return;

  if (!losers || losers.length === 0) {
    tbody.innerHTML = '<tr><td colspan="13" class="no-data">Нет данных</td></tr>';
    return;
  }

  tbody.innerHTML = '';
  losers.forEach(quote => {
    const priceOS = quote.closePriceOS || quote.closePrice;
    const changeOSPercent = quote.closePriceChangePercent !== undefined && quote.closePriceChangePercent !== null
      ? quote.closePriceChangePercent
      : calculatePriceChangePercent(quote.currentPrice, priceOS);
    // Всегда пересчитываем изменение от ВС %, так как текущая цена может обновляться
    const changeVSPercent = quote.closePriceVS && quote.currentPrice
      ? calculatePriceChangePercent(quote.currentPrice, quote.closePriceVS)
      : (quote.closePriceVSChangePercent !== undefined && quote.closePriceVSChangePercent !== null
        ? quote.closePriceVSChangePercent
        : null);
    const row = document.createElement('tr');
    const displayVolume = getDisplayVolume(quote);
    const histVolume = (typeof getAvgVolumeFromHistory === 'function') ? getAvgVolumeFromHistory(quote.figi) : 0;
    row.innerHTML = `
      <td>${renderInstrumentCell(quote)}</td>
      <td>${formatPrice(quote.currentPrice)}</td>
      <td>${formatPrice(quote.openPrice)}</td>
      <td>${formatPrice(priceOS)}</td>
      <td class="${getChangeClass(changeOSPercent)}">${formatPercent(changeOSPercent)}</td>
      <td>${formatPrice(quote.closePriceVS)}</td>
      <td class="${getChangeClass(changeVSPercent)}">${formatPercent(changeVSPercent)}</td>
      <td>${formatBidAsk(quote.bestBid, quote.bestBidQuantity)}</td>
      <td>${formatBidAsk(quote.bestAsk, quote.bestAskQuantity)}</td>
      <td>${formatVolume(displayVolume)}</td>
      <td>${formatAvgVolume(histVolume)}</td>
      <td>${formatPercentValue(calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice))}</td>
      <td>${formatTime(quote.timestamp)}</td>
    `;
    tbody.appendChild(row);

    // Подсветка изменений по ключевым полям (как в индексах)
    const cells = row.querySelectorAll('td');
    flashValueChange(cells[1], quote.figi, 'currentPrice', Number(quote.currentPrice)); // Текущая цена
    flashValueChange(cells[4], quote.figi, 'changeOS', changeOSPercent); // Изменение от ОС %
    flashValueChange(cells[6], quote.figi, 'changeVS', changeVSPercent); // Изменение от ВС %
    flashValueChange(cells[7], quote.figi, 'bestBid', Number(quote.bestBid)); // BID
    flashValueChange(cells[8], quote.figi, 'bestAsk', Number(quote.bestAsk)); // ASK
    const spreadPercent = calculateSpreadPercent(quote.bestBid, quote.bestAsk, quote.currentPrice);
    if (spreadPercent !== null) {
      flashValueChange(cells[11], quote.figi, 'spread', spreadPercent); // Спред
    }
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

function isWeekend() {
  const now = new Date();
  const dayOfWeek = now.getDay();
  return dayOfWeek === 0 || dayOfWeek === 6;
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

// Кэширование объемов как в weekend-сканере
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
// toggleIndexManagement, loadCurrentIndices, displayCurrentIndices, addIndex, removeIndex вынесены в indices-bar.js

// Обработчики
connectBtn.addEventListener('click', connect);
disconnectBtn.addEventListener('click', disconnect);
document.getElementById('gainersSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('gainersSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('gainersMaxResults').addEventListener('change', updateSortingSettings);
document.getElementById('losersSortBy').addEventListener('change', updateSortingSettings);
document.getElementById('losersSortOrder').addEventListener('change', updateSortingSettings);
document.getElementById('losersMaxResults').addEventListener('change', updateSortingSettings);

// Инициализация модуля индексов
initIndicesBar({
  apiEndpoint: '/api/scanner/morning-scanner',
  quotesMap: quotes,
  formatPrice: formatPrice,
  formatPercent: (percent) => formatPercent(percent).replace('%', ''),
  lastUpdateTime: lastUpdateTime,
  onIndexUpdate: (indexInfo, quoteData) => {
    // Callback при обновлении индекса (если нужен)
  }
});

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


