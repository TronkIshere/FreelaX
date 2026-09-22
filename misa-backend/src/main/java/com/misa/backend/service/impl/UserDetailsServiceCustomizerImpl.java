package com.misa.backend.service.impl;

import com.misa.backend.configuration.UserPrincipal;
import com.misa.backend.entity.User;
import com.misa.backend.repository.UserRepository;
import com.misa.backend.service.UserDetailsServiceCustomizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceCustomizerImpl implements UserDetailsServiceCustomizer {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return UserPrincipal.create(user);
    }
}
