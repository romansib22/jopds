package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.rs.jopds.repository.entity.UserSettings;
import ru.rs.jopds.service.UserSettingsService;
import ru.rs.jopds.service.UserService;

/**
 * Контроллер для настроек пользователя
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class UserSettingsController {
    
    private final UserSettingsService userSettingsService;
    private final UserService userService;
    
    /**
     * Страница настроек пользователя
     */
    @GetMapping
    public String settingsPage(Authentication authentication, Model model) {
        String userLogin = authentication.getName();
        UserSettings settings = userSettingsService.getSettings(userLogin)
            .orElse(UserSettings.builder()
                .searchRussian(true)
                .searchEnglish(true)
                .searchOtherLanguages(true)
                .build());
        
        // Убеждаемся, что значения не null
        if (settings.getSearchRussian() == null) {
            settings.setSearchRussian(true);
        }
        if (settings.getSearchEnglish() == null) {
            settings.setSearchEnglish(true);
        }
        if (settings.getSearchOtherLanguages() == null) {
            settings.setSearchOtherLanguages(true);
        }
        
        model.addAttribute("settings", settings);
        return "settings";
    }
    
    /**
     * Сохранение настроек поиска
     */
    @PostMapping("/search-filters")
    public String saveSearchFilters(
            Authentication authentication,
            @RequestParam(required = false) Boolean searchRussian,
            @RequestParam(required = false) Boolean searchEnglish,
            @RequestParam(required = false) Boolean searchOtherLanguages) {
        
        String userLogin = authentication.getName();
        // Если чекбокс не отмечен, он не отправляется, поэтому значение будет null
        // В сервисе обрабатываем: null = false (чекбокс не отмечен)
        userSettingsService.saveSettings(userLogin, searchRussian, searchEnglish, searchOtherLanguages);
        
        return "redirect:/settings?success=saved";
    }
    
    /**
     * Страница смены пароля
     */
    @GetMapping("/change-password")
    public String changePasswordPage(Model model) {
        return "change-password";
    }
    
    /**
     * Обработка смены пароля
     */
    @PostMapping("/change-password")
    public String changePassword(
            Authentication authentication,
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model) {
        
        String userLogin = authentication.getName();
        
        // Проверка на совпадение нового пароля и подтверждения
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Новый пароль и подтверждение не совпадают");
            return "change-password";
        }
        
        // Проверка длины пароля
        if (newPassword.length() < 8) {
            model.addAttribute("error", "Пароль должен содержать не менее 8 символов");
            return "change-password";
        }
        
        try {
            userService.changePassword(userLogin, oldPassword, newPassword);
            return "redirect:/settings?success=password_changed";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "change-password";
        }
    }
}
