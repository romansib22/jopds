package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rs.jopds.repository.entity.User;

import java.util.Optional;

/**
 * Repository для работы с пользователями
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByLoginAndIsActiveTrue(String login);

    boolean existsByLogin(String login);
}
