package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rs.jopds.repository.entity.ScanResult;

import java.util.Optional;

/**
 * Repository для работы с результатами сканирования
 */
@Repository
public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {
    
    /**
     * Получает последний результат сканирования
     */
    Optional<ScanResult> findFirstByOrderByScanDateDesc();
}
