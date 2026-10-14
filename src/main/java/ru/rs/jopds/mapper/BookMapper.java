package ru.rs.jopds.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.BookAuthor;
import ru.rs.jopds.model.BooksToSave;
import ru.rs.jopds.model.ImportContext;
import ru.rs.jopds.repository.GenreRepository;
import ru.rs.jopds.repository.entity.*;
import ru.rs.jopds.service.FavoriteAuthorService;

import java.util.*;

/**
 * Mapper для преобразования между Book и BookEntity
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class BookMapper {
    private final GenreRepository genreRepository;
    private final FavoriteAuthorService favoriteAuthorService;

    /**
     * Готовит контекст импорта: загружает карту жанров и карту избранных авторов
     * (один раз на весь импорт, а не на каждый батч)
     */
    public ImportContext createImportContext() {
        Map<String, String> genreMap = new HashMap<>();
        genreRepository.findAll().forEach(genre -> genreMap.put(genre.getCode(), genre.getGenre()));
        return new ImportContext(genreMap, favoriteAuthorService.getFavoriteAuthorsMap());
    }

    /**
     * Преобразует батч книг в сущности для сохранения.
     * Все вызовы в рамках одного импорта должны использовать один и тот же
     * ImportContext — тогда ID и дедупликация авторов сквозные между батчами.
     */
    public BooksToSave toEntity(List<Book> books, ImportContext context) {
        BooksToSave result = new BooksToSave();
        for (Book book : books) {
            long bookId = context.nextId();
            result.getBookEntities().add(toEntity(book, bookId, context.getGenreMap()));
            getAuthors(result.getBookAuthors(), result.getBookToAuthors(), context, book, bookId);
            getBookGenres(result.getBookToGenres(), book, bookId, context.getGenreMap());
        }
        return result;
    }


    public BookEntity toEntity(Book book, long bookId, Map<String, String> genreMap) {
        if (book == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String genreCode : book.getGenres()) {
            if (sb.length() == 0) {
                sb.append(genreMap.getOrDefault(genreCode, genreCode));
            } else {
                sb.append(", ").append(genreMap.getOrDefault(genreCode, genreCode));
            }
        }
        return BookEntity.builder()
                .id(bookId)
                .title(book.getTitle())
                .authorsString(book.getAuthorsString())
                .series(book.getSeries())
                .seriesNumber(book.getSeriesNumber())
                .genresString(sb.toString())
                .language(book.getLanguage())
                .format(book.getFormat())
                .fileSize(book.getFileSize())
                .date(book.getDate())
                .filePath(book.getFilePath())
                .additionalInfo(book.getAdditionalInfo())
                .archivePath(book.getArchivePath())
                .build();
    }

    private void getAuthors(Map<String, BookAuthorEntity> bookAuthors,
                            List<BookToAuthor> bookToAuthors,
                            ImportContext context,
                            Book book,
                            long bookId) {
        for (BookAuthor author : book.getAuthors()) {
            Long id = null;
            String fullName = replaceRussianLetters(author.getFullName());

            BookAuthorEntity existing = context.getAuthorsByName().get(fullName);
            if (existing == null) {
                id = context.nextId();
                existing = BookAuthorEntity.builder()
                        .id(id)
                        .lastName(replaceRussianLetters(author.getLastName()))
                        .firstName(replaceRussianLetters(author.getFirstName()))
                        .middleName(replaceRussianLetters(author.getMiddleName()))
                        .fullName(fullName)
                        .build();
                context.getAuthorsByName().put(fullName, existing);
                // В bookAuthors попадают только новые авторы батча — их и сохраняем
                bookAuthors.put(fullName, existing);
            } else {
                id = existing.getId();
            }
            if (id != null) {
                bookToAuthors.add(BookToAuthor.builder()
                        .bookId(bookId)
                        .authorId(id)
                        .build());

                // Переназначенные избранные копятся в контексте — сохраняются один раз
                // в конце импорта, после атомарной замены таблиц
                if (context.getFavoriteAuthorsMap().containsKey(fullName)) {
                    boolean added = false;
                    for (FavoriteAuthor fa : context.getFavoriteAuthorsMap().get(fullName)) {
                        fa.setBookAuthorId(id);
                        context.getRemappedFavorites().add(fa);
                        added = true;
                    }
                    if (added) {
                        context.getFavoriteAuthorsMap().remove(fullName);
                    }
                }
            }
        }
    }

    private String replaceRussianLetters(String input) {
        return input.replace("ё", "е").replace("Ё", "Е").trim();
    }

    private void getBookGenres(List<BookToGenre> bookToGenres, Book book, long bookId, Map<String, String> genreMap) {
        for (String genre : book.getGenres()) {
            if (genreMap.containsKey(genre)) {
                bookToGenres.add(BookToGenre.builder()
                        .bookId(bookId)
                        .genreId(genre)
                        .build());
            }
        }
    }

    /**
     * Преобразует BookEntity в Book
     */
    public Book toBook(BookEntity entity) {
        if (entity == null) {
            return null;
        }
        String[] genres = entity.getGenresString() != null ? entity.getGenresString().split(", ") : new String[0];

        return Book.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .authorsString(entity.getAuthorsString())
                .series(entity.getSeries())
                .seriesNumber(entity.getSeriesNumber())
                .genres(Arrays.asList(genres))
                .language(entity.getLanguage())
                .format(entity.getFormat())
                .fileSize(entity.getFileSize())
                .date(entity.getDate())
                .filePath(entity.getFilePath())
                .additionalInfo(entity.getAdditionalInfo())
                .archivePath(entity.getArchivePath())
                .build();
    }

    /**
     * Преобразует список BookEntity в список Book
     */
    public List<Book> toBookList(List<BookEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }

        List<Book> books = new ArrayList<>();
        for (BookEntity entity : entities) {
            books.add(toBook(entity));
        }
        return books;
    }
}

