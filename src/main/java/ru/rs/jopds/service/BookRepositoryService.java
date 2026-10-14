package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.jopds.repository.entity.BookAuthorEntity;
import ru.rs.jopds.repository.entity.BookEntity;
import ru.rs.jopds.repository.entity.BookToAuthor;
import ru.rs.jopds.repository.entity.BookToGenre;

import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Запись каталога в БД на этапе импорта.
 *
 * Импорт идёт в staging-таблицы (*_staging), рабочие таблицы при этом
 * не затрагиваются — пользователи до конца импорта видят старый каталог.
 * В конце импорта таблицы меняются местами одной атомарной операцией
 * RENAME TABLE (см. {@link #swapStagingTables()}): либо каталог заменён целиком,
 * либо (при любом сбое) остался прежним — частично заполненного каталога не бывает.
 *
 * ВНИМАНИЕ: методы вставляют именно в staging-таблицы — это единственный
 * путь записи каталога.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BookRepositoryService {

    /** Каталог: staging-таблица и её рабочая пара */
    private static final String[][] CATALOG_TABLES = {
            {"books_staging", "books"},
            {"book_author_staging", "book_author"},
            {"book_to_author_staging", "book_to_author"},
            {"book_to_genre_staging", "book_to_genre"}
    };

    private final DataSource dataSource;

    /**
     * Пересоздаёт staging-таблицы по текущей схеме рабочих таблиц
     * (CREATE TABLE ... LIKE копирует структуру и индексы, включая FULLTEXT).
     * Остатки прошлого неудачного импорта отбрасываются.
     */
    @Transactional
    public void prepareStagingTables() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            for (String[] tables : CATALOG_TABLES) {
                stmt.execute("DROP TABLE IF EXISTS " + tables[0]);
                stmt.execute("CREATE TABLE " + tables[0] + " LIKE " + tables[1]);
            }
            log.info("Staging-таблицы подготовлены ({} шт.)", CATALOG_TABLES.length);
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось подготовить staging-таблицы: " + e.getMessage(), e);
        }
    }

    /**
     * Атомарно заменяет рабочие таблицы таблицами staging и удаляет старые.
     * RENAME TABLE с несколькими таблицами выполняется как одна операция:
     * между «старый каталог» и «новый каталог» нет промежуточного состояния.
     * До этого момента рабочие таблицы не были затронуты, поэтому при сбое
     * импорта (или этого RENAME) откат не нужен — просто остаётся старый каталог.
     */
    @Transactional
    public void swapStagingTables() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            // На случай остатков прошлого обмена
            dropOldTables(stmt);
            stmt.execute("RENAME TABLE " +
                    "books TO books_old, books_staging TO books, " +
                    "book_author TO book_author_old, book_author_staging TO book_author, " +
                    "book_to_author TO book_to_author_old, book_to_author_staging TO book_to_author, " +
                    "book_to_genre TO book_to_genre_old, book_to_genre_staging TO book_to_genre");
            dropOldTables(stmt);
            log.info("Рабочие таблицы заменены на staging (атомарный RENAME TABLE)");
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось заменить рабочие таблицы: " + e.getMessage(), e);
        }
    }

    private void dropOldTables(Statement stmt) throws SQLException {
        stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
        stmt.execute("DROP TABLE IF EXISTS books_old, book_author_old, book_to_author_old, book_to_genre_old");
        stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /**
     * Сохраняет список книг батчами в books_staging (нативный JDBC batch insert)
     */
    @Transactional
    public void saveInBatches(List<BookEntity> entities, int batchSize, String entityName) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int total = entities.size();
        log.info("Начинаем сохранение {} {} с использованием JDBC batch insert", total, entityName);

        // Простой INSERT: staging-таблица всегда пустая к началу вставки
        String sql = "INSERT INTO books_staging (id, title, authors_string, series, series_number, genres_string, " +
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
     * Сохраняет список авторов батчами в book_author_staging (нативный JDBC batch insert)
     */
    @Transactional
    public void saveAuthorsInBatches(List<BookAuthorEntity> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int total = entities.size();
        log.info("Начинаем сохранение {} авторов с использованием JDBC batch insert", total);

        String sql = "INSERT INTO book_author_staging (id, last_name, first_name, middle_name, full_name) " +
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
     * Сохраняет список связей книг и авторов батчами в book_to_author_staging (нативный JDBC batch insert)
     */
    @Transactional
    public void saveBookToAuthorInBatches(List<BookToAuthor> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int total = entities.size();
        log.info("Начинаем сохранение {} связей с использованием JDBC batch insert", total);

        String sql = "INSERT INTO book_to_author_staging (book_id, author_id) VALUES (?, ?)";

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
     * Сохраняет список связей книг и жанров батчами в book_to_genre_staging (нативный JDBC batch insert)
     */
    @Transactional
    public void saveBookToGenreInBatches(List<BookToGenre> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int total = entities.size();
        log.info("Начинаем сохранение {} связей с использованием JDBC batch insert", total);

        String sql = "INSERT INTO book_to_genre_staging (book_id, genre_id) VALUES (?, ?)";

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
