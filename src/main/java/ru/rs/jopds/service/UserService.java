package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rs.jopds.repository.entity.Role;
import ru.rs.jopds.repository.entity.User;
import ru.rs.jopds.repository.entity.UserSettings;
import ru.rs.jopds.repository.RoleRepository;
import ru.rs.jopds.repository.UserRepository;
import ru.rs.jopds.repository.UserSettingsRepository;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с пользователями
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * Создает нового пользователя
     */
    @Transactional
    public User createUser(String login, String password, Long roleId, String description) {
        if (userRepository.existsByLogin(login)) {
            throw new IllegalArgumentException("Пользователь с логином " + login + " уже существует");
        }
        
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Роль с ID " + roleId + " не найдена"));
        
        User user = User.builder()
            .login(login)
            .password(passwordEncoder.encode(password))
            .roleId(roleId)
            .description(description)
            .isActive(true)
            .build();
        
        User savedUser = userRepository.save(user);
        
        // Создаем настройки по умолчанию для нового пользователя
        UserSettings settings = UserSettings.builder()
            .userLogin(login)
            .searchRussian(true)
            .searchEnglish(true)
            .searchOtherLanguages(true)
            .build();
        userSettingsRepository.save(settings);
        
        log.info("Создан пользователь: {}", login);
        return savedUser;
    }
    
    /**
     * Получает пользователя по логину
     */
    public Optional<User> findByLogin(String login) {
        return userRepository.findByLoginAndIsActiveTrue(login);
    }
    
    /**
     * Получает всех пользователей
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    /**
     * Обновляет роль пользователя
     */
    @Transactional
    public User updateUserRole(String login, Long roleId) {
        User user = userRepository.findById(login)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Роль не найдена"));
        
        user.setRoleId(roleId);
        return userRepository.save(user);
    }
    
    /**
     * Деактивирует пользователя
     */
    @Transactional
    public void deactivateUser(String login) {
        User user = userRepository.findById(login)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        user.setIsActive(false);
        userRepository.save(user);
        log.info("Пользователь {} деактивирован", login);
    }
    
    /**
     * Активирует пользователя
     */
    @Transactional
    public void activateUser(String login) {
        User user = userRepository.findById(login)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        user.setIsActive(true);
        userRepository.save(user);
        log.info("Пользователь {} активирован", login);
    }
    
    /**
     * Получает все роли
     */
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
    
    /**
     * Проверяет, является ли пользователь администратором
     */
    public boolean isAdmin(String login) {
        return userRepository.findByLoginAndIsActiveTrue(login)
            .map(user -> {
                Role role = roleRepository.findById(user.getRoleId()).orElse(null);
                return role != null && "ADMIN".equals(role.getName());
            })
            .orElse(false);
    }
    
    /**
     * Меняет пароль пользователя
     * 
     * @param login логин пользователя
     * @param oldPassword старый пароль
     * @param newPassword новый пароль
     * @throws IllegalArgumentException если старый пароль неверен или новый пароль не соответствует требованиям
     */
    @Transactional
    public void changePassword(String login, String oldPassword, String newPassword) {
        User user = userRepository.findById(login)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        
        // Проверяем старый пароль
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Неверный старый пароль");
        }
        
        // Проверяем длину нового пароля
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Пароль должен содержать не менее 8 символов");
        }
        
        // Устанавливаем новый пароль
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("Пароль пользователя {} изменен", login);
    }
}
