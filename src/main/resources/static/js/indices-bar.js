/**
 * Универсальный модуль для работы с полоской индексов
 * Используется во всех сканерах: weekend-scanner, morning-session-scanner, futures-scanner
 */

// Глобальные переменные для индексов (будут доступны из основного файла)
// Используем window для глобального доступа из всех файлов
if (typeof window.indices === 'undefined') {
    window.indices = new Map();
}
if (typeof window.INDICES_CONFIG === 'undefined') {
    window.INDICES_CONFIG = [];
}

// Конфигурация модуля (должна быть установлена перед использованием)
let indicesBarConfig = {
    apiEndpoint: '', // Базовый endpoint для API (например, '/api/scanner/weekend-scanner')
    quotesMap: null, // Map с котировками для поиска FIGI
    formatPrice: null, // Функция форматирования цены
    formatPercent: null, // Функция форматирования процента
    lastUpdateTime: null, // Время последнего обновления
    onIndexUpdate: null // Callback при обновлении индекса
};

/**
 * Инициализация модуля индексов
 * @param {Object} config - Конфигурация модуля
 */
function initIndicesBar(config) {
    indicesBarConfig = { ...indicesBarConfig, ...config };
}

/**
 * Инициализация полоски индексов
 */
function initializeIndicesBar() {
    const indicesContainer = document.getElementById('indicesContainer');
    if (!indicesContainer) {
        console.warn('indicesContainer not found');
        return;
    }

    indicesContainer.innerHTML = '';
    window.indices.clear();
    window.INDICES_CONFIG.forEach(config => {
        const indexElement = createIndexElement(config);
        indicesContainer.appendChild(indexElement);
        // Сохраняем по FIGI и по name/ticker для обратной совместимости
        const indexInfo = { ...config, element: indexElement, data: null, closePriceOS: null, closePriceEvening: null };
        if (config.figi) {
            window.indices.set(config.figi, indexInfo);
        }
        if (config.name) {
            window.indices.set(config.name, indexInfo);
        }
    });
}

/**
 * Создание элемента индекса
 */
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

/**
 * Обновление полоски индексов
 */
function updateIndicesBar(quoteData) {
    const figi = quoteData.figi;
    const ticker = quoteData.ticker;

    // Ищем индекс по FIGI или по тикеру (для обратной совместимости)
    let indexInfo = window.indices.get(figi);
    if (!indexInfo && ticker) {
        indexInfo = window.indices.get(ticker);
        // Если нашли по тикеру, но нет FIGI в конфиге, сохраняем FIGI
        if (indexInfo && !indexInfo.figi) {
            indexInfo.figi = figi;
            window.indices.set(figi, indexInfo);
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

    // Используем функцию форматирования из конфига или дефолтную
    const formatPrice = indicesBarConfig.formatPrice || ((price) => price.toFixed(2));
    const formatPercent = indicesBarConfig.formatPercent || ((percent) => percent.toFixed(2));
    const lastUpdateTime = indicesBarConfig.lastUpdateTime;

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
    if (!quoteData.closePriceOS && indexInfo.closePriceOS) {
        quoteData.closePriceOS = indexInfo.closePriceOS;
        quoteData.closePrice = indexInfo.closePriceOS;
    }
    if (!quoteData.closePriceVS && indexInfo.closePriceEvening) {
        quoteData.closePriceVS = indexInfo.closePriceEvening;
    }
    if (indexInfo.closePriceOS && !quoteData.closePriceOS) {
        quoteData.closePriceOS = indexInfo.closePriceOS;
        quoteData.closePrice = indexInfo.closePriceOS;
    }
    if (indexInfo.closePriceEvening && !quoteData.closePriceVS) {
        quoteData.closePriceVS = indexInfo.closePriceEvening;
    }
    indexInfo.data = quoteData;

    // Вызываем callback, если он установлен
    if (indicesBarConfig.onIndexUpdate) {
        indicesBarConfig.onIndexUpdate(indexInfo, quoteData);
    }
}

/**
 * Найти FIGI по тикеру из приходящих котировок
 */
function findFigiByTicker(ticker) {
    if (!ticker) return null;
    if (!indicesBarConfig.quotesMap) return null;

    // Ищем в текущих котировках
    for (const [figi, quote] of indicesBarConfig.quotesMap.entries()) {
        if (quote.ticker === ticker || quote.ticker === ticker.toUpperCase()) {
            return figi;
        }
    }

    return null;
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

                const formatPrice = indicesBarConfig.formatPrice || ((price) => price.toFixed(2));

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
            console.error('Ошибка при загрузке цен для индекса:', error);
        });
}

/**
 * Обновление индексов с сервера
 */
