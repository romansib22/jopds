-- Создание таблицы книг
CREATE TABLE IF NOT EXISTS books (
    id BIGINT PRIMARY KEY,
    title VARCHAR(1000) NOT NULL,
    authors_string TEXT,
    series VARCHAR(1000),
    series_number INT,
    genres_string TEXT,
    language VARCHAR(50),
    format VARCHAR(50),
    file_size BIGINT,
    date DATE,
    file_path VARCHAR(1000),
    additional_info TEXT,
    archive_path VARCHAR(255)
);
