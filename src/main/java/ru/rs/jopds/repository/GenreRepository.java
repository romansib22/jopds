package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rs.jopds.repository.entity.Genre;

public interface GenreRepository extends JpaRepository<Genre, String> {
}
