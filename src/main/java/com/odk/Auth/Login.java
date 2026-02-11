package com.odk.Auth;

import com.odk.Entity.Role;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.Service.EmailService;
import com.odk.Service.Interface.Service.UtilisateurService;
import com.odk.dto.AuthentificationDTO;
import com.odk.dto.LoginRequest;
import com.odk.dto.LoginResponse;
import com.odk.securityConfig.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
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
import java.util.Optional;



@RestController
@RequestMapping("/auth") // Uniquement /auth pour éviter les conflits
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:8089", "http://localhost:60409", "http://localhost:63243"})
@AllArgsConstructor
@Slf4j
@Transactional
@Tag(name = "Authentification", description = "API pour l'authentification des utilisateurs")
public class Login {

    private AuthenticationManager authenticationManager;
    private PasswordEncoder passwordEncoder;
    private UtilisateurRepository utilisateurRepository;
    private UtilisateurService utilisateurService;
    private JwtService jwtService;
    private EmailService emailService;
    //private JwtEncoder jwtEncoder;

    @Operation(summary = "Connexion utilisateur", description = "Authentifie un utilisateur et retourne un token JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Connexion réussie", 
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Identifiants incorrects"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    @PostMapping("/login")
    public ResponseEntity<?> connexion(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        log.info("Tentative de connexion depuis l'IP: {}", request.getRemoteAddr());
        log.info("User-Agent: {}", request.getHeader("User-Agent"));
        log.info("Origin: {}", request.getHeader("Origin"));
        log.info("Requête de connexion pour l'utilisateur: {}", loginRequest.getUsername());
        
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
                String token = tokens.get("bearer"); // Le JwtService retourne "bearer" pas "token"
                LoginResponse response = new LoginResponse(token, null); // Pas de refreshToken pour l'instant
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Échec de l'authentification");
            }
        } catch (BadCredentialsException e) {
            log.error("Erreur d'authentification pour l'utilisateur: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nom d'utilisateur ou mot de passe incorrect");
        } catch (Exception e) {
            log.error("Erreur lors de la connexion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion: " + e.getMessage());
        }
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<String> requestPasswordReset(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Optional<Utilisateur> utilisateurOpt = utilisateurRepository.findByEmail(email);

        if (utilisateurOpt.isPresent()) {
            Utilisateur utilisateur = utilisateurOpt.get();
            String token = jwtService.generateResetPasswordToken(utilisateur.getEmail()).get("token"); // Corrigé ici

            // Envoyer le mail avec instructions
            String resetPasswordUrl = "http://localhost:4200/set-new-password?token=" + token;
            emailService.sendSimpleEmail(email, "Réinitialisation de votre mot de passe",
                    String.format("Bonjour %s, cliquez sur ce lien pour réinitialiser votre mot de passe : %s", utilisateur.getNom(), resetPasswordUrl));
            return ResponseEntity.ok("{\"message\": \"Un email vous a été envoyé avec les instructions.\"}");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur introuvable");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> resetRequest) {
        String token = resetRequest.get("token");
        String newPassword = resetRequest.get("newPassword");

        // Décoder le token pour obtenir l'email
        String email = jwtService.getEmailFromToken(token);

        Optional<Utilisateur> utilisateurOpt = utilisateurRepository.findByEmail(email);
        if (utilisateurOpt.isPresent()) {
            Utilisateur utilisateur = utilisateurOpt.get();

            // Mettre à jour le mot de passe
            utilisateur.setPassword(passwordEncoder.encode(newPassword));
            utilisateurRepository.save(utilisateur);

            return ResponseEntity.status(HttpStatus.OK).body(Map.of("message", "Mot de passe mis à jour avec succès").toString());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur introuvable ou token invalide");
        }
    }

    // 3. Déconnexion - Invalider le token JWT si utilisé

    // Exemple de méthode pour envoyer un email (à implémenter ou intégrer un service d'envoi d'email)
    private void sendEmailWithResetLink(String email, String resetPasswordLink) {
        // Utiliser un service SMTP comme JavaMail, SendGrid, ou autre API pour envoyer l'email avec le lien
    }
}
