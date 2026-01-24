package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rs.jopds.repository.FavoriteAuthorRepository;
import ru.rs.jopds.repository.entity.FavoriteAuthor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с избранными авторами
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteAuthorService {

    private final FavoriteAuthorRepository favoriteAuthorRepository;

    /**
     * Получает всех избранных авторов пользователя
     */
    public List<FavoriteAuthor> getFavoriteAuthors(String username) {
        if (username == null) {
            return List.of();
        }
        return favoriteAuthorRepository.findByUsernameOrderByFullNameAsc(username);
    }

    /**
     * Получает Map<fullName, bookAuthorId> избранных авторов всех пользователей
     */
    public Map<String, List<FavoriteAuthor>> getFavoriteAuthorsMap() {
        Map<String, List<FavoriteAuthor>> result = new HashMap<>();
        List<FavoriteAuthor> allFavorites = favoriteAuthorRepository.findAll();
        for (FavoriteAuthor f : allFavorites) {
            if (f.getFullName() != null) {
                List<FavoriteAuthor> list = result.getOrDefault(f.getFullName(), new ArrayList<>());
                list.add(f);
                result.put(f.getFullName(), list);
            }
        }
        return result;
    }

    /**
     * Проверяет, является ли автор избранным по book_author_id
     */
    public boolean isFavoriteByAuthorId(String username, Long bookAuthorId) {
        if (username == null || bookAuthorId == null) {
            return false;
        }
        return favoriteAuthorRepository.existsByUsernameAndBookAuthorId(username, bookAuthorId);
    }

    /**
     * Добавляет автора в избранное по book_author_id
     */
    @Transactional
    public boolean addToFavoritesByAuthorId(String username, Long bookAuthorId, String fullName) {
        if (username == null || bookAuthorId == null) {
            return false;
        }

        // Проверяем, не добавлен ли уже
        if (isFavoriteByAuthorId(username, bookAuthorId)) {
            return false;
        }

        FavoriteAuthor favorite = FavoriteAuthor.builder()
                .username(username)
                .bookAuthorId(bookAuthorId)
                .fullName(fullName)
                .build();

        favoriteAuthorRepository.save(favorite);
        log.debug("Автор с ID {} добавлен в избранное для пользователя {}", bookAuthorId, username);
        return true;
    }

    /**
     * Удаляет автора из избранного по book_author_id
     */
    @Transactional
    public boolean removeFromFavoritesByAuthorId(String username, Long bookAuthorId) {
        if (username == null || bookAuthorId == null) {
            return false;
        }

        favoriteAuthorRepository.deleteByUsernameAndBookAuthorId(username, bookAuthorId);
        log.debug("Автор с ID {} удален из избранного для пользователя {}", bookAuthorId, username);
        return true;
    }

    /**
     * Переключает статус избранного по book_author_id
     */
    @Transactional
    public boolean toggleFavoriteByAuthorId(String username, Long bookAuthorId, String fullName) {
        if (isFavoriteByAuthorId(username, bookAuthorId)) {
            return removeFromFavoritesByAuthorId(username, bookAuthorId);
        } else {
            return addToFavoritesByAuthorId(username, bookAuthorId, fullName);
        }
    }
}
