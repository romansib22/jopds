-- Создание таблицы пользователей
CREATE TABLE IF NOT EXISTS users (
    login VARCHAR(100) NOT NULL PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);
