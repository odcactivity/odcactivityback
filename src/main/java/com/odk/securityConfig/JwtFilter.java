package com.odk.securityConfig;

import com.odk.Entity.Jwt;
import com.odk.Service.Interface.Service.UtilisateurService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Service
public class JwtFilter extends OncePerRequestFilter {

    private final UtilisateurService utilisateurService;
    private final JwtService jwtService;

    public JwtFilter(UtilisateurService utilisateurService, JwtService jwtService) {
        this.utilisateurService = utilisateurService;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!HttpMethod.OPTIONS.matches(request.getMethod())) {
            authenticateIfTokenPresent(request);
        }

        filterChain.doFilter(request, response);
    }

    /** Authentifie l'utilisateur si un Bearer token valide est présent (y compris sur les routes publiques). */
    private void authenticateIfTokenPresent(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authHeader.substring(7);
        Jwt tokenDansLaBDD = jwtService.tokenByValue(token);
        if (tokenDansLaBDD == null || jwtService.isTokenExpired(token)) {
            return;
        }

        String username = jwtService.extractUsername(token);
        if (username == null
                || tokenDansLaBDD.getUtilisateur() == null
                || !tokenDansLaBDD.getUtilisateur().getEmail().equals(username)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UserDetails userDetails = utilisateurService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
