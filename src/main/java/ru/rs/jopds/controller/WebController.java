package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.BookAuthor;
import ru.rs.jopds.service.BookService;
import ru.rs.jopds.service.FavoriteAuthorService;
import ru.rs.jopds.service.SearchService;
import ru.rs.jopds.service.UserService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Веб-контроллер для главной страницы и поиска
 */
@Controller
@RequiredArgsConstructor
public class WebController {
    
    private final BookService bookService;
    private final SearchService searchService;
    private final UserService userService;
    private final FavoriteAuthorService favoriteAuthorService;
    
    /**
     * Главная страница с количеством книг и формой поиска
     */
    @GetMapping("/")
    public String index(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) Boolean fuzzySearch,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long seriesAuthorId,
            @RequestParam(required = false) String seriesName,
            @RequestParam(required = false) Boolean fromAuthors,
            @RequestParam(required = false) String authorsPrefix,
            @RequestParam(required = false) Boolean fromNewBooks,
            @RequestParam(required = false) String newBooksDate,
            Model model) {
        
        // Получаем данные о последнем сканировании
        bookService.getLatestScanResult().ifPresent(scanResult -> {
            model.addAttribute("bookCount", scanResult.getBookCount());
            model.addAttribute("scanDate", scanResult.getScanDate());
            model.addAttribute("scanResult", scanResult);
        });
        
        // Если нет результата сканирования, используем текущее количество книг
        if (!model.containsAttribute("bookCount")) {
            long bookCount = bookService.getActualBookCount();
            model.addAttribute("bookCount", bookCount);
        }
        
        // Добавляем информацию о пользователе
        String userLogin = null;
        String username = null;
        if (authentication != null) {
            username = authentication.getName();
            userLogin = username;
            model.addAttribute("username", username);
            model.addAttribute("isAdmin", userService.isAdmin(username));
        } else {
            // Убеждаемся, что username всегда в модели (даже если null)
            model.addAttribute("username", null);
        }
        
        // Устанавливаем тип поиска по умолчанию - "author"
        String defaultSearchType = searchType != null ? searchType : "author";
        model.addAttribute("searchType", defaultSearchType);
        
        // Если указан seriesAuthorId и seriesName, показываем книги этой серии автора
        if (seriesAuthorId != null && seriesName != null && !seriesName.trim().isEmpty()) {
            List<Book> books = searchService.getBooksByAuthorIdAndSeries(seriesAuthorId, seriesName, userLogin);
            model.addAttribute("searchResults", books);
            model.addAttribute("authorId", seriesAuthorId);
            model.addAttribute("seriesName", seriesName);
            model.addAttribute("isSeriesView", true);
            
            // Если переход был из раздела "Новинки избранных авторов", сохраняем информацию для кнопки "Назад"
            if (fromNewBooks != null && fromNewBooks) {
                model.addAttribute("fromNewBooks", true);
                if (newBooksDate != null && !newBooksDate.trim().isEmpty()) {
                    model.addAttribute("newBooksDate", newBooksDate);
                }
            }
            
            return "index";
        }
        
        // Если указан authorId, показываем книги этого автора
        if (authorId != null) {
            List<Book> books = searchService.getBooksByAuthorId(authorId, userLogin);
            model.addAttribute("searchResults", books);
            model.addAttribute("authorId", authorId);
            
            // Создаем Map для каждой книги: список авторов с их ID и ФИО
            Map<Integer, List<Map<String, Object>>> bookAuthorsMap = new HashMap<>();
            int bookIndex = 0;
            for (Book book : books) {
                List<Map<String, Object>> authorsList = new ArrayList<>();
                
                // Сначала пытаемся использовать список авторов
                if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                    for (BookAuthor author : book.getAuthors()) {
                        if (author.getFullName() != null) {
                            Map<String, Object> authorInfo = new HashMap<>();
                            authorInfo.put("name", author.getFullName());
                            authorInfo.put("authorId", author.getAuthorId());
                            authorInfo.put("lastName", author.getLastName());
                            authorInfo.put("firstName", author.getFirstName());
                            authorInfo.put("middleName", author.getMiddleName());
                            authorsList.add(authorInfo);
                        }
                    }
                }
                
                // Если список авторов пустой, используем authorsString
                if (authorsList.isEmpty() && book.getAuthorsString() != null && !book.getAuthorsString().isEmpty()) {
                    String[] authors = book.getAuthorsString().split(",");
                    for (String authorName : authors) {
                        String trimmedAuthor = authorName.trim();
                        if (!trimmedAuthor.isEmpty()) {
                            Map<String, Object> authorInfo = new HashMap<>();
                            authorInfo.put("name", trimmedAuthor);
                            authorInfo.put("authorId", null);
                            authorInfo.put("lastName", null);
                            authorInfo.put("firstName", null);
                            authorInfo.put("middleName", null);
                            authorsList.add(authorInfo);
                        }
                    }
                }
                
                // Добавляем в map только если есть авторы
                if (!authorsList.isEmpty()) {
                    bookAuthorsMap.put(bookIndex, authorsList);
                }
                bookIndex++;
            }
            model.addAttribute("bookAuthorsMap", bookAuthorsMap);
            
            // Если переход был из раздела "Авторы", сохраняем информацию для кнопки "Назад"
            if (fromAuthors != null && fromAuthors) {
                model.addAttribute("fromAuthors", true);
                if (authorsPrefix != null && !authorsPrefix.isEmpty()) {
                    model.addAttribute("authorsPrefix", authorsPrefix);
                }
            }
            
            // Сохраняем контекст поиска для кнопки "Назад", если он был передан
            if (query != null && !query.trim().isEmpty()) {
                model.addAttribute("backToSearchQuery", query);
                model.addAttribute("backToSearchType", defaultSearchType);
                model.addAttribute("backToFuzzySearch", fuzzySearch != null && fuzzySearch);
                model.addAttribute("showBackToSearch", true);
            }
            
            return "index";
        }
        
        // Если есть поисковый запрос, выполняем поиск
        if (query != null && !query.trim().isEmpty()) {
            boolean isFuzzySearch = fuzzySearch != null && fuzzySearch;
            
            if ("author".equals(defaultSearchType)) {
                // Поиск по автору возвращает список авторов
                List<BookAuthor> authors = searchService.searchByAuthor(query.trim(), isFuzzySearch);
                model.addAttribute("searchAuthors", authors);
                // Создаем Map для быстрого доступа к ID автора по fullName
                Map<String, Long> authorIdMap = new HashMap<>();
                for (BookAuthor author : authors) {
                    Long foundAuthorId = searchService.getAuthorIdByFullName(author.getFullName());
                    if (foundAuthorId != null) {
                        authorIdMap.put(author.getFullName(), foundAuthorId);
                    }
                }
                model.addAttribute("authorIdMap", authorIdMap);
                
                // Получаем множество ID избранных авторов для быстрой проверки
                if (userLogin != null) {
                    Set<Long> favoriteAuthorIds = favoriteAuthorService.getFavoriteAuthors(userLogin)
                            .stream()
                            .filter(fa -> fa.getBookAuthorId() != null)
                            .map(fa -> fa.getBookAuthorId())
                            .collect(Collectors.toSet());
                    model.addAttribute("favoriteAuthorIds", favoriteAuthorIds);
                }
            } else {
                // Поиск по названию и серии возвращает список книг
                List<Book> results = performSearch(query.trim(), defaultSearchType, userLogin, isFuzzySearch);
                model.addAttribute("searchResults", results);
                
                // Получаем множество ID избранных авторов для быстрой проверки
                if (userLogin != null) {
                    Set<Long> favoriteAuthorIds = favoriteAuthorService.getFavoriteAuthors(userLogin)
                            .stream()
                            .filter(fa -> fa.getBookAuthorId() != null)
                            .map(fa -> fa.getBookAuthorId())
                            .collect(Collectors.toSet());
                    model.addAttribute("favoriteAuthorIds", favoriteAuthorIds);
                }
                
                // Создаем Map для быстрого доступа к ID автора по имени из результатов поиска
                Map<String, Long> authorIdMapFromResults = new HashMap<>();
                // Создаем Map для каждой книги: список авторов с их ID
                Map<Integer, List<Map<String, Object>>> bookAuthorsMap = new HashMap<>();
                
                int bookIndex = 0;
                for (Book book : results) {
                    List<Map<String, Object>> authorsList = new ArrayList<>();
                    
                    if (book.getAuthorsString() != null && !book.getAuthorsString().isEmpty()) {
                        // Разбиваем строку авторов (может быть несколько авторов через запятую)
                        String[] authors = book.getAuthorsString().split(",");
                        for (String authorName : authors) {
                            String trimmedAuthor = authorName.trim();
                            if (!trimmedAuthor.isEmpty()) {
                                Map<String, Object> authorInfo = new HashMap<>();
                                authorInfo.put("name", trimmedAuthor);
                                
                                if (!authorIdMapFromResults.containsKey(trimmedAuthor)) {
                                    Long foundAuthorId = searchService.getAuthorIdByFullName(trimmedAuthor);
                                    if (foundAuthorId != null) {
                                        authorIdMapFromResults.put(trimmedAuthor, foundAuthorId);
                                    }
                                }
                                
                                Long foundAuthorIdForMap = authorIdMapFromResults.get(trimmedAuthor);
                                authorInfo.put("authorId", foundAuthorIdForMap);
                                
                                // Добавляем информацию об авторе для проверки избранного
                                // Пытаемся получить из BookAuthor, если есть
                                boolean authorFound = false;
                                if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                                    for (BookAuthor bookAuthor : book.getAuthors()) {
                                        if (bookAuthor.getFullName() != null && bookAuthor.getFullName().equals(trimmedAuthor)) {
                                            authorInfo.put("lastName", bookAuthor.getLastName());
                                            authorInfo.put("firstName", bookAuthor.getFirstName());
                                            authorInfo.put("middleName", bookAuthor.getMiddleName());
                                            authorFound = true;
                                            break;
                                        }
                                    }
                                }
                                
                                // Если не нашли в book.getAuthors(), пытаемся получить из БД
                                if (!authorFound) {
                                    BookAuthor authorFromDb = searchService.getAuthorByFullName(trimmedAuthor);
                                    if (authorFromDb != null) {
                                        authorInfo.put("lastName", authorFromDb.getLastName());
                                        authorInfo.put("firstName", authorFromDb.getFirstName());
                                        authorInfo.put("middleName", authorFromDb.getMiddleName());
                                    }
                                }
                                
                                authorsList.add(authorInfo);
                            }
                        }
                    } else if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                        // Если есть список авторов
                        for (BookAuthor author : book.getAuthors()) {
                            if (author.getFullName() != null) {
                                Map<String, Object> authorInfo = new HashMap<>();
                                authorInfo.put("name", author.getFullName());
                                
                                Long foundAuthorId = author.getAuthorId();
                                if (foundAuthorId == null && !authorIdMapFromResults.containsKey(author.getFullName())) {
                                    foundAuthorId = searchService.getAuthorIdByFullName(author.getFullName());
                                    if (foundAuthorId != null) {
                                        authorIdMapFromResults.put(author.getFullName(), foundAuthorId);
                                    }
                                } else if (foundAuthorId == null) {
                                    foundAuthorId = authorIdMapFromResults.get(author.getFullName());
                                }
                                
                                authorInfo.put("authorId", foundAuthorId);
                                
                                // Добавляем информацию об авторе для проверки избранного
                                authorInfo.put("lastName", author.getLastName());
                                authorInfo.put("firstName", author.getFirstName());
                                authorInfo.put("middleName", author.getMiddleName());
                                
                                authorsList.add(authorInfo);
                            }
                        }
                    }
                    
                    bookAuthorsMap.put(bookIndex, authorsList);
                    bookIndex++;
                }
                
                model.addAttribute("authorIdMapFromResults", authorIdMapFromResults);
                model.addAttribute("bookAuthorsMap", bookAuthorsMap);
            }
            
            model.addAttribute("searchQuery", query);
            model.addAttribute("fuzzySearch", isFuzzySearch);
        }
        
        return "index";
    }
    
    /**
     * Страница входа
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    /**
     * Страница новинок избранных авторов
     */
    @GetMapping("/new-books")
    public String newBooks(
            Authentication authentication,
            @RequestParam(required = false) String date,
            Model model) {
        
        // Добавляем информацию о пользователе
        String username = null;
        if (authentication != null) {
            username = authentication.getName();
            model.addAttribute("username", username);
            model.addAttribute("isAdmin", userService.isAdmin(username));
        } else {
            model.addAttribute("username", null);
            // Если пользователь не авторизован, перенаправляем на главную
            return "redirect:/";
        }
        
        // Парсим дату из параметра или используем дату по умолчанию (текущая дата - 30 дней)
        LocalDate searchDate;
        if (date != null && !date.trim().isEmpty()) {
            try {
                searchDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                // Если не удалось распарсить, используем дату по умолчанию
                searchDate = LocalDate.now().minusDays(30);
            }
        } else {
            searchDate = LocalDate.now().minusDays(30);
        }
        
        model.addAttribute("searchDate", searchDate);
        model.addAttribute("searchDateStr", searchDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        
        // Если дата была передана, выполняем поиск
        if (date != null && !date.trim().isEmpty()) {
            List<Book> books = searchService.getBooksByFavoriteAuthorsAndDate(username, searchDate);
            model.addAttribute("searchResults", books);
            
            // Создаем Map для каждой книги: список авторов с их ID и ФИО
            Map<Integer, List<Map<String, Object>>> bookAuthorsMap = new HashMap<>();
            int bookIndex = 0;
            for (Book book : books) {
                List<Map<String, Object>> authorsList = new ArrayList<>();
                
                // Сначала пытаемся использовать список авторов
                if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                    for (BookAuthor author : book.getAuthors()) {
                        if (author.getFullName() != null) {
                            Map<String, Object> authorInfo = new HashMap<>();
                            authorInfo.put("name", author.getFullName());
                            authorInfo.put("authorId", author.getAuthorId());
                            authorInfo.put("lastName", author.getLastName());
                            authorInfo.put("firstName", author.getFirstName());
                            authorInfo.put("middleName", author.getMiddleName());
                            authorsList.add(authorInfo);
                        }
                    }
                }
                
                // Если список авторов пустой, используем authorsString
                if (authorsList.isEmpty() && book.getAuthorsString() != null && !book.getAuthorsString().isEmpty()) {
                    String[] authors = book.getAuthorsString().split(",");
                    for (String authorName : authors) {
                        String trimmedAuthor = authorName.trim();
                        if (!trimmedAuthor.isEmpty()) {
                            Map<String, Object> authorInfo = new HashMap<>();
                            authorInfo.put("name", trimmedAuthor);
                            Long foundAuthorId = searchService.getAuthorIdByFullName(trimmedAuthor);
                            authorInfo.put("authorId", foundAuthorId);
                            authorsList.add(authorInfo);
                        }
                    }
                }
                
                if (!authorsList.isEmpty()) {
                    bookAuthorsMap.put(bookIndex, authorsList);
                }
                bookIndex++;
            }
            model.addAttribute("bookAuthorsMap", bookAuthorsMap);
        }
        
        return "new-books";
    }
    
    /**
     * Выполняет поиск в зависимости от типа (только для поиска по названию и серии)
     */
    private List<Book> performSearch(String query, String searchType, String userLogin, boolean fuzzySearch) {
        switch (searchType.toLowerCase()) {
            case "title":
                return searchService.searchByTitle(query, userLogin, fuzzySearch);
            case "series":
                return searchService.searchBySeries(query, userLogin, fuzzySearch);
            default:
                return searchService.searchByTitle(query, userLogin, fuzzySearch);
        }
    }
}

