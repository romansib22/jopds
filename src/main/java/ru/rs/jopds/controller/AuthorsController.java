package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rs.jopds.service.AuthorService;
import ru.rs.jopds.service.FavoriteAuthorService;
import ru.rs.jopds.service.UserService;

import javax.servlet.http.HttpSession;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Контроллер для страницы авторов
 */
@Controller
@RequiredArgsConstructor
public class AuthorsController {

    private final AuthorService authorService;
    private final UserService userService;
    private final FavoriteAuthorService favoriteAuthorService;
    private static final String SESSION_FILTER_KEY = "authorsFilterType";

    /**
     * Страница со списком авторов
     */
    @GetMapping("/authors")
    public String authors(
            Authentication authentication,
            @RequestParam(required = false) String prefix,
            HttpSession session,
            Model model) {

        // Добавляем информацию о пользователе
        String username = null;
        if (authentication != null) {
            username = authentication.getName();
            model.addAttribute("username", username);
            model.addAttribute("isAdmin", userService.isAdmin(username));
            
            // Получаем множество ID избранных авторов для быстрой проверки
            Set<Long> favoriteAuthorIds = favoriteAuthorService.getFavoriteAuthors(username)
                    .stream()
                    .filter(fa -> fa.getBookAuthorId() != null)
                    .map(fa -> fa.getBookAuthorId())
                    .collect(Collectors.toSet());
            model.addAttribute("favoriteAuthorIds", favoriteAuthorIds);
        } else {
            model.addAttribute("username", null);
        }

        // Получаем фильтр из сессии
        String filterType = (String) session.getAttribute(SESSION_FILTER_KEY);
        if (filterType == null) {
            filterType = "ALL"; // По умолчанию "Все"
        }
        model.addAttribute("currentFilter", filterType);

        // Получаем результат навигации с учетом фильтра
        AuthorService.AuthorNavigationResult result = authorService.getNavigationResult(prefix, filterType);
        model.addAttribute("navigationResult", result);
        model.addAttribute("navType", result.getType().toString());

        // Для кнопки "Назад" определяем предыдущий префикс
        if (result.getCurrentPrefix() != null && !result.getCurrentPrefix().isEmpty()) {
            String currentPrefix = result.getCurrentPrefix();
            if (currentPrefix.length() > 1) {
                // Убираем последний символ для возврата на предыдущий уровень
                String backPrefix = currentPrefix.substring(0, currentPrefix.length() - 1);
                model.addAttribute("backPrefix", backPrefix.isEmpty() ? null : backPrefix);
            } else {
                // Возвращаемся на главную страницу авторов
                model.addAttribute("backPrefix", null);
            }
        } else {
            model.addAttribute("backPrefix", null);
        }

        return "authors";
    }

    /**
     * Обработка фильтрации
     */
    @PostMapping("/authors/filter")
    public String filterAuthors(
            @RequestParam String filterType,
            HttpSession session) {
        
        // Сохраняем фильтр в сессии
        session.setAttribute(SESSION_FILTER_KEY, filterType);
        
        // Перенаправляем на страницу авторов без префикса (главная страница)
        return "redirect:/authors";
    }
}
