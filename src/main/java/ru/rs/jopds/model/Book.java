package ru.rs.jopds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Модель книги для каталога
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    /**
     * ID книги в БД
     */
    private Long id;

    /**
     * Название книги
     */
    private String title;

    /**
     * Список авторов
     */
    private String authorsString;
    private List<BookAuthor> authors;

    /**
     * Название серии
     */
    private String series;

    /**
     * Номер в серии
     */
    private Integer seriesNumber;

    /**
     * Список жанров
     */
    private List<String> genres;

    /**
     * Язык книги
     */
    private String language;

    /**
     * Формат файла (fb2, epub, pdf и т.д.)
     */
    private String format;

    /**
     * Размер файла в байтах
     */
    private Long fileSize;

    /**
     * Дата добавления
     */
    private LocalDate date;

    /**
     * Путь к файлу внутри архива
     */
    private String filePath;

    /**
     * Дополнительная информация
     */
    private String additionalInfo;

    /**
     * Имя архива с книгой
     */
    private String archivePath;
}

