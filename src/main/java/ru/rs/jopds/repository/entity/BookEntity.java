package ru.rs.jopds.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * JPA Entity для хранения книги в БД
 */
@Entity
@Table(name = "books")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookEntity {
    
    @Id
    private Long id;
    
    @Column(nullable = false, length = 1000)
    private String title;
    
    /**
     * Список авторов
     */
    @Column(name = "authors_string", columnDefinition = "TEXT")
    private String authorsString;
    
    @Column(name = "series", length = 1000)
    private String series;
    
    @Column(name = "series_number")
    private Integer seriesNumber;
    
    /**
     * Список жанров
     */
    @Column(name = "genres_string", columnDefinition = "TEXT")
    private String genresString;
    
    @Column(length = 50)
    private String language;
    
    @Column(length = 50)
    private String format;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    private LocalDate date;
    
    @Column(name = "file_path", length = 1000)
    private String filePath;
    
    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    @Column(name = "archive_path", length = 255)
    private String archivePath;
}

