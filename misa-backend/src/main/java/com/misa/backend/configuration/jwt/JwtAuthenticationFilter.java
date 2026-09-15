package com.misa.backend.configuration.jwt;

import com.misa.backend.configuration.UserPrincipal;
import com.misa.backend.dto.response.auth.SignInStatus;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.service.JwtService;
import com.misa.backend.service.UserDetailsServiceCustomizer;
import com.misa.backend.util.SignOnUtils;
import com.nimbusds.jose.JOSEException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserDetailsServiceCustomizer userDetailsServiceCustomizer;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String jwt = jwtService.getJwtFromRequest(request);
        try {
            if (!StringUtils.hasText(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            String email = jwtService.extractUserName(jwt);
            UserDetails userDetails = userDetailsServiceCustomizer.loadUserByUsername(email);

            if (userDetails instanceof UserPrincipal user && jwtService.verificationToken(jwt, user)) {
                SignOnUtils.set(new SignOnUtils.SignOnUser(
                        user.getId(), jwt, null, SignInStatus.SUCCESS, null, user.getEmail()
                ));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } catch (ApplicationException | ParseException | JOSEException | UsernameNotFoundException e) {
            log.debug("JWT rejected: {}", e.getMessage());
            filterChain.doFilter(request, response);
        } finally {
            SignOnUtils.clear();
        }
    }
}
