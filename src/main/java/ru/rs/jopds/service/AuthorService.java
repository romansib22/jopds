package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.jopds.model.BookAuthor;
import ru.rs.jopds.repository.BookAuthorEntityRepository;
import ru.rs.jopds.repository.entity.BookAuthorEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для работы с авторами
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorService {

    private final BookAuthorEntityRepository authorRepository;
    private static final int MAX_AUTHORS_PER_PAGE = 50;

    /**
     * Получает список первых символов full_name всех авторов
     */
    public List<String> getFirstChars() {
        return authorRepository.getFirstChars();
    }

    /**
     * Фильтрует список первых символов по типу
     */
    public List<String> filterFirstChars(List<String> chars, String filterType) {
        if (filterType == null || filterType.isEmpty() || "ALL".equals(filterType)) {
            return chars;
        }

        return chars.stream()
                .filter(charStr -> {
                    if (charStr == null || charStr.isEmpty()) {
                        return false;
                    }
                    char firstChar = charStr.charAt(0);
                    switch (filterType) {
                        case "RUSSIAN":
                            return (firstChar >= 'А' && firstChar <= 'Я') || (firstChar >= 'а' && firstChar <= 'я');
                        case "ENGLISH":
                            return (firstChar >= 'A' && firstChar <= 'Z') || (firstChar >= 'a' && firstChar <= 'z');
                        case "SYMBOLS":
                            return (firstChar >= '0' && firstChar <= '9') || !Character.isLetter(firstChar);
                        case "OTHER":
                            return Character.isLetter(firstChar) && 
                                   !((firstChar >= 'А' && firstChar <= 'Я') || (firstChar >= 'а' && firstChar <= 'я')) &&
                                   !((firstChar >= 'A' && firstChar <= 'Z') || (firstChar >= 'a' && firstChar <= 'z'));
                        default:
                            return true;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Получает авторов по префиксу full_name
     */
    public List<BookAuthor> getAuthorsByPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedPrefix = prefix.trim().toUpperCase();
        List<Object[]> results = authorRepository.findByPrefix(normalizedPrefix);

        return results.stream()
                .map(row -> {
                    Long id = row[0] != null ? ((Number) row[0]).longValue() : null;
                    String firstName = row[1] != null ? (String) row[1] : null;
                    String lastName = row[2] != null ? (String) row[2] : null;
                    String middleName = row[3] != null ? (String) row[3] : null;
                    String fullName = row[4] != null ? (String) row[4] : null;
                    Integer bookCount = row[5] != null ? ((Number) row[5]).intValue() : 0;

                    return BookAuthor.builder()
                            .authorId(id)
                            .firstName(firstName)
                            .lastName(lastName)
                            .middleName(middleName)
                            .fullName(fullName)
                            .bookCount(bookCount)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Получает префиксы следующей длины для указанного базового префикса
     */
    public List<String> getPrefixes(String basePrefix, int length) {
        if (basePrefix == null || basePrefix.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedPrefix = basePrefix.trim().toUpperCase();

        switch (length) {
            case 2:
                return authorRepository.getPrefixes2(normalizedPrefix);
            case 3:
                return authorRepository.getPrefixes3(normalizedPrefix);
            case 4:
                return authorRepository.getPrefixes4(normalizedPrefix);
            case 5:
                return authorRepository.getPrefixes5(normalizedPrefix);
            default:
                // Для других длин получаем авторов и извлекаем префиксы в Java
                List<BookAuthor> authors = getAuthorsByPrefix(normalizedPrefix);
                return authors.stream()
                        .map(author -> {
                            String fullName = author.getFullName();
                            if (fullName != null && fullName.length() >= length) {
                                return fullName.substring(0, length).toUpperCase();
                            }
                            return null;
                        })
                        .filter(prefix -> prefix != null)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
        }
    }

    /**
     * Определяет, нужно ли показывать префиксы или авторов
     * Если авторов больше MAX_AUTHORS_PER_PAGE, возвращает префиксы следующей длины
     */
    public AuthorNavigationResult getNavigationResult(String prefix) {
        return getNavigationResult(prefix, null);
    }

    /**
     * Определяет, нужно ли показывать префиксы или авторов с учетом фильтра
     * Если авторов больше MAX_AUTHORS_PER_PAGE, возвращает префиксы следующей длины
     */
    public AuthorNavigationResult getNavigationResult(String prefix, String filterType) {
        if (prefix == null || prefix.trim().isEmpty()) {
            // Первая страница - показываем первые символы
            List<String> firstChars = getFirstChars();
            // Применяем фильтр, если указан
            if (filterType != null && !filterType.isEmpty()) {
                firstChars = filterFirstChars(firstChars, filterType);
            }
            return AuthorNavigationResult.builder()
                    .type(AuthorNavigationType.FIRST_CHARS)
                    .items(firstChars)
                    .build();
        }

        String normalizedPrefix = prefix.trim().toUpperCase();
        List<BookAuthor> authors = getAuthorsByPrefix(normalizedPrefix);

        // После 4 символов всегда показываем авторов, независимо от количества
        if (normalizedPrefix.length() >= 4) {
            // Показываем авторов
            return AuthorNavigationResult.builder()
                    .type(AuthorNavigationType.AUTHORS)
                    .authors(authors)
                    .currentPrefix(normalizedPrefix)
                    .build();
        }

        if (authors.size() <= MAX_AUTHORS_PER_PAGE) {
            // Показываем авторов
            return AuthorNavigationResult.builder()
                    .type(AuthorNavigationType.AUTHORS)
                    .authors(authors)
                    .currentPrefix(normalizedPrefix)
                    .build();
        } else {
            // Показываем префиксы следующей длины
            int nextLength = normalizedPrefix.length() + 1;
            List<String> prefixes = getPrefixes(normalizedPrefix, nextLength);
            return AuthorNavigationResult.builder()
                    .type(AuthorNavigationType.PREFIXES)
                    .items(prefixes)
                    .currentPrefix(normalizedPrefix)
                    .build();
        }
    }

    /**
     * Результат навигации по авторам
     */
    @lombok.Data
    @lombok.Builder
    public static class AuthorNavigationResult {
        private AuthorNavigationType type;
        private List<String> items; // Для FIRST_CHARS и PREFIXES
        private List<BookAuthor> authors; // Для AUTHORS
        private String currentPrefix; // Текущий префикс для навигации назад
    }

    /**
     * Тип навигации
     */
    public enum AuthorNavigationType {
        FIRST_CHARS,  // Показываем первые символы
        PREFIXES,     // Показываем префиксы
        AUTHORS       // Показываем авторов
    }
}
