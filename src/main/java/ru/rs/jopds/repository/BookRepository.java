package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rs.jopds.repository.entity.BookEntity;

import java.util.List;

/**
 * Repository для работы с книгами в БД
 */
@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long> {

    /**
     * Поиск книг по названию (нечеткий поиск)
     */
    List<BookEntity> findByTitleContainingIgnoreCase(String title);

    /**
     * Поиск книг по названию (четкий поиск)
     */
    List<BookEntity> findByTitleIgnoreCase(String title);

    /**
     * Поиск книг по серии
     */
    List<BookEntity> findBySeries(String series);

    /**
     * Поиск книг по языку
     */
    List<BookEntity> findByLanguage(String language);

    /**
     * Поиск книг по формату
     */
    List<BookEntity> findByFormat(String format);


    /**
     * Поиск книг по части названия или автора
     */
    @Query(value = "SELECT * FROM books WHERE " +
            "LOWER(title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "authors_json LIKE CONCAT('%', :query, '%')",
            nativeQuery = true)
    List<BookEntity> searchByTitleOrAuthor(@Param("query") String query);

    /**
     * Нечеткий поиск книг по названию через FULLTEXT-индекс (без полного скана таблицы).
     * Запрос в boolean-режиме: '+слово1* +слово2*' (все слова, префиксное совпадение).
     */
    @Query(value = "SELECT * FROM books WHERE MATCH(title) AGAINST (:query IN BOOLEAN MODE)",
            nativeQuery = true)
    List<BookEntity> searchByTitleFulltext(@Param("query") String query);

    /**
     * Поиск книг по серии (нечеткий поиск)
     */
    List<BookEntity> findBySeriesContainingIgnoreCase(String series);

    /**
     * Поиск книг по серии (четкий поиск)
     */
    List<BookEntity> findBySeriesIgnoreCase(String series);

    /**
     * Удаление всех книг из каталога
     */
    void deleteAll();

    /**
     * Подсчет количества книг
     */
    long count();

    String SQL_FIND_BOOKS_BY_AUTHOR = "SELECT b.*\n" +
            "  FROM book_to_author bta\n" +
            "  JOIN books b ON b.id = bta.book_id\n" +
            "  WHERE bta.author_id = ?1";

    @Query(value = SQL_FIND_BOOKS_BY_AUTHOR, nativeQuery = true)
    List<BookEntity> findBooksByAuthorId(long authorId);

    String SQL_FIND_BOOKS_BY_AUTHOR_AND_SERIES = "SELECT DISTINCT b.*\n" +
            "  FROM book_to_author bta\n" +
            "  JOIN books b ON b.id = bta.book_id\n" +
            "  WHERE bta.author_id = ?1\n" +
            "    AND b.series = ?2";

    @Query(value = SQL_FIND_BOOKS_BY_AUTHOR_AND_SERIES, nativeQuery = true)
    List<BookEntity> findBooksByAuthorIdAndSeries(long authorId, String series);

    String SQL_FIND_BOOKS_BY_FAVORITE_AUTHORS_AND_DATE = "SELECT DISTINCT b.*\n" +
            "  FROM book_to_author bta\n" +
            "  JOIN books b ON b.id = bta.book_id\n" +
            "  JOIN favorite_author fa ON fa.book_author_id = bta.author_id\n" +
            "  WHERE fa.username = ?1\n" +
            "    AND b.date >= ?2\n" +
            "    AND b.date IS NOT NULL\n" +
            "  ORDER BY b.date DESC, b.title ASC";

    @Query(value = SQL_FIND_BOOKS_BY_FAVORITE_AUTHORS_AND_DATE, nativeQuery = true)
    List<BookEntity> findBooksByFavoriteAuthorsAndDate(String username, java.time.LocalDate minDate);
}

