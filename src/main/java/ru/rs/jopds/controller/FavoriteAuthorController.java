package ru.rs.jopds.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rs.jopds.service.FavoriteAuthorService;

/**
 * REST контроллер для работы с избранными авторами
 */
@RestController
@RequestMapping("/api/favorite-authors")
@RequiredArgsConstructor
public class FavoriteAuthorController {

    private final FavoriteAuthorService favoriteAuthorService;


    /**
     * Переключает статус избранного автора по book_author_id
     */
    @PostMapping("/toggle-by-id")
    public ResponseEntity<ToggleResponse> toggleFavoriteById(
            @RequestBody ToggleByIdRequest request,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        Long bookAuthorId = request.getBookAuthorId();
        
        // Определяем текущий статус ДО переключения
        boolean wasFavorite = favoriteAuthorService.isFavoriteByAuthorId(username, bookAuthorId);
        
        // Переключаем статус
        boolean toggleSuccess = favoriteAuthorService.toggleFavoriteByAuthorId(
                username, bookAuthorId, request.getFullName());
        
        if (!toggleSuccess) {
            // Если переключение не удалось, возвращаем текущий статус
            boolean currentStatus = favoriteAuthorService.isFavoriteByAuthorId(username, bookAuthorId);
            return ResponseEntity.ok(new ToggleResponse(currentStatus));
        }

        // Новый статус - противоположный предыдущему
        boolean newStatus = !wasFavorite;
        
        return ResponseEntity.ok(new ToggleResponse(newStatus));
    }

    /**
     * Проверяет, является ли автор избранным по book_author_id
     */
    @GetMapping("/check-by-id")
    public ResponseEntity<CheckResponse> checkFavoriteById(
            @RequestParam Long bookAuthorId,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        boolean isFavorite = favoriteAuthorService.isFavoriteByAuthorId(username, bookAuthorId);

        return ResponseEntity.ok(new CheckResponse(isFavorite));
    }

    @Data
    public static class ToggleResponse {
        @JsonProperty("isFavorite")
        private boolean isFavorite;

        public ToggleResponse(boolean isFavorite) {
            this.isFavorite = isFavorite;
        }
    }

    @Data
    public static class CheckResponse {
        @JsonProperty("isFavorite")
        private boolean isFavorite;

        public CheckResponse(boolean isFavorite) {
            this.isFavorite = isFavorite;
        }
    }

    @Data
    public static class ToggleByIdRequest {
        private Long bookAuthorId;
        private String fullName;
    }
}
