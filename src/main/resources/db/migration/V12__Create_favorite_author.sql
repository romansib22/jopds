CREATE TABLE IF NOT EXISTS favorite_author (
  id BIGINT AUTO_INCREMENT NOT NULL,
   book_author_id BIGINT NOT NULL,
   username VARCHAR(100) NOT NULL,
   full_name VARCHAR(500) NULL,
   CONSTRAINT pk_favorite_author PRIMARY KEY (id)
);

ALTER TABLE favorite_author ADD CONSTRAINT uc_favorite_author UNIQUE (username, book_author_id);
CREATE INDEX IF NOT EXISTS idx_favorite_author_book_author_id ON favorite_author(book_author_id);