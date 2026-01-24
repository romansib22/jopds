package ru.rs.jopds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Конфигурация для пользователя admin по умолчанию
 */
@Data
@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {
    
    /**
     * Пароль пользователя admin по умолчанию
     */
    private String defaultPassword = "admin";
}
