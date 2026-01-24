package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rs.jopds.repository.BookAuthorEntityRepository;
import ru.rs.jopds.repository.BookRepository;
import ru.rs.jopds.repository.BookToAuthorRepository;
import ru.rs.jopds.repository.BookToGenreRepository;
import ru.rs.jopds.repository.entity.BookAuthorEntity;
import ru.rs.jopds.repository.entity.BookEntity;
import ru.rs.jopds.repository.entity.BookToAuthor;
import ru.rs.jopds.repository.entity.BookToGenre;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class BookRepositoryService {
    private final BookRepository bookRepository;
    private final BookAuthorEntityRepository authorRepository;
    private final BookToAuthorRepository bookToAuthorRepository;
    private final BookToGenreRepository bookToGenreRepository;
    private final DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional()
    public void clearAll() {
        // Используем TRUNCATE для быстрой очистки (быстрее чем DELETE)
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            connection.setAutoCommit(false);
            // Отключаем проверку внешних ключей для быстрой очистки
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            stmt.execute("TRUNCATE TABLE book_to_author");
            stmt.execute("TRUNCATE TABLE book_author");
            stmt.execute("TRUNCATE TABLE books");
            stmt.execute("TRUNCATE TABLE book_to_genre");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            connection.commit();
            connection.setAutoCommit(true);
            log.info("Таблицы очищены с использованием TRUNCATE");
        } catch (SQLException e) {
            log.warn("Не удалось использовать TRUNCATE, используем DELETE: {}", e.getMessage());
            // Fallback на обычное удаление
            bookToAuthorRepository.deleteAll();
            authorRepository.deleteAll();
            bookRepository.deleteAll();
            bookToGenreRepository.deleteAll();
            entityManager.flush();
        }
    }


    /**
     * Сохраняет список книг батчами с оптимизацией памяти (использует нативный JDBC batch insert)
     */
    @Transactional
    public void saveInBatches(List<BookEntity> entities, int batchSize, String entityName) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int total = entities.size();
        log.info("Начинаем сохранение {} {} с использованием JDBC batch insert", total, entityName);

        // Используем простой INSERT без ON DUPLICATE KEY для максимальной скорости
        // (так как мы очистили таблицу перед вставкой)
        String sql = "INSERT INTO books (id, title, authors_string, series, series_number, genres_string, " +
                "language, format, file_size, date, file_path, additional_info, archive_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            connection.setAutoCommit(false);
            int saved = 0;

            for (int i = 0; i < total; i++) {
                BookEntity entity = entities.get(i);

                ps.setLong(1, entity.getId());
                ps.setString(2, entity.getTitle());
                ps.setString(3, entity.getAuthorsString());
                ps.setString(4, entity.getSeries());
                ps.setObject(5, entity.getSeriesNumber());
                ps.setString(6, entity.getGenresString());
                ps.setString(7, entity.getLanguage());
                ps.setString(8, entity.getFormat());
                ps.setObject(9, entity.getFileSize());
                ps.setObject(10, entity.getDate());
                ps.setString(11, entity.getFilePath());
                ps.setString(12, entity.getAdditionalInfo());
                ps.setString(13, entity.getArchivePath());

                ps.addBatch();

                // Выполняем batch каждые batchSize записей
                if ((i + 1) % batchSize == 0 || (i + 1) == total) {
                    int[] results = ps.executeBatch();
                    connection.commit();
                    saved += results.length;
                    log.debug("Сохранено {}/{} {}", saved, total, entityName);
                }
            }

            connection.setAutoCommit(true);
            log.info("Сохранено {} {} с использованием JDBC batch insert", total, entityName);
        } catch (SQLException e) {
            log.error("Ошибка при batch insert книг", e);
            throw new RuntimeException("Ошибка при сохранении книг", e);
        }
    }

    /**
     * Сохраняет список авторов батчами с оптимизацией памяти (использует нативный JDBC batch insert)
     */
    @Transactional
    public void saveAuthorsInBatches(List<BookAuthorEntity> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int total = entities.size();
        log.info("Начинаем сохранение {} авторов с использованием JDBC batch insert", total);

        // Используем простой INSERT без ON DUPLICATE KEY для максимальной скорости
        String sql = "INSERT INTO book_author (id, last_name, first_name, middle_name, full_name) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            connection.setAutoCommit(false);
            int saved = 0;

            for (int i = 0; i < total; i++) {
                BookAuthorEntity entity = entities.get(i);

                ps.setLong(1, entity.getId());
                ps.setString(2, entity.getLastName());
                ps.setString(3, entity.getFirstName());
                ps.setString(4, entity.getMiddleName());
                ps.setString(5, entity.getFullName());

                ps.addBatch();

                // Выполняем batch каждые batchSize записей
                if ((i + 1) % batchSize == 0 || (i + 1) == total) {
                    int[] results = ps.executeBatch();
                    connection.commit();
                    saved += results.length;
                    log.debug("Сохранено {}/{} авторов", saved, total);
                }
            }

            connection.setAutoCommit(true);
            log.info("Сохранено {} авторов с использованием JDBC batch insert", total);
        } catch (SQLException e) {
            log.error("Ошибка при batch insert авторов", e);
            throw new RuntimeException("Ошибка при сохранении авторов", e);
        }
    }

    /**
     * Сохраняет список связей книг и авторов батчами с оптимизацией памяти (использует нативный JDBC batch insert)
     */
    @Transactional
    public void saveBookToAuthorInBatches(List<BookToAuthor> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int total = entities.size();
        log.info("Начинаем сохранение {} связей с использованием JDBC batch insert", total);

        String sql = "INSERT INTO book_to_author (book_id, author_id) VALUES (?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            connection.setAutoCommit(false);
            int saved = 0;

            for (int i = 0; i < total; i++) {
                BookToAuthor entity = entities.get(i);

                ps.setLong(1, entity.getBookId());
                ps.setLong(2, entity.getAuthorId());

                ps.addBatch();

                // Выполняем batch каждые batchSize записей
                if ((i + 1) % batchSize == 0 || (i + 1) == total) {
                    int[] results = ps.executeBatch();
                    connection.commit();
                    saved += results.length;
                    log.debug("Сохранено {}/{} связей", saved, total);
                }
            }

            connection.setAutoCommit(true);
            log.info("Сохранено {} связей с использованием JDBC batch insert", total);
        } catch (SQLException e) {
            log.error("Ошибка при batch insert связей", e);
            throw new RuntimeException("Ошибка при сохранении связей", e);
        }
    }

    /**
     * Сохраняет список связей книг и жанров батчами с оптимизацией памяти (использует нативный JDBC batch insert)
     */
    @Transactional
    public void saveBookToGenreInBatches(List<BookToGenre> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return;
        }


        int total = entities.size();
        log.info("Начинаем сохранение {} связей с использованием JDBC batch insert", total);

        String sql = "INSERT INTO book_to_genre (book_id, genre_id) VALUES (?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            connection.setAutoCommit(false);
            int saved = 0;

            for (int i = 0; i < total; i++) {
                BookToGenre entity = entities.get(i);

                ps.setLong(1, entity.getBookId());
                ps.setString(2, entity.getGenreId());

                ps.addBatch();

                // Выполняем batch каждые batchSize записей
                if ((i + 1) % batchSize == 0 || (i + 1) == total) {
                    int[] results = ps.executeBatch();
                    connection.commit();
                    saved += results.length;
                    log.debug("Сохранено {}/{} связей", saved, total);
                }
            }

            connection.setAutoCommit(true);
            log.info("Сохранено {} связей с использованием JDBC batch insert", total);
        } catch (SQLException e) {
            log.error("Ошибка при batch insert связей", e);
            throw new RuntimeException("Ошибка при сохранении связей", e);
        }
    }

}
