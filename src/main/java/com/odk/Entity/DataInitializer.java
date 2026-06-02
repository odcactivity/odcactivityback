package com.odk.Entity;

import com.odk.Enum.TypeEntite;
import com.odk.Repository.EntiteOdcRepository;
import com.odk.Repository.RoleRepository;
import com.odk.Repository.UtilisateurRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@AllArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private UtilisateurRepository utilisateurRepository;
    private PasswordEncoder passwordEncoder;
    private RoleRepository roleRepository;
    private EntiteOdcRepository entiteOdcRepository;

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

        Entite entiteDcire = assurerEntiteDirectionDcire();

        if (utilisateurRepository.findByEmail("dcire@gmail.com").isEmpty()) {
            Utilisateur directeur = new Utilisateur();
            directeur.setNom("DCIRE");
            directeur.setPrenom("ODC");
            directeur.setPhone("00000000");
            directeur.setGenre("Homme");
            directeur.setEmail("dcire@gmail.com");
            directeur.setPassword(passwordEncoder.encode("motdepasse123"));
            directeur.setRole(directeurRole);
            directeur.setEntite(entiteDcire);
            utilisateurRepository.save(directeur);
            System.out.println("Utilisateur DIRECTEUR (dcire@gmail.com) créé avec succès !");
        } else {
            utilisateurRepository.findByEmail("dcire@gmail.com").ifPresent(u -> {
                if (u.getEntite() == null) {
                    u.setEntite(entiteDcire);
                    utilisateurRepository.save(u);
                    System.out.println("Compte dcire@gmail.com rattaché à l'entité DCIRE (id=" + entiteDcire.getId() + ").");
                }
            });
            System.out.println("Utilisateur DIRECTEUR existe déjà !");
        }

        Role directeurOdcRole = roleRepository.findByNom("DIRECTEUR_ODC")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setNom("DIRECTEUR_ODC");
                    return roleRepository.save(r);
                });

        // Compte métier : notifications nouveaux courriers / activités côté directeur ODC (rôle DIRECTEUR_ODC).
        // Forçage au démarrage pour éviter les 401 si le compte a été modifié manuellement en base.
        Utilisateur dirOdc = utilisateurRepository.findByEmail("directeurODC@gmail.com")
                .orElseGet(() -> {
                    Utilisateur u = new Utilisateur();
                    u.setNom("Directeur");
                    u.setPrenom("ODC");
                    u.setPhone("00000000");
                    u.setGenre("Homme");
                    u.setEmail("directeurODC@gmail.com");
                    return u;
                });
        dirOdc.setRole(directeurOdcRole);
        dirOdc.setPassword(passwordEncoder.encode("DirOT2026"));
        dirOdc.setEtat(true);
        utilisateurRepository.save(dirOdc);
        System.out.println("Compte DIRECTEUR_ODC synchronisé: directeurODC@gmail.com / DirOT2026");

        Role responsableOdkRole = roleRepository.findByNom("RESPONSABLE_ODK")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setNom("RESPONSABLE_ODK");
                    return roleRepository.save(r);
                });
        if (utilisateurRepository.findByEmail("orangekalanso@gmail.com").isEmpty()) {
            Utilisateur resp = new Utilisateur();
            resp.setNom("Responsable");
            resp.setPrenom("ODK");
            resp.setPhone("00000000");
            resp.setGenre("Homme");
            resp.setEmail("orangekalanso@gmail.com");
            resp.setPassword(passwordEncoder.encode("motdepasse123"));
            resp.setRole(responsableOdkRole);
            resp.setEtat(true);
            utilisateurRepository.save(resp);
            System.out.println("Compte RESPONSABLE_ODK créé : orangekalanso@gmail.com / motdepasse123");
        } else {
            utilisateurRepository.findByEmail("orangekalanso@gmail.com").ifPresent(u -> {
                u.setRole(responsableOdkRole);
                u.setPassword(passwordEncoder.encode("motdepasse123"));
                u.setEtat(true);
                utilisateurRepository.save(u);
                System.out.println("Compte RESPONSABLE_ODK synchronisé : orangekalanso@gmail.com");
            });
        }

        creerDirecteurStructure(
                "DIRECTEUR_FONDATION", "directeurFondation@gmail.com", "Fondation", "FONDATION");
        creerDirecteurStructure(
                "DIRECTEUR_RSE", "directeurRSE@gmail.com", "RSE", "RSE");
        creerDirecteurStructure(
                "DIRECTEUR_DCI", "directeurDCI@gmail.com", "DCI", "DCI");
    }

    /**
     * Associe une direction existante si le nom matche ; sinon crée quand même le compte (entité null)
     * pour permettre la connexion après reset BDD — rattachement entité possible depuis l’admin.
     */
    private void creerDirecteurStructure(
            String roleNom, String email, String prenom, String motClefDirection) {
        Role role = roleRepository
                .findByNom(roleNom)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setNom(roleNom);
                    return roleRepository.save(r);
                });
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            System.out.println("Utilisateur " + roleNom + " existe déjà !");
            return;
        }
        Optional<Entite> entOpt = entiteOdcRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(e -> directionMatcheStructure(e, motClefDirection))
                .findFirst();
        Utilisateur u = new Utilisateur();
        u.setNom(roleNom.replace("DIRECTEUR_", ""));
        u.setPrenom(prenom);
        u.setPhone("00000000");
        u.setGenre("Homme");
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("motdepasse123"));
        u.setRole(role);
        if (entOpt.isPresent()) {
            u.setEntite(entOpt.get());
            System.out.println("Utilisateur " + roleNom + " (" + email + ") créé — entité : " + entOpt.get().getNom());
        } else {
            System.out.println(
                    "Init : aucune direction « "
                            + motClefDirection
                            + " » — compte "
                            + email
                            + " créé sans entité (rattacher une direction en admin si besoin).");
        }
        utilisateurRepository.save(u);
    }

    /** Direction hub DCIRE (émission courriers) — créée si absente. */
    private Entite assurerEntiteDirectionDcire() {
        Optional<Entite> existante = entiteOdcRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(DataInitializer::nomIndiqueEntiteDcire)
                .findFirst();
        if (existante.isPresent()) {
            return existante.get();
        }
        Entite hub = new Entite();
        hub.setNom("DCIRE");
        hub.setType(TypeEntite.DIRECTION);
        hub.setDescription("Direction hub — émission des courriers division");
        Entite saved = entiteOdcRepository.save(hub);
        System.out.println("Entité direction DCIRE créée (id=" + saved.getId() + ").");
        return saved;
    }

    private static boolean nomIndiqueEntiteDcire(Entite e) {
        if (e == null || e.getNom() == null) {
            return false;
        }
        String n = e.getNom().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return n.contains("DCIRE") || n.contains("DCI RE");
    }

    private static boolean directionMatcheStructure(Entite e, String motClefDirection) {
        String n = e.getNom() != null ? e.getNom().toUpperCase().replaceAll("\\s+", " ").trim() : "";
        if (n.contains("DCIRE")) {
            return false;
        }
        if ("FONDATION".equals(motClefDirection)) {
            return n.contains("FONDATION");
        }
        if ("RSE".equals(motClefDirection)) {
            return n.contains("RSE")
                    || (n.contains("RESPONSABIL") && n.contains("SOCIALE"));
        }
        if ("DCI".equals(motClefDirection)) {
            return n.contains("DCI") && !n.contains("DCIRE");
        }
        return false;
    }
}
