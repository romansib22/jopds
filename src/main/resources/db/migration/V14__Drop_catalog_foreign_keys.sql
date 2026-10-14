-- Удаление FOREIGN KEY из каталоговых таблиц (books, book_author, book_to_author, book_to_genre).
--
-- Зачем: каталог теперь заменяется импортом целиком через staging-таблицы
-- и атомарный RENAME TABLE (см. BookRepositoryService). CREATE TABLE ... LIKE
-- не копирует FOREIGN KEY — после первого такого обмена ограничения исчезли бы
-- молча. Убираем явно, чтобы схема БД соответствовала реальности.
--
-- FK и так не работали для приложения: импорт очищал таблицы через
-- TRUNCATE с SET FOREIGN_KEY_CHECKS=0 (обход ограничений), приложение —
-- единственный писатель каталога и поддерживает целостность само.
-- FK на users/user_settings/roles не затронуты.
--
-- Идемпотентность: DROP FOREIGN KEY IF EXISTS (расширение MariaDB, как IF NOT EXISTS в V8/V13).
-- Имена ограничений сгенерированы InnoDB для безымянных FOREIGN KEY: <таблица>_ibfk_N.

ALTER TABLE book_to_author DROP FOREIGN KEY IF EXISTS book_to_author_ibfk_1;
ALTER TABLE book_to_author DROP FOREIGN KEY IF EXISTS book_to_author_ibfk_2;
ALTER TABLE book_to_genre DROP FOREIGN KEY IF EXISTS book_to_genre_ibfk_1;
