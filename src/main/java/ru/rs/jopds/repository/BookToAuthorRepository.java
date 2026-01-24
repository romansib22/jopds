package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rs.jopds.repository.entity.BookToAuthor;

@Repository
public interface BookToAuthorRepository extends JpaRepository<BookToAuthor, Long> {

}
