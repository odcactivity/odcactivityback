package com.odk.securityConfig;

import com.odk.Entity.Jwt;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.JwtRepository;
import com.odk.Service.Interface.Service.UtilisateurService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {
    public static final String BEARER = "bearer";
    public static final String TOKEN = "token"; // Changé pour éviter la confusion
    public static final String REFRESH_TOKEN = "refreshToken";
    private final String ENCRIPTION_KEY = "608f36e92dc66d97d5933f0e6371493cb4fc05b1aa8f8de64014732472303a7c";
    private final UtilisateurService utilisateurService;
    private final JwtRepository jwtRepository;

    public Jwt tokenByValue(String value) {
        return this.jwtRepository.findByValeurAndDesactiveAndExpire(
                value,
                false,
                false
        ).orElseThrow(() -> new RuntimeException("Token invalide ou inconnu"));
    }
    public Map<String, String> generate(String username) {
        System.out.println("=== DEBUG: Génération de token pour " + username + " ===");
        Utilisateur utilisateur = (Utilisateur) this.utilisateurService.loadUserByUsername(username);
        System.out.println("Utilisateur trouvé: " + utilisateur.getEmail());
        
        final Map<String, String> jwtMap = this.generateJwt(utilisateur);
        System.out.println("JWT Map: " + jwtMap);
        
        final Map<String, String> refreshTokenMap = this.generateRefreshToken(utilisateur);
        System.out.println("RefreshToken Map: " + refreshTokenMap);

        final Jwt jwt = Jwt
                .builder()
                .valeur(jwtMap.get("bearer"))
                .desactive(false)
                .expire(false)
                .utilisateur(utilisateur)
                .refreshToken(refreshTokenMap.get("refreshToken"))
                .build();
        this.jwtRepository.save(jwt);
        
        // Retourner les deux tokens avec les bonnes clés
        Map<String, String> response = new HashMap<>();
        response.put("token", jwtMap.get("bearer"));
        response.put("refreshToken", refreshTokenMap.get("refreshToken"));
        System.out.println("Response finale: " + response);
        System.out.println("=== FIN DEBUG ===");
        return response;
    }

    public String extractUsername(String token) {
        return this.getClaim(token, Claims::getSubject);
    }

    public boolean isTokenExpired(String token) {
        Date expirationDate = getExpirationDateFromToken(token);
        return expirationDate.before(new Date());
    }

    private Date getExpirationDateFromToken(String token) {
        return this.getClaim(token, Claims::getExpiration);
    }

    private <T> T getClaim(String token, Function<Claims, T> function) {
        Claims claims = getAllClaims(token);
        return function.apply(claims);
    }

    private Claims getAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(this.getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Map<String, String> generateJwt(Utilisateur utilisateur) {
        final long currentTime = System.currentTimeMillis();
        final long expirationTime = currentTime + 7 * 24 * 60 * 60 * 1000; // 7 jours

        final Map<String, Object> claims = new HashMap<>();
        claims.put("id", utilisateur.getId());
        claims.put("nom", utilisateur.getNom());
        claims.put("prenom", utilisateur.getPrenom());
        claims.put("genre", utilisateur.getGenre());
        claims.put("email", utilisateur.getEmail());
        claims.put("phone", utilisateur.getPhone());
        claims.put("role", utilisateur.getRole().getNom());
        claims.put(Claims.EXPIRATION, new Date(expirationTime));
        claims.put(Claims.SUBJECT, utilisateur.getEmail());

        final String bearer = Jwts.builder()
                .setIssuedAt(new Date(currentTime))
                .setExpiration(new Date(expirationTime))
                .setSubject(utilisateur.getEmail())
                .setClaims(claims)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
        Map<String, String> result = new HashMap<>();
        result.put("bearer", bearer);
        return result;
    }

    private Map<String, String> generateRefreshToken(Utilisateur utilisateur) {
        final long currentTime = System.currentTimeMillis();
        final long expirationTime = currentTime + 7 * 24 * 60 * 60 * 1000; // 7 jours

        final Map<String, Object> claims = new HashMap<>();
        claims.put("id", utilisateur.getId());
        claims.put("email", utilisateur.getEmail());
        claims.put("type", "refresh");
        claims.put(Claims.EXPIRATION, new Date(expirationTime));
        claims.put(Claims.SUBJECT, utilisateur.getEmail());

        final String refreshToken = Jwts.builder()
                .setIssuedAt(new Date(currentTime))
                .setExpiration(new Date(expirationTime))
                .setSubject(utilisateur.getEmail())
                .setClaims(claims)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
        Map<String, String> result = new HashMap<>();
        result.put("refreshToken", refreshToken);
        return result;
    }

    private Key getKey() {
        final byte[] decoder = Base64.getDecoder().decode(ENCRIPTION_KEY);
        return Keys.hmacShaKeyFor(decoder);
    }

    public Map<String, String> generateResetPasswordToken(String email) {
        final Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() +7200000)) // Expire dans 2 HEURES
                .signWith(getKey(), SignatureAlgorithm.HS256) // Assurez-vous que la clé correspond à cet algorithme
                .compact();

        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        return result;
    }

    public String getEmailFromToken(String token) {
        // Mesure temporaire : log du token
        System.out.println("Vérification du token : " + token);

        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Le token reçu est vide ou null.");
        }

        Claims claims = this.getAllClaims(token); // Décoder les claims
        return claims.getSubject(); // Extrait l'email ou l'identifiant de l'utilisateur
    }
}
