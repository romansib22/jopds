package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.rs.jopds.repository.entity.Role;
import ru.rs.jopds.repository.entity.User;
import ru.rs.jopds.repository.RoleRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Сервис для загрузки пользователей из БД для Spring Security
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserService userService;
    private final RoleRepository roleRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> userOpt = userService.findByLogin(username);
        
        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("Пользователь не найден: " + username);
        }
        
        User user = userOpt.get();
        
        // Загружаем роль
        Role role = roleRepository.findById(user.getRoleId())
            .orElseThrow(() -> new UsernameNotFoundException("Роль не найдена для пользователя: " + username));
        
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getLogin())
            .password(user.getPassword())
            .authorities(getAuthorities(role))
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(!user.getIsActive())
            .build();
    }
    
    private Collection<? extends GrantedAuthority> getAuthorities(Role role) {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.getName()));
    }
}
