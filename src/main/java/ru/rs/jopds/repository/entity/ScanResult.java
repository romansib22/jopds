package ru.rs.jopds.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Сущность для хранения результатов сканирования INPX файла
 */
@Entity
@Table(name = "scan_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "book_count", nullable = false)
    private Long bookCount;
    
    @Column(name = "scan_date", nullable = false)
    private LocalDateTime scanDate;
    
    @Column(name = "file_path", length = 1000)
    private String filePath;
}
