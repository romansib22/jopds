-- Создание таблицы настроек пользователей
CREATE TABLE IF NOT EXISTS user_settings (
    user_login VARCHAR(100) NOT NULL PRIMARY KEY,
    search_russian BOOLEAN DEFAULT TRUE,
    search_english BOOLEAN DEFAULT TRUE,
    search_other_languages BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (user_login) REFERENCES users(login)
);
