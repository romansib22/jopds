package ru.rs.jopds.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * Настройки пользователя (фильтры поиска)
 */
@Entity
@Table(name = "user_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {
    
    @Id
    @Column(name = "user_login", length = 100)
    private String userLogin;
    
    @OneToOne
    @JoinColumn(name = "user_login", insertable = false, updatable = false)
    private User user;
    
    @Column(name = "search_russian")
    @Builder.Default
    private Boolean searchRussian = true;
    
    @Column(name = "search_english")
    @Builder.Default
    private Boolean searchEnglish = true;
    
    @Column(name = "search_other_languages")
    @Builder.Default
    private Boolean searchOtherLanguages = true;
}
