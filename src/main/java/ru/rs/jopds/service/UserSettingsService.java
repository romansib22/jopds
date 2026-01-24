package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rs.jopds.repository.entity.UserSettings;
import ru.rs.jopds.repository.UserSettingsRepository;

import java.util.Optional;

/**
 * Сервис для работы с настройками пользователей
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {
    
    private final UserSettingsRepository userSettingsRepository;
    
    /**
     * Получает настройки пользователя
     */
    public Optional<UserSettings> getSettings(String userLogin) {
        return userSettingsRepository.findByUserLogin(userLogin);
    }
    
    /**
     * Сохраняет или обновляет настройки пользователя
     */
    @Transactional
    public UserSettings saveSettings(String userLogin, Boolean searchRussian, 
                                     Boolean searchEnglish, Boolean searchOtherLanguages) {
        UserSettings settings = userSettingsRepository.findByUserLogin(userLogin)
            .orElse(UserSettings.builder()
                .userLogin(userLogin)
                .searchRussian(true)
                .searchEnglish(true)
                .searchOtherLanguages(true)
                .build());
        
        // Если настройки не найдены, создаем новую запись
        if (settings.getUserLogin() == null) {
            settings.setUserLogin(userLogin);
        }
        
        // Обрабатываем чекбоксы: если null, значит чекбокс не отмечен (false)
        settings.setSearchRussian(searchRussian != null && searchRussian);
        settings.setSearchEnglish(searchEnglish != null && searchEnglish);
        settings.setSearchOtherLanguages(searchOtherLanguages != null && searchOtherLanguages);
        
        UserSettings saved = userSettingsRepository.save(settings);
        log.info("Настройки пользователя {} сохранены: русский={}, английский={}, другие={}", 
                userLogin, saved.getSearchRussian(), saved.getSearchEnglish(), saved.getSearchOtherLanguages());
        
        return saved;
    }
    
    /**
     * Проверяет, должен ли язык быть включен в поиск
     */
    public boolean shouldSearchLanguage(String userLogin, String language) {
        if (userLogin == null) {
            // Если пользователь не аутентифицирован, показываем все языки
            return true;
        }
        
        UserSettings settings = getSettings(userLogin)
            .orElse(UserSettings.builder()
                .searchRussian(true)
                .searchEnglish(true)
                .searchOtherLanguages(true)
                .build());
        
        if (language == null || language.trim().isEmpty()) {
            return settings.getSearchOtherLanguages() != null && settings.getSearchOtherLanguages();
        }
        
        String langLower = language.toLowerCase().trim();
        
        // Русский язык
        if ("ru".equals(langLower) || "rus".equals(langLower) || "russian".equals(langLower) || "русский".equals(langLower)) {
            return settings.getSearchRussian() != null && settings.getSearchRussian();
        }
        
        // Английский язык
        if ("en".equals(langLower) || "eng".equals(langLower) || "english".equals(langLower) || "английский".equals(langLower)) {
            return settings.getSearchEnglish() != null && settings.getSearchEnglish();
        }
        
        // Другие языки
        return settings.getSearchOtherLanguages() != null && settings.getSearchOtherLanguages();
    }
}
