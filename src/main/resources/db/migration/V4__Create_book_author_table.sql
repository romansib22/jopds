-- Создание таблицы авторов книг
CREATE TABLE IF NOT EXISTS book_author (
    id BIGINT PRIMARY KEY,
    last_name VARCHAR(255),
    first_name VARCHAR(255),
    middle_name VARCHAR(255),
    full_name VARCHAR(500)
);
