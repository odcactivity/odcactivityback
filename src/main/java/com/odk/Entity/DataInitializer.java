package com.odk.Entity;

import com.odk.Repository.RoleRepository;
import com.odk.Repository.UtilisateurRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private UtilisateurRepository utilisateurRepository;
    private PasswordEncoder passwordEncoder;
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {

        // Vérifie si le rôle SUPERADMIN existe, sinon le crée
        Role superAdminRole = roleRepository.findByNom("SUPERADMIN")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setNom("SUPERADMIN");
                    return roleRepository.save(newRole);
                });

        // Vérifie si un utilisateur SUPERADMIN existe déjà pour éviter les doublons
        if (utilisateurRepository.findByEmail("admin@gmail.com").isEmpty()) {
            Utilisateur superAdmin = new Utilisateur();
            superAdmin.setNom("admin");
            superAdmin.setPrenom("admin");
            superAdmin.setPhone("78412541");  // Exemple de numéro de téléphone
            superAdmin.setGenre("Homme");  // Exemple de genre (ou "Femme" selon votre besoin)
            superAdmin.setEmail("admin@gmail.com");
            superAdmin.setPassword(passwordEncoder.encode("motdepasse123")); // Mot de passe encodé
            superAdmin.setRole(superAdminRole);

            // Sauvegarde l'utilisateur dans la base de données
            utilisateurRepository.save(superAdmin);

            System.out.println("Utilisateur SUPERADMIN créé avec succès !");
        } else {
            System.out.println("Utilisateur SUPERADMIN existe déjà !");
        }


        // Vérifie si le rôle SUPERADMIN existe, sinon le crée
        Role PersonnelROle = roleRepository.findByNom("PERSONNEL")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setNom("PERSONNEL");
                    return roleRepository.save(newRole);
                });

        // Vérifie si un utilisateur SUPERADMIN existe déjà pour éviter les doublons
        if (utilisateurRepository.findByEmail("madoumadeltitokone77@gmail.com").isEmpty()) {
            Utilisateur personnel = new Utilisateur();
            personnel.setNom("personnel");
            personnel.setPrenom("personnel");
            personnel.setPhone("78412541");  // Exemple de numéro de téléphone
            personnel.setGenre("Homme");  // Exemple de genre (ou "Femme" selon votre besoin)
            personnel.setEmail("madoumadeltitokone77@gmail.com");
            personnel.setPassword(passwordEncoder.encode("motdepasse123")); // Mot de passe encodé
            personnel.setRole(PersonnelROle);
           // personnel.setEntite();

            // Sauvegarde l'utilisateur dans la base de données
            utilisateurRepository.save(personnel);

            System.out.println("Utilisateur Personnel créé avec succès !");
        } else {
            System.out.println("Utilisateur Personnel existe déjà !");
        }

        Role directeurRole = roleRepository.findByNom("DIRECTEUR")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setNom("DIRECTEUR");
                    return roleRepository.save(r);
                });

        // Compte DCIRE (directeur)
        // Migration douce: si l'ancien email existe déjà, on le renomme vers le nouveau.
        Optional<Utilisateur> oldDirecteur = utilisateurRepository.findByEmail("directeur@gmail.com");
        if (oldDirecteur.isPresent() && utilisateurRepository.findByEmail("dcire@gmail.com").isEmpty()) {
            Utilisateur u = oldDirecteur.get();
            u.setEmail("dcire@gmail.com");
            u.setRole(directeurRole);
            utilisateurRepository.save(u);
            System.out.println("Utilisateur DIRECTEUR renommé de directeur@gmail.com vers dcire@gmail.com !");
        }

        if (utilisateurRepository.findByEmail("dcire@gmail.com").isEmpty()) {
            Utilisateur directeur = new Utilisateur();
            directeur.setNom("DCIRE");
            directeur.setPrenom("ODC");
            directeur.setPhone("00000000");
            directeur.setGenre("Homme");
            directeur.setEmail("dcire@gmail.com");
            directeur.setPassword(passwordEncoder.encode("motdepasse123"));
            directeur.setRole(directeurRole);
            utilisateurRepository.save(directeur);
            System.out.println("Utilisateur DIRECTEUR (dcire@gmail.com) créé avec succès !");
        } else {
            System.out.println("Utilisateur DIRECTEUR existe déjà !");
        }

        Role directeurOdcRole = roleRepository.findByNom("DIRECTEUR_ODC")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setNom("DIRECTEUR_ODC");
                    return roleRepository.save(r);
                });

        // Compte métier : notifications nouveaux courriers / activités côté directeur ODC (rôle DIRECTEUR_ODC).
        if (utilisateurRepository.findByEmail("directeurODC@gmail.com").isEmpty()) {
            Utilisateur dirOdc = new Utilisateur();
            dirOdc.setNom("Directeur");
            dirOdc.setPrenom("ODC");
            dirOdc.setPhone("00000000");
            dirOdc.setGenre("Homme");
            dirOdc.setEmail("directeurODC@gmail.com");
            dirOdc.setPassword(passwordEncoder.encode("DirOT2026"));
            dirOdc.setRole(directeurOdcRole);
            utilisateurRepository.save(dirOdc);
            System.out.println("Utilisateur DIRECTEUR_ODC (directeurODC@gmail.com) créé avec succès !");
        } else {
            System.out.println("Utilisateur DIRECTEUR_ODC existe déjà !");
        }

    }
}
