package ru.rs.jopds.mapper;

import org.junit.jupiter.api.Test;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.BookAuthor;
import ru.rs.jopds.model.BooksToSave;
import ru.rs.jopds.model.ImportContext;
import ru.rs.jopds.repository.GenreRepository;
import ru.rs.jopds.repository.entity.FavoriteAuthor;
import ru.rs.jopds.repository.entity.Genre;
import ru.rs.jopds.service.FavoriteAuthorService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Свойства ImportContext при батчевом импорте: сквозные ID между батчами,
 * дедупликация авторов, переназначение избранного ровно один раз
 */
class BookMapperImportContextTest {

    @Test
    void crossBatchIdsAuthorDedupAndFavorites() {
        GenreRepository genreRepository = mock(GenreRepository.class);
        FavoriteAuthorService favoriteAuthorService = mock(FavoriteAuthorService.class);
        when(genreRepository.findAll()).thenReturn(List.of(
                Genre.builder().code("sf_fantasy").genre("Фэнтези").build()));
        FavoriteAuthor favorite = FavoriteAuthor.builder()
                .id(1L).bookAuthorId(999L).username("admin").fullName("Перумов Николай").build();
        // Карта должна быть мутабельной: mapper удаляет переназначенных авторов
        when(favoriteAuthorService.getFavoriteAuthorsMap())
                .thenReturn(new HashMap<>(Map.of("Перумов Николай", List.of(favorite))));

        BookMapper mapper = new BookMapper(genreRepository, favoriteAuthorService);
        ImportContext context = mapper.createImportContext();

        Book book1 = Book.builder()
                .title("Книга 1")
                .genres(List.of("sf_fantasy"))
                .authors(List.of(
                        BookAuthor.builder().fullName("Перумов Николай").lastName("перумов").firstName("николай").middleName("").build(),
                        BookAuthor.builder().fullName("Кош Алекс").lastName("кош").firstName("алекс").middleName("").build()))
                .build();
        Book book2 = Book.builder()
                .title("Книга 2")
                .genres(List.of())
                .authors(List.of(
                        BookAuthor.builder().fullName("Перумов Николай").lastName("перумов").firstName("николай").middleName("").build(),
                        BookAuthor.builder().fullName("Лукьяненко Сергей").lastName("лукьяненко").firstName("сергей").middleName("").build()))
                .build();

        BooksToSave batch1 = mapper.toEntity(List.of(book1), context);
        BooksToSave batch2 = mapper.toEntity(List.of(book2), context);

        // Книга 1: id=0, её новые авторы id=1,2; книга 2: id=3 — последовательность сквозная
        assertEquals(0L, batch1.getBookEntities().get(0).getId());
        assertEquals(3L, batch2.getBookEntities().get(0).getId());

        // Автор из батча 1 не создаётся заново в батче 2
        assertEquals(2, batch1.getBookAuthors().size());
        assertEquals(1, batch2.getBookAuthors().size());
        assertTrue(batch2.getBookAuthors().containsKey("Лукьяненко Сергей"));

        // Связь книги 2 с уже известным автором указывает на его ID из батча 1
        assertEquals(2, batch2.getBookToAuthors().size());
        assertEquals(1L, batch2.getBookToAuthors().get(0).getAuthorId());
        assertEquals(4L, batch2.getBookToAuthors().get(1).getAuthorId());

        // Избранный переназначен на новый ID ровно один раз и копится в контексте
        assertEquals(1, context.getRemappedFavorites().size());
        assertEquals(1L, favorite.getBookAuthorId());
        assertTrue(context.getFavoriteAuthorsMap().isEmpty());

        // Жанр раскодирован через карту жанров контекста
        assertEquals("Фэнтези", batch1.getBookEntities().get(0).getGenresString());
        assertEquals(1, batch1.getBookToGenres().size());
    }
}