function updateIndicesFromServer() {
    if (!indicesBarConfig.apiEndpoint) {
        console.warn('API endpoint not configured');
        return;
    }

    fetch(`${indicesBarConfig.apiEndpoint}/indices`)
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                window.INDICES_CONFIG = data.indices;
                const indicesContainer = document.getElementById('indicesContainer');

                // Сохраняем старые данные индексов перед очисткой
                const oldIndicesData = new Map();
                window.indices.forEach((value, key) => {
                    oldIndicesData.set(key, {
                        closePriceOS: value.closePriceOS,
                        closePriceEvening: value.closePriceEvening,
                        data: value.data
                    });
                });

                indicesContainer.innerHTML = '';
                window.indices.clear();
                window.INDICES_CONFIG.forEach(config => {
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
                        const formatPrice = indicesBarConfig.formatPrice || ((price) => price.toFixed(2));
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
                        window.indices.set(config.figi, indexInfo);
                    }
                    if (config.name) {
                        window.indices.set(config.name, indexInfo);
                    }

                    // Если есть котировка с текущей ценой, обновляем отображение после добавления в indices
                    if (oldData && oldData.data && oldData.data.currentPrice) {
                        setTimeout(() => {
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
 * Загрузка цен индексов
 */
function loadIndexPrices() {
    if (!indicesBarConfig.apiEndpoint) {
        console.warn('API endpoint not configured');
        return;
    }

    // Сначала пытаемся загрузить через API сканера (для обратной совместимости)
    fetch(`${indicesBarConfig.apiEndpoint}/indices/prices`)
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const formatPrice = indicesBarConfig.formatPrice || ((price) => price.toFixed(2));
                Object.values(data.prices).forEach(priceData => {
                    const indexInfo = window.indices.get(priceData.name) || window.indices.get(priceData.figi);
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
    window.INDICES_CONFIG.forEach(config => {
        let figi = config.figi;
        if (!figi) {
            figi = findFigiByTicker(config.name);
            if (figi) {
                // Обновляем конфиг, чтобы сохранить найденный FIGI
                config.figi = figi;
                // Также обновляем в индексах
                const indexInfo = window.indices.get(config.name);
                if (indexInfo) {
                    indexInfo.figi = figi;
                    window.indices.set(figi, indexInfo);
                }
            }
        }

        // Если FIGI все еще нет, используем name как fallback
        if (!figi) {
            figi = config.name;
        }

        if (!figi) return;

        const indexInfo = window.indices.get(figi) || window.indices.get(config.name);
        if (!indexInfo) return;

        // Если цены уже загружены, пропускаем
        if (indexInfo.closePriceOS && indexInfo.closePriceEvening) {
            return;
        }

        // Загружаем цены из кэша
        fetch(`/api/price-cache/prices/${figi}`)
            .then(response => {
                if (!response.ok) {
                    // Если не нашли по FIGI, пытаемся найти по тикеру
                    if (response.status === 404 && config.name) {
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

                    const formatPrice = indicesBarConfig.formatPrice || ((price) => price.toFixed(2));

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
                }
            })
            .catch(error => {
                console.error(`Ошибка при загрузке цен для индекса ${figi}:`, error);
            });
    });
}

/**
 * Переключение модального окна управления индексами
 */
function toggleIndexManagement() {
    const modal = document.getElementById('indexManagementModal');
    if (modal.style.display === 'none') {
        modal.style.display = 'flex';
        loadCurrentIndices();
    } else {
        modal.style.display = 'none';
    }
}

/**
 * Загрузка текущих индексов для отображения в модальном окне
 */
function loadCurrentIndices() {
    if (!indicesBarConfig.apiEndpoint) {
        console.warn('API endpoint not configured');
        return;
    }

    fetch(`${indicesBarConfig.apiEndpoint}/indices`)
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

/**
 * Отображение текущих индексов в модальном окне
 */
function displayCurrentIndices(indicesList) {
    const container = document.getElementById('currentIndicesList');
    if (!container) return;

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

/**
 * Добавление нового индекса
 */
function addIndex() {
    if (!indicesBarConfig.apiEndpoint) {
        alert('API endpoint not configured');
        return;
    }

    const name = document.getElementById('newIndexTicker').value.trim();
    const displayName = document.getElementById('newIndexDisplayName').value.trim() || name;
    if (!name) {
        alert('Пожалуйста, заполните Ticker');
        return;
    }
    fetch(`${indicesBarConfig.apiEndpoint}/indices/add`, {
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
                    setTimeout(() => { loadIndexPrices(); }, 1000);
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

/**
 * Удаление индекса
 */
function removeIndex(name) {
    if (!indicesBarConfig.apiEndpoint) {
        alert('API endpoint not configured');
        return;
    }

    if (!confirm(`Вы уверены, что хотите удалить индекс "${name}"?`)) return;
    fetch(`${indicesBarConfig.apiEndpoint}/indices/remove`, {
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

