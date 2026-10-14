package ru.rs.jopds.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.rs.jopds.repository.entity.BookAuthorEntity;
import ru.rs.jopds.repository.entity.FavoriteAuthor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Состояние одного импорта каталога: сквозные ID, дедупликация авторов,
 * карта жанров и избранное.
 * Раньше жило в мутируемых полях синглтона BookMapper — из-за этого импорт
 * нельзя было разбить на батчи (последовательность ID и дедупликация авторов
 * обнулялись бы на каждом батче). Теперь контекст создаётся на импорт
 * и передаётся через все батчи.
 */
@Getter
@RequiredArgsConstructor
public class ImportContext {

    /** Карта жанров (код → название), загружается один раз в начале импорта */
    private final Map<String, String> genreMap;

    /** Избранные авторы по нормализованному full_name, загружаются один раз;
     *  после сопоставления с новым ID запись удаляется, чтобы не переназначать повторно */
    private final Map<String, List<FavoriteAuthor>> favoriteAuthorsMap;

    /** Дедупликация авторов по нормализованному full_name (ё → е) в рамках одного импорта */
    private final Map<String, BookAuthorEntity> authorsByName = new HashMap<>();

    /** Единая последовательность ID для книг и авторов (как в исходной реализации) */
    private long sequence = 0L;

    public synchronized long nextId() {
        return sequence++;
    }
}
