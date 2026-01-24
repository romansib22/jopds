package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rs.jopds.repository.entity.BookToGenre;

public interface BookToGenreRepository extends JpaRepository<BookToGenre, Long> {
}
