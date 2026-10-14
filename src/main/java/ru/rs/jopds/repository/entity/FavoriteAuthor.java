package ru.rs.jopds.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * Entity для хранения избранных авторов пользователя
 */
@Entity
@Table(name = "favorite_author")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteAuthor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_author_id", nullable = false)
    private Long bookAuthorId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "full_name")
    private String fullName;
}
