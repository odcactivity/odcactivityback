/*package com.odk.securityConfig;

import com.odk.Entity.Jwt;
import com.odk.Service.Interface.Service.UtilisateurService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token;
        Jwt tokenDansLaBDD = null;
        String username = null;
        boolean isTokenExpired = true;

        // Ignorer Swagger et API docs
        String path = request.getRequestURI();
        if(path.startsWith("/swagger") || path.startsWith("/v3/api-docs") || path.startsWith("/webjars")) {
            filterChain.doFilter(request, response);
            return;
        }


        // Bearer eyJhbGciOiJIUzI1NiJ9.eyJub20iOiJBY2hpbGxlIE1CT1VHVUVORyIsImVtYWlsIjoiYWNoaWxsZS5tYm91Z3VlbmdAY2hpbGxvLnRlY2gifQ.zDuRKmkonHdUez-CLWKIk5Jdq9vFSUgxtgdU1H2216U
        final String authorization = request.getHeader("Authorization");
        if(authorization != null && authorization.startsWith("Bearer ")){
            token = authorization.substring(7);
            tokenDansLaBDD = this.jwtService.tokenByValue(token);
            isTokenExpired = jwtService.isTokenExpired(token);
            username = jwtService.extractUsername(token);
        }

        if(
                !isTokenExpired
                        && tokenDansLaBDD.getUtilisateur().getEmail().equals(username)
                        && SecurityContextHolder.getContext().getAuthentication() == null
        ) {
            UserDetails userDetails = utilisateurService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }

}
*/
package com.odk.securityConfig;

import com.odk.Entity.Jwt;
import com.odk.Service.Interface.Service.UtilisateurService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // --- Ignorer les endpoints publics (Swagger, API docs, webjars)
        if (path.startsWith("/swagger") || path.startsWith("/v3/api-docs") || path.startsWith("/webjars")) {
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
