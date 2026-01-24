package ru.rs.jopds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Конфигурация для INPX сервиса
 */
@Data
@Component
@ConfigurationProperties(prefix = "inpx")
public class InpxProperties {
    
    /**
     * Полный путь к INPX файлу (приоритетнее чем directory + fileName)
     */
    private String filePath;
    
    /**
     * Путь к директории с INPX файлами
     */
    private String directory = "./inpx";
    
    /**
     * Имя INPX файла для чтения (опционально, если не указано, берется первый найденный)
     */
    private String fileName;
    
    /**
     * Путь к директории с архивами книг (ZIP файлы с книгами)
     * Используется для скачивания книг из архивов
     */
    private String bookDirectory;
}

