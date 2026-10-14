package ru.rs.jopds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.rs.jopds.repository.entity.BookAuthorEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookAuthorEntityRepository extends JpaRepository<BookAuthorEntity, Long> {
    /**
     * Находит автора по полному имени
     */
    Optional<BookAuthorEntity> findByFullName(String fullName);

    /**
     * Находит авторов по списку полных имён одним запросом (анти-N+1)
     */
    List<BookAuthorEntity> findByFullNameIn(Collection<String> fullNames);

    /**
     * Строгий поиск авторов по фамилии
     * Возвращает: id, first_name, last_name, middle_name, full_name, book_count
     */
    String SQL_FIND_BY_F = "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE last_name = ?1\n" +
            " GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name";

    @Query(value = SQL_FIND_BY_F, nativeQuery = true)
    List<Object[]> findByF(String lastName);

    /**
     * Нестрогий поиск авторов по фамилии
     * Возвращает: id, first_name, last_name, middle_name, full_name, book_count
     */
    String SQL_FIND_BY_F_FUZZY = "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE last_name like CONCAT('%', ?1, '%')\n" +
            " GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name";

    @Query(value = SQL_FIND_BY_F_FUZZY, nativeQuery = true)
    List<Object[]> findByFFuzzy(String lastName);

    /**
     * Строгий поиск авторов по имени и фамилии
     * Возвращает: id, first_name, last_name, middle_name, full_name, book_count
     */
    String SQL_FIND_BY_FI = "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) as book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE first_name = ?1\n" +
            "  AND last_name = ?2\n" +
            " GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name \n" +
            "UNION ALL   \n" +
            "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) as book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE first_name = ?2\n" +
            "  AND last_name = ?1\n" +
            "  GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name";

    @Query(value = SQL_FIND_BY_FI, nativeQuery = true)
    List<Object[]> findByFI(String firstName, String lastName);

    /**
     * Нестрогий поиск авторов по имени и фамилии
     * Возвращает: id, first_name, last_name, middle_name, full_name, book_count
     */
    String SQL_FIND_BY_FI_FUZZY = "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) as book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE first_name like CONCAT('%', ?1, '%')\n" +
            "  AND last_name like CONCAT('%', ?2, '%')\n" +
            " GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name \n" +
            "UNION ALL   \n" +
            "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) as book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE first_name like CONCAT('%', ?2, '%')\n" +
            "  AND last_name like CONCAT('%', ?1, '%')\n" +
            "  GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name";

    @Query(value = SQL_FIND_BY_FI_FUZZY, nativeQuery = true)
    List<Object[]> findByFIFuzzy(String firstName, String lastName);

    /**
     * Строгий поиск авторов по фамилии, имени и отчеству
     * Возвращает: id, first_name, last_name, middle_name, full_name, book_count
     */
    String SQL_FIND_BY_FIO = "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE a.last_name = ?1\n" +
            "  AND  a.first_name = ?2\n" +
            "  and a.middle_name = ?3\n" +
            " GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name \n" +
            "UNION ALL   \n" +
            "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE a.last_name = ?3\n" +
            "   AND  a.first_name = ?1\n" +
            "  and a.middle_name = ?2\n" +
            "  GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name";

    @Query(value = SQL_FIND_BY_FIO, nativeQuery = true)
    List<Object[]> findByFIO(String lastName, String firstName, String middleName);

    /**
     * Нестрогий поиск авторов по фамилии, имени и отчеству
     * Возвращает: id, first_name, last_name, middle_name, full_name, book_count
     */
    String SQL_FIND_BY_FIO_FUZZY = "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE a.last_name like CONCAT('%', ?1, '%')\n" +
            "  AND  a.first_name like CONCAT('%', ?2, '%')\n" +
            "  and a.middle_name like CONCAT('%', ?3, '%')\n" +
            " GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name \n" +
            "UNION ALL   \n" +
            "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE a.last_name like CONCAT('%', ?2, '%')\n" +
            "   AND  a.first_name like CONCAT('%', ?3, '%')\n" +
            "  and a.middle_name like CONCAT('%', ?1, '%')\n" +
            "  GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name";

    @Query(value = SQL_FIND_BY_FIO_FUZZY, nativeQuery = true)
    List<Object[]> findByFIOFuzzy(String lastName, String firstName, String middleName);

    /**
     * Получает уникальные первые символы full_name авторов, отсортированные по алфавиту
     * Возвращает: первый символ
     */
    String SQL_GET_FIRST_CHARS = "SELECT DISTINCT UPPER(LEFT(full_name, 1)) as first_char\n" +
            "  FROM book_author\n" +
            " WHERE full_name IS NOT NULL AND full_name != ''\n" +
            " ORDER BY first_char";

    @Query(value = SQL_GET_FIRST_CHARS, nativeQuery = true)
    List<String> getFirstChars();

    /**
     * Получает авторов, у которых full_name начинается с указанного префикса
     * Возвращает: id, first_name, last_name, middle_name, full_name, book_count
     */
    String SQL_FIND_BY_PREFIX = "SELECT a.id, a.first_name, a.last_name, a.middle_name, a.full_name, count(bta.book_id) as book_count\n" +
            "  FROM book_author a\n" +
            "  JOIN book_to_author bta ON bta.author_id = a.id\n" +
            " WHERE a.full_name LIKE CONCAT(?1, '%')\n" +
            " GROUP BY a.id, a.first_name, a.last_name, a.middle_name, a.full_name\n" +
            " ORDER BY a.full_name";

    @Query(value = SQL_FIND_BY_PREFIX, nativeQuery = true)
    List<Object[]> findByPrefix(String prefix);

    /**
     * Получает уникальные префиксы длины 2 из full_name авторов, которые начинаются с указанного префикса
     * Возвращает: префикс
     */
    String SQL_GET_PREFIXES_2 = "SELECT DISTINCT UPPER(LEFT(full_name, 2)) as prefix\n" +
            "  FROM book_author\n" +
            " WHERE full_name LIKE CONCAT(?1, '%')\n" +
            "   AND full_name IS NOT NULL AND full_name != ''\n" +
            " ORDER BY prefix";

    @Query(value = SQL_GET_PREFIXES_2, nativeQuery = true)
    List<String> getPrefixes2(String basePrefix);

    /**
     * Получает уникальные префиксы длины 3 из full_name авторов, которые начинаются с указанного префикса
     * Возвращает: префикс
     */
    String SQL_GET_PREFIXES_3 = "SELECT DISTINCT UPPER(LEFT(full_name, 3)) as prefix\n" +
            "  FROM book_author\n" +
            " WHERE full_name LIKE CONCAT(?1, '%')\n" +
            "   AND full_name IS NOT NULL AND full_name != ''\n" +
            " ORDER BY prefix";

    @Query(value = SQL_GET_PREFIXES_3, nativeQuery = true)
    List<String> getPrefixes3(String basePrefix);

    /**
     * Получает уникальные префиксы длины 4 из full_name авторов, которые начинаются с указанного префикса
     * Возвращает: префикс
     */
    String SQL_GET_PREFIXES_4 = "SELECT DISTINCT UPPER(LEFT(full_name, 4)) as prefix\n" +
            "  FROM book_author\n" +
            " WHERE full_name LIKE CONCAT(?1, '%')\n" +
            "   AND full_name IS NOT NULL AND full_name != ''\n" +
            " ORDER BY prefix";

    @Query(value = SQL_GET_PREFIXES_4, nativeQuery = true)
    List<String> getPrefixes4(String basePrefix);

    /**
     * Получает уникальные префиксы длины 5 из full_name авторов, которые начинаются с указанного префикса
     * Возвращает: префикс
     */
    String SQL_GET_PREFIXES_5 = "SELECT DISTINCT UPPER(LEFT(full_name, 5)) as prefix\n" +
            "  FROM book_author\n" +
            " WHERE full_name LIKE CONCAT(?1, '%')\n" +
            "   AND full_name IS NOT NULL AND full_name != ''\n" +
            " ORDER BY prefix";

    @Query(value = SQL_GET_PREFIXES_5, nativeQuery = true)
    List<String> getPrefixes5(String basePrefix);
}