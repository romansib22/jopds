package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rs.jopds.repository.entity.FavoriteAuthor;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteAuthorRepository extends JpaRepository<FavoriteAuthor, Long> {
    
    /**
     * Получает всех избранных авторов пользователя, отсортированных по full_name
     */
    List<FavoriteAuthor> findByUsernameOrderByFullNameAsc(String username);
    
    /**
     * Проверяет, есть ли автор в избранном по book_author_id
     */
    boolean existsByUsernameAndBookAuthorId(String username, Long bookAuthorId);
    
    /**
     * Находит избранного автора по username и book_author_id
     */
    Optional<FavoriteAuthor> findByUsernameAndBookAuthorId(String username, Long bookAuthorId);
    
    /**
     * Удаляет избранного автора по username и book_author_id
     */
    void deleteByUsernameAndBookAuthorId(String username, Long bookAuthorId);
}
