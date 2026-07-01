package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.rs.jopds.JopdsApplication;
import ru.rs.jopds.repository.entity.Role;
import ru.rs.jopds.repository.entity.User;
import ru.rs.jopds.service.UserService;

import java.util.List;

/**
 * Контроллер для администрирования пользователей
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final UserService userService;
    
    /**
     * Страница администрирования
     */
    @GetMapping
    public String adminPage(Authentication authentication, Model model) {
        List<User> users = userService.getAllUsers();
        List<Role> roles = userService.getAllRoles();
        
        // Передаем имя текущего пользователя для проверки в шаблоне
        String currentUserLogin = authentication != null ? authentication.getName() : null;
        
        model.addAttribute("users", users);
        model.addAttribute("roles", roles);
        model.addAttribute("currentUserLogin", currentUserLogin);
        model.addAttribute("appVersion", getAppVersion());
        return "admin";
    }
    
    /**
     * Создание нового пользователя
     */
    @PostMapping("/users/create")
    public String createUser(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam Long roleId,
            @RequestParam(required = false) String description) {
        
        try {
            userService.createUser(login, password, roleId, description);
            return "redirect:/admin?success=created";
        } catch (Exception e) {
            return "redirect:/admin?error=" + e.getMessage();
        }
    }
    
    /**
     * Обновление роли пользователя
     */
    @PostMapping("/users/{login}/role")
    public String updateUserRole(
            @PathVariable String login,
            @RequestParam Long roleId) {
        
        try {
            userService.updateUserRole(login, roleId);
            return "redirect:/admin?success=role_updated";
        } catch (Exception e) {
            return "redirect:/admin?error=" + e.getMessage();
        }
    }
    
    /**
     * Деактивация пользователя
     */
    @PostMapping("/users/{login}/deactivate")
    public String deactivateUser(Authentication authentication, @PathVariable String login) {
        try {
            // Проверяем, что пользователь не пытается деактивировать сам себя
            String currentUserLogin = authentication != null ? authentication.getName() : null;
            if (currentUserLogin != null && currentUserLogin.equals(login)) {
                return "redirect:/admin?error=Нельзя деактивировать самого себя";
            }
            
            userService.deactivateUser(login);
            return "redirect:/admin?success=deactivated";
        } catch (Exception e) {
            return "redirect:/admin?error=" + e.getMessage();
        }
    }
    
    /**
     * Активация пользователя
     */
    @PostMapping("/users/{login}/activate")
    public String activateUser(@PathVariable String login) {
        try {
            userService.activateUser(login);
            return "redirect:/admin?success=activated";
        } catch (Exception e) {
            return "redirect:/admin?error=" + e.getMessage();
        }
    }

    private String getAppVersion() {
        String version = JopdsApplication.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
