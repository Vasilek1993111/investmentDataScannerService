package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.entity.SharesAggregatedDataEntity;
import com.example.investmentdatascannerservice.repository.SharesAggregatedDataRepository;

@Service
public class SharesAggregatedDataService {

    private static final Logger logger = LoggerFactory.getLogger(SharesAggregatedDataService.class);

    @Autowired
    private SharesAggregatedDataRepository sharesAggregatedDataRepository;

    /**
     * Получить средний утренний объем для FIGI
     */
    public BigDecimal getAvgVolumeMorning(String figi) {
        try {
            Optional<SharesAggregatedDataEntity> data =
                    sharesAggregatedDataRepository.findByFigi(figi);
            if (data.isPresent() && data.get().getAvgVolumeMorning() != null) {
                return data.get().getAvgVolumeMorning();
            }
        } catch (Exception e) {
            logger.warn("Ошибка при получении среднего утреннего объема для FIGI {}: {}", figi,
                    e.getMessage());
        }
        return null;
    }

    /**
     * Получить средние утренние объемы для списка FIGI
     */
    public Map<String, BigDecimal> getAvgVolumeMorningMap(List<String> figis) {
        Map<String, BigDecimal> volumeMap = new HashMap<>();
        try {
            List<SharesAggregatedDataEntity> dataList =
                    sharesAggregatedDataRepository.findByFigiIn(figis);
            for (SharesAggregatedDataEntity data : dataList) {
                if (data.getAvgVolumeMorning() != null) {
                    volumeMap.put(data.getFigi(), data.getAvgVolumeMorning());
                }
            }
            logger.info("Загружено {} записей с данными по утренним объемам из {} запрошенных",
                    volumeMap.size(), figis.size());
        } catch (Exception e) {
            logger.error("Ошибка при загрузке средних утренних объемов: {}", e.getMessage(), e);
        }
        return volumeMap;
    }

    /**
     * Получить средние объемы выходного дня для списка FIGI
     */
    public Map<String, BigDecimal> getAvgVolumeWeekendMap(List<String> figis) {
        Map<String, BigDecimal> volumeMap = new HashMap<>();
        try {
            List<SharesAggregatedDataEntity> dataList =
                    sharesAggregatedDataRepository.findByFigiIn(figis);
            for (SharesAggregatedDataEntity data : dataList) {
                if (data.getAvgVolumeWeekend() != null) {
                    volumeMap.put(data.getFigi(), data.getAvgVolumeWeekend());
                }
            }
            logger.info("Загружено {} записей с данными по объемам выходного дня из {} запрошенных",
                    volumeMap.size(), figis.size());
        } catch (Exception e) {
            logger.error("Ошибка при загрузке средних объемов выходного дня: {}", e.getMessage(),
                    e);
        }
        return volumeMap;
    }

    /**
     * Получить все данные с утренними объемами
     */
    public List<SharesAggregatedDataEntity> getAllWithMorningVolume() {
        try {
            return sharesAggregatedDataRepository.findAllWithMorningVolume();
        } catch (Exception e) {
            logger.error("Ошибка при загрузке всех данных с утренними объемами: {}", e.getMessage(),
                    e);
            return List.of();
        }
    }

    /**
     * Подсчитать количество записей с данными по утреннему объему
     */
    public long getCountWithMorningVolume() {
        try {
            return sharesAggregatedDataRepository.countWithMorningVolume();
        } catch (Exception e) {
            logger.error("Ошибка при подсчете записей с утренними объемами: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Сохранить или обновить данные
     */
    public SharesAggregatedDataEntity saveOrUpdate(SharesAggregatedDataEntity entity) {
        try {
            return sharesAggregatedDataRepository.save(entity);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении агрегированных данных для FIGI {}: {}",
                    entity.getFigi(), e.getMessage(), e);
            return null;
        }
    }
}
