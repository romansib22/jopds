package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rs.jopds.mapper.BookMapper;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.BooksToSave;
import ru.rs.jopds.model.ImportContext;
import ru.rs.jopds.repository.BookRepository;
import ru.rs.jopds.repository.FavoriteAuthorRepository;
import ru.rs.jopds.repository.ScanResultRepository;
import ru.rs.jopds.repository.entity.BookAuthorEntity;
import ru.rs.jopds.repository.entity.BookEntity;
import ru.rs.jopds.repository.entity.ScanResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с книгами в БД
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookRepositoryService bookRepositoryService;
    private final BookMapper bookMapper;
    private final DataSource dataSource;
    private final ScanResultRepository scanResultRepository;
    private final FavoriteAuthorRepository favoriteAuthorRepository;


    /**
     * Начинает импорт каталога: очищает старые данные и готовит контекст
     * (карта жанров и избранных авторов загружаются один раз на весь импорт).
     * Возвращает контекст, который нужно передавать в каждый saveBookBatch.
     */
    public ImportContext startImport() {
        long startTime = System.currentTimeMillis();
        bookRepositoryService.clearAll();
        log.info("Старые данные очищены за {} мс", System.currentTimeMillis() - startTime);
        return bookMapper.createImportContext();
    }

    /**
     * Сохраняет один батч книг в рамках импорта (стриминговый импорт:
     * весь каталог никогда не находится в памяти целиком).
     * Каждый батч — самостоятельный набор JDBC-вставок.
     */
    public void saveBookBatch(List<Book> batch, ImportContext context) {
        BooksToSave entities = bookMapper.toEntity(batch, context);

        bookRepositoryService.saveInBatches(entities.getBookEntities(), 5000, "книг");

        List<BookAuthorEntity> authorsList = new ArrayList<>(entities.getBookAuthors().values());
        if (!authorsList.isEmpty()) {
            bookRepositoryService.saveAuthorsInBatches(authorsList, 5000);
        }

        if (!entities.getBookToAuthors().isEmpty()) {
            bookRepositoryService.saveBookToAuthorInBatches(entities.getBookToAuthors(), 5000);
        }

        if (!entities.getBookToGenres().isEmpty()) {
            bookRepositoryService.saveBookToGenreInBatches(entities.getBookToGenres(), 5000);
        }

        // Переназначенные ID избранных авторов сохраняем по мере появления в батчах
        if (!entities.getFavoriteAuthors().isEmpty()) {
            favoriteAuthorRepository.saveAll(entities.getFavoriteAuthors());
        }
    }

    /**
     * Завершает импорт: создаёт недостающие индексы и фиксирует результат сканирования
     */
    @Transactional
    public void finishImport(long bookCount, String filePath) {
        createIndexesIfNotExist();
        saveScanResult(bookCount, filePath);
    }

    /**
     * Сохраняет результат сканирования в БД
     */
    @Transactional
    public void saveScanResult(long bookCount, String filePath) {
        ScanResult scanResult = ScanResult.builder()
                .bookCount(bookCount)
                .scanDate(LocalDateTime.now())
                .filePath(filePath)
                .build();

        scanResultRepository.save(scanResult);
        log.info("Результат сканирования сохранен: {} книг, дата: {}", bookCount, scanResult.getScanDate());
    }

    /**
     * Получает последний результат сканирования
     */
    public Optional<ScanResult> getLatestScanResult() {
        return scanResultRepository.findFirstByOrderByScanDateDesc();
    }

    /**
     * Создает индексы по полям title, authors_json, series если они не существуют
     */
    @Transactional
    public void createIndexesIfNotExist() {
        try (Connection connection = dataSource.getConnection()) {
            createIndexes(connection);
            log.info("Проверка и создание индексов завершена");
        } catch (Exception e) {
            log.warn("Ошибка при создании индексов (может быть они уже существуют): {}", e.getMessage());
        }
    }

    private void createIndexes(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            // Проверяем существование индексов через INFORMATION_SCHEMA
            DatabaseMetaData metaData = connection.getMetaData();

            // Индекс по title
            if (!indexExists(metaData, "books", "idx_title")) {
                statement.execute("CREATE INDEX idx_title ON books(title)");
                log.info("Индекс idx_title создан");
            }

            // Индекс по series
            if (!indexExists(metaData, "books", "idx_series")) {
                statement.execute("CREATE INDEX idx_series ON books(series)");
                log.info("Индекс idx_series создан");
            }

            // Индекс по authors_json (для TEXT нужен префикс или FULLTEXT)
            if (!indexExists(metaData, "books", "idx_authors_json")) {
                try {
                    // Пробуем создать FULLTEXT индекс для MySQL
                    statement.execute("CREATE FULLTEXT INDEX idx_authors_json ON books(authors_json)");
                    log.info("FULLTEXT индекс idx_authors_json создан");
                } catch (Exception e) {
                    // Если не поддерживается, создаем обычный индекс с префиксом
                    log.debug("Не удалось создать FULLTEXT индекс, пробуем обычный: {}", e.getMessage());
                }
            }

            // Индекс по author_last_names
            if (!indexExists(metaData, "books", "idx_author_last_names")) {
                try {
                    statement.execute("CREATE FULLTEXT INDEX idx_author_last_names ON books(author_last_names)");
                    log.info("FULLTEXT индекс idx_author_last_names создан");
                } catch (Exception e) {
                    log.debug("Не удалось создать FULLTEXT индекс: {}", e.getMessage());
                }
            }

            // Индекс по author_first_names
            if (!indexExists(metaData, "books", "idx_author_first_names")) {
                try {
                    statement.execute("CREATE FULLTEXT INDEX idx_author_first_names ON books(author_first_names)");
                    log.info("FULLTEXT индекс idx_author_first_names создан");
                } catch (Exception e) {
                    log.debug("Не удалось создать FULLTEXT индекс: {}", e.getMessage());
                }
            }

            // Индекс по language
            if (!indexExists(metaData, "books", "idx_language")) {
                statement.execute("CREATE INDEX idx_language ON books(language)");
                log.info("Индекс idx_language создан");
            }

            // Индекс по format
            if (!indexExists(metaData, "books", "idx_format")) {
                statement.execute("CREATE INDEX idx_format ON books(format)");
                log.info("Индекс idx_format создан");
            }

            // Индексы для таблицы book_author
            if (!indexExists(metaData, "book_author", "idx_last_name")) {
                statement.execute("CREATE INDEX idx_last_name ON book_author(last_name)");
                log.info("Индекс idx_last_name создан");
            }

            if (!indexExists(metaData, "book_author", "idx_first_name")) {
                statement.execute("CREATE INDEX idx_first_name ON book_author(first_name)");
                log.info("Индекс idx_first_name создан");
            }

            if (!indexExists(metaData, "book_author", "idx_middle_name")) {
                statement.execute("CREATE INDEX idx_middle_name ON book_author(middle_name)");
                log.info("Индекс idx_middle_name создан");
            }

            // Индексы для таблицы book_to_author
            if (!indexExists(metaData, "book_to_author", "idx_book_id")) {
                statement.execute("CREATE INDEX idx_book_id ON book_to_author(book_id)");
                log.info("Индекс idx_book_id создан");
            }

            if (!indexExists(metaData, "book_to_author", "idx_author_id")) {
                statement.execute("CREATE INDEX idx_author_id ON book_to_author(author_id)");
                log.info("Индекс idx_author_id создан");
            }
        }
    }

    private boolean indexExists(DatabaseMetaData metaData, String tableName, String indexName) {
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                String existingIndexName = indexes.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(existingIndexName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Ошибка при проверке существования индекса {}: {}", indexName, e.getMessage());
        }
        return false;
    }


    /**
     * Получает все книги из БД
     */
    public List<Book> getAllBooks() {
        List<BookEntity> entities = bookRepository.findAll();
        return bookMapper.toBookList(entities);
    }

    /**
     * Поиск книг по названию
     */
    public List<Book> searchByTitle(String title) {
        List<BookEntity> entities = bookRepository.findByTitleContainingIgnoreCase(title);
        return bookMapper.toBookList(entities);
    }

    /**
     * Поиск книг по серии
     */
    public List<Book> getBooksBySeries(String series) {
        List<BookEntity> entities = bookRepository.findBySeries(series);
        return bookMapper.toBookList(entities);
    }

    /**
     * Поиск книг по языку
     */
    public List<Book> getBooksByLanguage(String language) {
        List<BookEntity> entities = bookRepository.findByLanguage(language);

        return bookMapper.toBookList(entities);
    }

    /**
     * Поиск книг по формату
     */
    public List<Book> getBooksByFormat(String format) {
        List<BookEntity> entities = bookRepository.findByFormat(format);
        return bookMapper.toBookList(entities);
    }


    /**
     * Возвращает количество книг в БД (из последнего результата сканирования)
     */
    public long getBookCount() {
        return getLatestScanResult()
                .map(ScanResult::getBookCount)
                .orElse(0L);
    }

    /**
     * Возвращает количество книг в БД напрямую из таблицы books
     */
    public long getActualBookCount() {
        return bookRepository.count();
    }
}

