package com.odk.securityConfig;

import com.odk.Entity.Jwt;
import com.odk.Service.Interface.Service.UtilisateurService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod; // Added this import
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

    // Liste des chemins qui sont permitAll() dans Security.java
    private static final String[] PERMIT_ALL_PATHS = {
            "/auth/**",
            "/login",
            "/login/**",
            "/images/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-resources/**",
            "/webjars/**",
            "/api-docs/**",
            "/swagger-resources/configuration/**",
            "/swagger-resources/configuration/ui/**",
            "/swagger-resources/configuration/security/**",
            "/configuration/**",
            "/configuration/ui/**",
            "/configuration/security/**",
            "/activitevalidation/**",
            "/entites/**",
            "/reporting/**",
            "/reportinghebdo/**",
            "/api/validation-test/**",
            "/api/debug/**",
            "/api/courriers/reponse",
            "/api/courriers/*/reponses",
            "/api/courriers/*/has-reponded",
            "/api/courriers/*/1",
            "/api/courriers/ENVOYER/**",
            "/api/courriers/IMPUTER/**",
            "/api/courriers/EN_COURS/**",
            "/api/courriers/ARCHIVER/**",
            "/api/courriers/REPONDU/**"
    };

    public JwtFilter(UtilisateurService utilisateurService, JwtService jwtService) {
        this.utilisateurService = utilisateurService;
        this.jwtService = jwtService;
    }

    // Méthode pour vérifier si le chemin de la requête correspond à un chemin permitAll()
    private boolean isPermittedPath(String requestURI) {
        for (String pattern : PERMIT_ALL_PATHS) {
            // Simplified path matching, Spring's AntPathMatcher is more robust but this will work for common cases
            // and avoids adding more dependencies just for this.
            if (pattern.endsWith("/**")) {
                if (requestURI.startsWith(pattern.substring(0, pattern.length() - 3))) {
                    return true;
                }
            } else if (pattern.endsWith("/*")) {
                String basePattern = pattern.substring(0, pattern.length() - 1);
                if (requestURI.startsWith(basePattern) && requestURI.indexOf('/', basePattern.length()) == -1) {
                    return true;
                }
            } else {
                if (requestURI.equals(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Si le chemin est un endpoint permitAll() ou une requête OPTIONS, ignorer la validation JWT
        if (isPermittedPath(path) || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        final String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;
        boolean isTokenValid = false;
        Jwt tokenDansLaBDD = null;


        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            tokenDansLaBDD = jwtService.tokenByValue(token);

            if (tokenDansLaBDD != null) { // Vérification null
                isTokenValid = !jwtService.isTokenExpired(token);
                username = jwtService.extractUsername(token);
            }
        }

        // --- Authentification si token valide et utilisateur présent
        if (isTokenValid
                && tokenDansLaBDD != null
                && tokenDansLaBDD.getUtilisateur().getEmail().equals(username)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = utilisateurService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // Continuer la chaîne
        filterChain.doFilter(request, response);
    }
}
