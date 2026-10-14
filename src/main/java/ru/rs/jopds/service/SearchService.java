package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.jopds.mapper.BookMapper;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.BookAuthor;
import ru.rs.jopds.repository.BookAuthorEntityRepository;
import ru.rs.jopds.repository.BookRepository;
import ru.rs.jopds.repository.entity.BookAuthorEntity;
import ru.rs.jopds.repository.entity.BookEntity;
import ru.rs.jopds.repository.entity.UserSettings;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для поиска книг по различным критериям
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final BookRepository bookRepository;
    private final BookAuthorEntityRepository authorRepository;
    private final BookMapper bookMapper;
    private final UserSettingsService userSettingsService;

    /**
     * Поиск авторов по запросу (возвращает список авторов, а не книг)
     */
    public List<BookAuthor> searchByAuthor(String authorQuery, boolean fuzzySearch) {
        if (authorQuery == null || authorQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String query = authorQuery.trim();
        String lowerQuery = query.toLowerCase();
        String[] queryWords;
        
        // Если строка содержит запятую, пытаемся разделить по ", " (запятая + пробел)
        if (lowerQuery.contains(",")) {
            queryWords = lowerQuery.split(", ");
            log.debug("Разделение по ', ': получено {} элементов", queryWords.length);
            // Если split не разделил строку (массив из одного элемента), пробуем разделить просто по запятой
            if (queryWords.length == 1 && queryWords[0].equals(lowerQuery)) {
                log.debug("Split по ', ' не разделил строку, пробуем разделить по ','");
                queryWords = lowerQuery.split(",");
                // Убираем пробелы в начале и конце каждого элемента
                for (int i = 0; i < queryWords.length; i++) {
                    queryWords[i] = queryWords[i].trim();
                }
                log.debug("Разделение по ',': получено {} элементов", queryWords.length);
            }
        } else {
            queryWords = lowerQuery.split("\\s+");
            log.debug("Разделение по пробелам: получено {} элементов", queryWords.length);
        }
        
        log.debug("Итоговый массив queryWords: длина={}, элементы={}", queryWords.length, java.util.Arrays.toString(queryWords));

        List<Object[]> matchingResults = new ArrayList<>();

        if (queryWords.length == 1) {
            //Поиск по фамилии
            if (fuzzySearch) {
                // Нестрогий поиск
                matchingResults = authorRepository.findByFFuzzy(queryWords[0]);
            } else {
                matchingResults = authorRepository.findByF(queryWords[0]);
            }
        }

        if (queryWords.length == 2) {
            // поиск по имени и фамилии
            if (fuzzySearch) {
                // Нестрогий поиск
                matchingResults = authorRepository.findByFIFuzzy(queryWords[0], queryWords[1]);
            } else {
                matchingResults = authorRepository.findByFI(queryWords[0], queryWords[1]);
            }
        }

        if (queryWords.length == 3) {
            if (fuzzySearch) {
                // Нестрогий поиск
                matchingResults = authorRepository.findByFIOFuzzy(queryWords[0], queryWords[1], queryWords[2]);
            } else {
                matchingResults = authorRepository.findByFIO(queryWords[0], queryWords[1], queryWords[2]);
            }
        }

        // Преобразуем результаты Object[] в BookAuthor
        // Порядок колонок: id, firstName, lastName, middleName, fullName, bookCount
        List<BookAuthor> matchingAuthors = matchingResults.stream()
                .map(row -> {
                    Long id = row[0] != null ? ((Number) row[0]).longValue() : null;
                    String firstName = row[1] != null ? (String) row[1] : null;
                    String lastName = row[2] != null ? (String) row[2] : null;
                    String middleName = row[3] != null ? (String) row[3] : null;
                    String fullName = row[4] != null ? (String) row[4] : null;
                    Long bookCount = row[5] != null ? ((Number) row[5]).longValue() : null;

                    return BookAuthor.builder()
                            .authorId(id)
                            .firstName(firstName)
                            .lastName(lastName)
                            .middleName(middleName)
                            .fullName(fullName)
                            .bookCount(bookCount != null ? bookCount.intValue() : null)
                            .build();
                })
                .collect(Collectors.toList());

        log.debug("Поиск по автору '{}' (нестрогий: {}): найдено {} авторов", query, fuzzySearch, matchingAuthors.size());
        return matchingAuthors;
    }

    /**
     * Получает ID автора по полному имени
     */
    public Long getAuthorIdByFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return null;
        }

        return authorRepository.findByFullName(fullName.trim())
                .map(BookAuthorEntity::getId)
                .orElse(null);
    }

    /**
     * Получает автора по полному имени
     */
    public BookAuthor getAuthorByFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return null;
        }

        return authorRepository.findByFullName(fullName.trim())
                .map(entity -> BookAuthor.builder()
                        .authorId(entity.getId())
                        .firstName(entity.getFirstName())
                        .lastName(entity.getLastName())
                        .middleName(entity.getMiddleName())
                        .fullName(entity.getFullName())
                        .build())
                .orElse(null);
    }

    /**
     * Получает ID авторов по списку полных имён одним запросом (анти-N+1)
     */
    public Map<String, Long> getAuthorIdMapByFullNames(Collection<String> fullNames) {
        if (fullNames == null || fullNames.isEmpty()) {
            return new HashMap<>();
        }

        return authorRepository.findByFullNameIn(fullNames).stream()
                .collect(Collectors.toMap(BookAuthorEntity::getFullName, BookAuthorEntity::getId, (a, b) -> a));
    }

    /**
     * Получает авторов по списку полных имён одним запросом (анти-N+1)
     */
    public Map<String, BookAuthor> getAuthorsByFullNames(Collection<String> fullNames) {
        if (fullNames == null || fullNames.isEmpty()) {
            return new HashMap<>();
        }

        return authorRepository.findByFullNameIn(fullNames).stream()
                .collect(Collectors.toMap(BookAuthorEntity::getFullName,
                        entity -> BookAuthor.builder()
                                .authorId(entity.getId())
                                .firstName(entity.getFirstName())
                                .lastName(entity.getLastName())
                                .middleName(entity.getMiddleName())
                                .fullName(entity.getFullName())
                                .build(),
                        (a, b) -> a));
    }

    /**
     * Получает все книги указанного автора
     */
    public List<Book> getBooksByAuthorId(Long authorId, String userLogin) {
        if (authorId == null) {
            return new ArrayList<>();
        }

        // Получаем все книги автора
        List<BookEntity> allBooks = bookRepository.findBooksByAuthorId(authorId);

        // Настройки языков получаем один раз, а не на каждую книгу (анти-N+1)
        UserSettings langSettings = userLogin != null
                ? userSettingsService.getSettings(userLogin).orElse(null)
                : null;

        // Фильтруем книги по языку
        List<BookEntity> matchingBooks = allBooks.stream()
                .filter(book -> userSettingsService.shouldSearchLanguage(langSettings, book.getLanguage()))
                .collect(Collectors.toList());

        log.debug("Найдено {} книг для автора с ID {}", matchingBooks.size(), authorId);
        return bookMapper.toBookList(matchingBooks);
    }

    /**
     * Получает все книги указанного автора и серии
     */
    public List<Book> getBooksByAuthorIdAndSeries(Long authorId, String series, String userLogin) {
        if (authorId == null || series == null || series.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Получаем все книги автора и серии
        List<BookEntity> allBooks = bookRepository.findBooksByAuthorIdAndSeries(authorId, series.trim());

        // Настройки языков получаем один раз, а не на каждую книгу (анти-N+1)
        UserSettings langSettings = userLogin != null
                ? userSettingsService.getSettings(userLogin).orElse(null)
                : null;

        // Фильтруем книги по языку
        List<BookEntity> matchingBooks = allBooks.stream()
                .filter(book -> userSettingsService.shouldSearchLanguage(langSettings, book.getLanguage()))
                .collect(Collectors.toList());

        log.debug("Найдено {} книг для автора с ID {} и серии '{}'", matchingBooks.size(), authorId, series);
        return bookMapper.toBookList(matchingBooks);
    }


    /**
     * Поиск книг по названию
     */
    public List<Book> searchByTitle(String titleQuery, String userLogin, boolean fuzzySearch) {
        if (titleQuery == null || titleQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String query = titleQuery.trim().toLowerCase();
        List<BookEntity> entities;

        if (fuzzySearch) {
            // Нестрогий поиск - ищем подстроку
            entities = bookRepository.findByTitleContainingIgnoreCase(query);
        } else {
            // Строгий поиск - ищем точное совпадение
            entities = bookRepository.findByTitleIgnoreCase(query);
        }
        // Применяем фильтр языков (настройки получаем один раз, а не на каждую книгу)
        if (userLogin != null) {
            UserSettings langSettings = userSettingsService.getSettings(userLogin).orElse(null);
            entities = entities.stream()
                    .filter(book -> userSettingsService.shouldSearchLanguage(langSettings, book.getLanguage()))
                    .collect(Collectors.toList());
        }

        return bookMapper.toBookList(entities);
    }

    /**
     * Поиск книг по названию серии
     */
    public List<Book> searchBySeries(String seriesQuery, String userLogin, boolean fuzzySearch) {
        if (seriesQuery == null || seriesQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String query = seriesQuery.trim().toLowerCase();
        List<BookEntity> entities;

        if (fuzzySearch) {
            // Нестрогий поиск - ищем подстроку
            entities = bookRepository.findBySeriesContainingIgnoreCase(query);
        } else {
            // Строгий поиск - ищем точное совпадение или по словам
            entities = bookRepository.findBySeriesIgnoreCase(query);
        }

        // Применяем фильтр языков (настройки получаем один раз, а не на каждую книгу)
        if (userLogin != null) {
            UserSettings langSettings = userSettingsService.getSettings(userLogin).orElse(null);
            entities = entities.stream()
                    .filter(book -> userSettingsService.shouldSearchLanguage(langSettings, book.getLanguage()))
                    .collect(Collectors.toList());
        }
        return bookMapper.toBookList(entities);
    }

    /**
     * Получает книги избранных авторов, у которых дата >= указанной даты
     */
    public List<Book> getBooksByFavoriteAuthorsAndDate(String username, LocalDate minDate) {
        if (username == null || username.trim().isEmpty() || minDate == null) {
            return new ArrayList<>();
        }

        // Получаем все книги избранных авторов с датой >= minDate
        List<BookEntity> allBooks = bookRepository.findBooksByFavoriteAuthorsAndDate(username.trim(), minDate);

        // Настройки языков получаем один раз, а не на каждую книгу (анти-N+1)
        UserSettings langSettings = userSettingsService.getSettings(username.trim()).orElse(null);

        // Фильтруем книги по языку
        List<BookEntity> matchingBooks = allBooks.stream()
                .filter(book -> userSettingsService.shouldSearchLanguage(langSettings, book.getLanguage()))
                .collect(Collectors.toList());

        log.debug("Найдено {} книг избранных авторов для пользователя {} с датой >= {}", 
                matchingBooks.size(), username, minDate);
        return bookMapper.toBookList(matchingBooks);
    }

}

