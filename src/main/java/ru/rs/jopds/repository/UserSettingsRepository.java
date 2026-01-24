package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rs.jopds.repository.entity.UserSettings;

import java.util.Optional;

/**
 * Repository для работы с настройками пользователей
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {

    Optional<UserSettings> findByUserLogin(String userLogin);
}
