package com.payment.backend.service.impl;

import com.payment.backend.configuration.UserPrincipal;
import com.payment.backend.entity.User;
import com.payment.backend.repository.UserRepository;
import com.payment.backend.service.UserDetailsServiceCustomizer;
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
