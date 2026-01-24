package ru.rs.jopds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация для подключения к MySQL БД
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource")
public class DatabaseProperties {
    
    /**
     * URL подключения к БД
     */
    private String url;
    
    /**
     * Имя пользователя БД
     */
    private String username;
    
    /**
     * Пароль БД
     */
    private String password;
}

