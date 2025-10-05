package com.example.investmentdatascannerservice.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.DividendEntity;

@Repository
public interface DividendRepository extends JpaRepository<DividendEntity, Long> {

    @Query("SELECT d.figi FROM DividendEntity d WHERE d.declaredDate >= :fromDate")
    List<String> findFigiWithDeclaredSince(@Param("fromDate") LocalDate fromDate);
}


