package com.odk.securityConfig;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableMethodSecurity
@AllArgsConstructor
public class Security {

    private final JwtFilter jwtFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors().and() // Active CORS avec notre bean CorsConfigurationSource
                .csrf().disable()
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/activitevalidation/**").permitAll()  // Autoriser les routes d'authentification
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/utilisateur/modifierMotDePasse").authenticated()
                        .requestMatchers("/role/**").hasRole("SUPERADMIN")
                        .requestMatchers("/entites/**").permitAll()
                        .requestMatchers("/reportinghebdo/**").permitAll()
                        .requestMatchers("/reporting/**").permitAll()
                        //Endpoints supportActivite ......
                        .requestMatchers("/api/supports").hasAnyRole("PERSONNEL","SUPERADMIN") //Get all...
                        .requestMatchers("/api/supports/**").hasAnyRole("PERSONNEL","SUPERADMIN") //Get by id, POST, PUT, DELETE...
                        //Acces Endpoints Fichiers&Tailles ...
                        .requestMatchers("/api/stats").hasAnyRole("PERSONNEL","SUPERADMIN")  //Get All ...
                        .requestMatchers("/api/stats/**").hasAnyRole("PERSONNEL","SUPERADMIN") //Get by id, POST, PUT, DELETE ...
                        //Acces Endpoints Fichiers&Tailles ...
                        .requestMatchers(HttpMethod.PUT, "/api/courriers/**").hasAnyRole("PERSONNEL","SUPERADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/historique/**").hasAnyRole("PERSONNEL","SUPERADMIN")
                        .requestMatchers("/utilisateur/modifierMotDePasse").authenticated()
                        .requestMatchers("/role/**").hasRole("SUPERADMIN")

                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // --- CORS Configuration pour Spring Security ---
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",   // ton front Angular
                "http://localhost:60409",  // le port que tu utilises
                "https://odc-web-6afd.onrender.com",

                "http://hebergement-odc-activite-front.s3-website-us-east-1.amazonaws.com"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true); // important si tu utilises des cookies ou tokens
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
