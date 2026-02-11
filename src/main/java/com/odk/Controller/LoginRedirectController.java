package com.odk.Controller;

import com.odk.Entity.Utilisateur;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.Service.UtilisateurService;
import com.odk.dto.LoginRequest;
import com.odk.dto.LoginResponse;
import com.odk.securityConfig.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/login")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:8089", "http://localhost:60409", "http://localhost:63243"})
@AllArgsConstructor
@Slf4j
public class LoginRedirectController {

    private AuthenticationManager authenticationManager;
    private PasswordEncoder passwordEncoder;
    private UtilisateurRepository utilisateurRepository;
    private UtilisateurService utilisateurService;
    private JwtService jwtService;

    @PostMapping
    public ResponseEntity<?> loginEndpoint(@RequestBody LoginRequest loginRequest, HttpServletRequest httpRequest) {
        log.info("=== TENTATIVE DE CONNEXION VIA /login ===");
        log.info("URL complète: {}", httpRequest.getRequestURL());
        log.info("Méthode: {}", httpRequest.getMethod());
        log.info("Content-Type: {}", httpRequest.getContentType());
        log.info("User-Agent: {}", httpRequest.getHeader("User-Agent"));
        log.info("Origin: {}", httpRequest.getHeader("Origin"));
        log.info("Utilisateur: {}", loginRequest.getUsername());
        log.info("Body reçu: {}", loginRequest);
        
        if (loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            log.warn("Tentative de connexion avec username ou password null");
            return ResponseEntity.badRequest().body("Nom d'utilisateur et mot de passe requis");
        }

        try {
            final Authentication authenticate = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );

            if (authenticate.isAuthenticated()) {
                Map<String, String> tokens = this.jwtService.generate(loginRequest.getUsername());
                String token = tokens.get("bearer");
                LoginResponse response = new LoginResponse(token, null);
                log.info("✅ CONNEXION RÉUSSIE pour l'utilisateur: {}", loginRequest.getUsername());
                return ResponseEntity.ok(response);
            } else {
                log.error("❌ Échec de l'authentification pour l'utilisateur: {}", loginRequest.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Échec de l'authentification");
            }
        } catch (BadCredentialsException e) {
            log.error("❌ Erreur d'authentification pour l'utilisateur: {} - {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nom d'utilisateur ou mot de passe incorrect");
        } catch (Exception e) {
            log.error("❌ Erreur lors de la connexion pour l'utilisateur: {} - {}", loginRequest.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion: " + e.getMessage());
        }
    }

    @PostMapping("/auth/login") // Endpoint de fallback pour /auth/login
    public ResponseEntity<?> authLoginEndpoint(@RequestBody LoginRequest loginRequest, HttpServletRequest httpRequest) {
        log.info("=== TENTATIVE DE CONNEXION VIA /auth/login (fallback) ===");
        return loginEndpoint(loginRequest, httpRequest);
    }
}
