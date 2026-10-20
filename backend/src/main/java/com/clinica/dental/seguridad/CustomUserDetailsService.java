package com.clinica.dental.seguridad;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final SystemUserRepository userRepository;

    public CustomUserDetailsService(SystemUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SystemUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario " + username + " no encontrado"));

        Set<GrantedAuthority> authorities = new HashSet<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority(role.getRoleName()));
            for (Permission perm : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority("OP_" + perm.getPermissionName()));
            }
        }

        return User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .disabled(!Boolean.TRUE.equals(user.getActive()))
            .authorities(authorities)
            .build();
    }
}
