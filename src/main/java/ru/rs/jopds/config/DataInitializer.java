package ru.rs.jopds.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.rs.jopds.repository.entity.Role;
import ru.rs.jopds.repository.entity.User;
import ru.rs.jopds.repository.entity.UserSettings;
import ru.rs.jopds.repository.RoleRepository;
import ru.rs.jopds.repository.UserRepository;
import ru.rs.jopds.repository.UserSettingsRepository;

/**
 * Инициализация начальных данных (роли и пользователь admin)
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    public void run(String... args) {
        initializeRoles();
        initializeAdminUser();
    }
    
    private void initializeRoles() {
        // Создаем роль USER, если её нет
        if (roleRepository.findByName("USER").isEmpty()) {
            Role userRole = Role.builder()
                .name("USER")
                .description("Обычный пользователь")
                .build();
            roleRepository.save(userRole);
            log.info("Создана роль USER");
        }
        
        // Создаем роль ADMIN, если её нет
        if (roleRepository.findByName("ADMIN").isEmpty()) {
            Role adminRole = Role.builder()
                .name("ADMIN")
                .description("Администратор")
                .build();
            roleRepository.save(adminRole);
            log.info("Создана роль ADMIN");
        }
    }
    
    private void initializeAdminUser() {
        // Создаем пользователя admin, если пользователей в нет совсем (инициализация БД)
        int userCount = userRepository.findAll().size();
        if (!userRepository.existsByLogin("admin") && userCount == 0) {
            Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("Роль ADMIN не найдена"));
            
            String adminPassword = adminProperties.getDefaultPassword();
            User admin = User.builder()
                .login("admin")
                .password(passwordEncoder.encode(adminPassword))
                .roleId(adminRole.getId())
                .description("Администратор системы")
                .isActive(true)
                .build();
            
            userRepository.save(admin);
            
            // Создаем настройки для admin
            UserSettings settings = UserSettings.builder()
                .userLogin("admin")
                .searchRussian(true)
                .searchEnglish(true)
                .searchOtherLanguages(true)
                .build();
            userSettingsRepository.save(settings);
            
            log.info("Создан пользователь admin с паролем из конфигурации");
        }
    }
}
