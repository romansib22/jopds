-- Создание индексов для таблицы books
CREATE INDEX IF NOT EXISTS idx_title ON books(title);
CREATE INDEX IF NOT EXISTS idx_series ON books(series);
CREATE INDEX IF NOT EXISTS idx_language ON books(language);
CREATE INDEX IF NOT EXISTS idx_format ON books(format);

-- Создание индексов для таблицы book_author
CREATE INDEX IF NOT EXISTS idx_last_name ON book_author(last_name);
CREATE INDEX IF NOT EXISTS idx_first_name ON book_author(first_name);
CREATE INDEX IF NOT EXISTS idx_middle_name ON book_author(middle_name);

-- Создание индексов для таблицы book_to_author
CREATE INDEX IF NOT EXISTS idx_book_id ON book_to_author(book_id);
CREATE INDEX IF NOT EXISTS idx_author_id ON book_to_author(author_id);
