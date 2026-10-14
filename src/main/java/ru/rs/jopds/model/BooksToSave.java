package ru.rs.jopds.model;

import lombok.Data;
import ru.rs.jopds.repository.entity.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BooksToSave {
    private List<BookEntity> bookEntities = new ArrayList<>();
    /** Только новые авторы этого батча (ключ — нормализованное full_name) */
    private Map<String,BookAuthorEntity> bookAuthors = new HashMap<>();
    private List<BookToAuthor> bookToAuthors = new ArrayList<>();
    private List<BookToGenre> bookToGenres = new ArrayList<>();
}
