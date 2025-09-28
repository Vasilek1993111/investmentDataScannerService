package com.example.investmentdatascannerservice.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.entity.ShareEntity;
import com.example.investmentdatascannerservice.repository.ShareRepository;

/**
 * Сервис для работы с акциями из таблицы invest.shares
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private final ShareRepository shareRepository;

    public ShareService(ShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    /**
     * Получить все акции
     */
    public List<ShareEntity> getAllShares() {
        try {
            List<ShareEntity> shares = shareRepository.findAllShares();
            log.info("Loaded {} shares from database", shares.size());
            if (!shares.isEmpty()) {
                log.info("First 3 shares: {}",
                        shares.subList(0, Math.min(3, shares.size())).stream()
                                .map(s -> String.format("FIGI=%s, ticker=%s, name=%s, exchange=%s",
                                        s.getFigi(), s.getTicker(), s.getName(), s.getExchange()))
                                .collect(Collectors.toList()));
            }
            return shares;
        } catch (Exception e) {
            log.error("Error loading shares from database", e);
            return List.of();
        }
    }

    /**
     * Получить список FIGI всех акций
     */
    public List<String> getAllShareFigis() {
        List<ShareEntity> shares = getAllShares();
        List<String> figis = shares.stream().map(ShareEntity::getFigi).collect(Collectors.toList());
        log.info("Returning {} FIGIs for scanning", figis.size());
        return figis;
    }

    /**
     * Получить карту имен инструментов (FIGI -> Название)
     */
    public Map<String, String> getShareNames() {
        return getAllShares().stream()
                .collect(Collectors.toMap(ShareEntity::getFigi,
                        share -> share.getName() != null ? share.getName() : share.getTicker(),
                        (existing, replacement) -> existing));
    }

    /**
     * Получить карту тикеров (FIGI -> Тикер)
     */
    public Map<String, String> getShareTickers() {
        List<ShareEntity> shares = getAllShares();
        Map<String, String> tickers = shares.stream().collect(Collectors.toMap(ShareEntity::getFigi,
                ShareEntity::getTicker, (existing, replacement) -> existing));
        log.info("Loaded {} tickers from database", tickers.size());
        if (!tickers.isEmpty()) {
            log.info("First 5 tickers: {}",
                    tickers.entrySet().stream().limit(5)
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.toList()));
        }
        return tickers;
    }

    /**
     * Получить количество всех акций
     */
    public long getAllSharesCount() {
        try {
            return shareRepository.count();
        } catch (Exception e) {
            log.error("Error counting shares", e);
            return 0;
        }
    }

    /**
     * Найти акции по тикеру
     */
    public List<ShareEntity> findByTicker(String ticker) {
        try {
            List<ShareEntity> shares = shareRepository.findByTicker(ticker);
            log.info("Found {} shares with ticker: {}", shares.size(), ticker);
            return shares;
        } catch (Exception e) {
            log.error("Error finding shares by ticker: {}", ticker, e);
            return List.of();
        }
    }
}
