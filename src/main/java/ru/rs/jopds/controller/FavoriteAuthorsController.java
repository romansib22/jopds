package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.rs.jopds.repository.entity.FavoriteAuthor;
import ru.rs.jopds.service.FavoriteAuthorService;
import ru.rs.jopds.service.UserService;

import java.util.List;

/**
 * Контроллер для страницы избранных авторов
 */
@Controller
@RequiredArgsConstructor
public class FavoriteAuthorsController {

    private final FavoriteAuthorService favoriteAuthorService;
    private final UserService userService;

    /**
     * Страница со списком избранных авторов
     */
    @GetMapping("/favorite-authors")
    public String favoriteAuthors(Authentication authentication, Model model) {
        if (authentication == null) {
            return "redirect:/login";
        }

        String username = authentication.getName();
        model.addAttribute("username", username);
        model.addAttribute("isAdmin", userService.isAdmin(username));

        // Получаем список избранных авторов
        List<FavoriteAuthor> favoriteAuthors = favoriteAuthorService.getFavoriteAuthors(username);
        model.addAttribute("favoriteAuthors", favoriteAuthors);

        return "favorite-authors";
    }
}
