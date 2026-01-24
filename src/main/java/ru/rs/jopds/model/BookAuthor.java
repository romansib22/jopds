package ru.rs.jopds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Список авторов книги
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAuthor {
    /**
     * Фамилия
     */
    private String lastName;
    /**
     * Имя
     */
    private String firstName;
    /**
     * Отчество
     */
    private String middleName;

    /**
     * Полное фио
     */
    private String fullName;

    private Integer bookCount;
    private Long authorId;
}
