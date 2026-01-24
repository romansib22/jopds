CREATE TABLE IF NOT EXISTS book_to_genre (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    genre_id varchar(255) NOT NULL,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_book_to_genre_book_id ON book_to_genre(book_id);
--CREATE INDEX IF NOT EXISTS idx_book_to_genre_genre_id ON book_to_genre(genre_id);