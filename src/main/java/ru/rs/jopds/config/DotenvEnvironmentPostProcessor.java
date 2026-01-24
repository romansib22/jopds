package ru.rs.jopds.config;

import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

/**
 * EnvironmentPostProcessor для загрузки переменных из .env файла
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, 
                                       org.springframework.boot.SpringApplication application) {
        MutablePropertySources propertySources = environment.getPropertySources();
        // Добавляем DotenvPropertySource с высоким приоритетом (первым)
        propertySources.addFirst(new DotenvPropertySource());
        
        // Отладочный вывод - проверяем, что переменные доступны
        System.out.println("[DotenvEnvironmentPostProcessor] Проверка переменных в Environment:");
        System.out.println("  DATASOURCE_URL = " + environment.getProperty("DATASOURCE_URL"));
        System.out.println("  spring.datasource.url = " + environment.getProperty("spring.datasource.url"));
        System.out.println("  DATASOURCE_USERNAME = " + environment.getProperty("DATASOURCE_USERNAME"));
        System.out.println("  spring.datasource.username = " + environment.getProperty("spring.datasource.username"));
    }
}
