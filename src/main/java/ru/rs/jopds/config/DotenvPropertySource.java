package ru.rs.jopds.config;

import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PropertySource для загрузки переменных из .env файла
 */
public class DotenvPropertySource extends PropertySource<Map<String, String>> {

    private static final String ENV_FILE = ".env";

    public DotenvPropertySource() {
        super("dotenv", loadEnvFile());
    }

    @Override
    public Object getProperty(String name) {
        return source.get(name);
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> properties = new HashMap<>();
        
        try {
            Path envPath = Paths.get(ENV_FILE);
            if (!Files.exists(envPath)) {
                String userDir = System.getProperty("user.dir");
                envPath = Paths.get(userDir, ENV_FILE);
            }

            if (Files.exists(envPath)) {
                System.out.println("[DotenvPropertySource] Загрузка .env файла из: " + envPath.toAbsolutePath());
                List<String> lines = Files.readAllLines(envPath);
                int loadedCount = 0;
                
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex > 0) {
                        String key = line.substring(0, equalsIndex).trim();
                        String value = line.substring(equalsIndex + 1).trim();
                        
                        // Удаляем кавычки, если есть
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        
                        properties.put(key, value);
                        
                        // Также устанавливаем значения для Spring Boot свойств, если это переменные datasource
                        if (key.equals("DATASOURCE_URL")) {
                            properties.put("spring.datasource.url", value);
                        } else if (key.equals("DATASOURCE_USERNAME")) {
                            properties.put("spring.datasource.username", value);
                        } else if (key.equals("DATASOURCE_PASSWORD")) {
                            properties.put("spring.datasource.password", value);
                        }
                        
                        loadedCount++;
                        System.out.println("[DotenvPropertySource] Загружена переменная: " + key + " = " + 
                            (key.toLowerCase().contains("password") ? "***" : value));
                    }
                }
                System.out.println("[DotenvPropertySource] Всего загружено переменных: " + loadedCount);
            } else {
                System.out.println("[DotenvPropertySource] .env файл не найден");
            }
        } catch (IOException e) {
            System.err.println("[DotenvPropertySource] Ошибка загрузки .env файла: " + e.getMessage());
        }
        
        return properties;
    }
}
