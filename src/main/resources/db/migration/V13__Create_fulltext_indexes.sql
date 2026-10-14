-- FULLTEXT-индексы для поиска без полного скана таблицы
-- Используются:
--   idx_title_ft      - нестрогий поиск по названию книги (MATCH ... AGAINST вместо LIKE '%...%')
--   idx_last_name_ft  - нестрогий поиск автора по фамилии
-- Запросы короче 3 символов (innodb_ft_min_token_size) продолжают идти через LIKE-fallback.

CREATE FULLTEXT INDEX idx_title_ft ON books(title);
CREATE FULLTEXT INDEX idx_last_name_ft ON book_author(last_name);
