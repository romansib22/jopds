package ru.rs.jopds.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * JPA Entity для хранения списка авторов книги в БД
 */
@Entity
@Table(name = "book_author")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAuthorEntity {
    @Id
    private Long id;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "middle_name")
    private String middleName;
    @Column(name = "full_name")
    private String fullName;
}
