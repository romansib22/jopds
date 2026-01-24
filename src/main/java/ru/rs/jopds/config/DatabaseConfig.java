package ru.rs.jopds.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Конфигурация для подключения к MySQL БД
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfig {
    
    private final DatabaseProperties dbProperties;
    
    public DatabaseConfig(DatabaseProperties dbProperties) {
        this.dbProperties = dbProperties;
    }
    
    @Bean
    @Primary
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        
        // Устанавливаем драйвер MySQL
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        log.info("Настроена MySQL БД");
        
        // Устанавливаем параметры подключения из конфигурации
        dataSource.setUrl(dbProperties.getUrl());
        dataSource.setUsername(dbProperties.getUsername());
        dataSource.setPassword(dbProperties.getPassword());
        
        return dataSource;
    }
}

