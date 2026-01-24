-- Создание таблицы связи книг и авторов
CREATE TABLE IF NOT EXISTS book_to_author (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES book_author(id) ON DELETE CASCADE
);
