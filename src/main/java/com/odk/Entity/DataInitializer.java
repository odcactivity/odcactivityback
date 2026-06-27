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

/**
 * Initialise uniquement les rôles et comptes manquants.
 * Ne réécrit jamais mot de passe / rôle / email des comptes déjà présents
 * (modifications admin ou profil conservées après redémarrage).
 */
@Component
@AllArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String[] ROLES_APPLICATION = {
            "SUPERADMIN", "ADMIN", "PERSONNEL", "DIRECTEUR", "DCIRE", "DIRECTEUR_ODC",
            "RESPONSABLE_ODK", "RESPONSABLE_FABLAB", "RESPONSABLE_OFAB", "RESPONSABLE_MULTIMEDIA",
            "DIRECTEUR_FONDATION", "DIRECTEUR_RSE", "DIRECTEUR_DCI"
    };

    private UtilisateurRepository utilisateurRepository;
    private PasswordEncoder passwordEncoder;
    private RoleRepository roleRepository;
    private EntiteOdcRepository entiteOdcRepository;

    @Override
    public void run(String... args) {
        for (String roleNom : ROLES_APPLICATION) {
            assurerRole(roleNom);
        }

        Role superAdminRole = roleRepository.findByNom("SUPERADMIN").orElseThrow();

        creerUtilisateurSiAbsent(
                "admin@gmail.com",
                "admin",
                "admin",
                "78412541",
                superAdminRole,
                "motdepasse123",
                null,
                "SUPERADMIN");

        Role personnelRole = roleRepository.findByNom("PERSONNEL").orElseThrow();
        creerUtilisateurSiAbsent(
                "madoumadeltitokone77@gmail.com",
                "personnel",
                "personnel",
                "78412541",
                personnelRole,
                "motdepasse123",
                null,
                "PERSONNEL");

        Role directeurRole = roleRepository.findByNom("DIRECTEUR").orElseThrow();
        migrerEmailDirecteurLegacy();
        Entite entiteDcire = assurerEntiteDirectionDcire();

        creerUtilisateurSiAbsent(
                "dcire@gmail.com",
                "DCIRE",
                "ODC",
                "00000000",
                directeurRole,
                "motdepasse123",
                entiteDcire,
                "DIRECTEUR (DCIRE)");
        utilisateurRepository.findByEmail("dcire@gmail.com").ifPresent(u -> {
            if (u.getEntite() == null) {
                u.setEntite(entiteDcire);
                utilisateurRepository.save(u);
            }
        });

        Role directeurOdcRole = roleRepository.findByNom("DIRECTEUR_ODC").orElseThrow();
        creerUtilisateurSiAbsent(
                "directeurODC@gmail.com",
                "Directeur",
                "ODC",
                "00000000",
                directeurOdcRole,
                "DirOT2026",
                null,
                "DIRECTEUR_ODC");

        Role responsableOdkRole = roleRepository.findByNom("RESPONSABLE_ODK").orElseThrow();
        creerUtilisateurSiAbsent(
                "orangekalanso@gmail.com",
                "Responsable",
                "ODK",
                "00000000",
                responsableOdkRole,
                "motdepasse123",
                null,
                "RESPONSABLE_ODK");

        creerDirecteurStructure(
                "DIRECTEUR_FONDATION", "directeurFondation@gmail.com", "Fondation", "FONDATION");
        creerDirecteurStructure(
                "DIRECTEUR_RSE", "directeurRSE@gmail.com", "RSE", "RSE");
        creerDirecteurStructure(
                "DIRECTEUR_DCI", "directeurDCI@gmail.com", "DCI", "DCI");
    }

    private Role assurerRole(String roleNom) {
        return roleRepository.findByNom(roleNom).orElseGet(() -> {
            Role r = new Role();
            r.setNom(roleNom);
            Role saved = roleRepository.save(r);
            System.out.println("Rôle créé : " + roleNom);
            return saved;
        });
    }

    private void creerUtilisateurSiAbsent(
            String email,
            String nom,
            String prenom,
            String phone,
            Role role,
            String motDePasseInitial,
            Entite entite,
            String libelleLog) {
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            System.out.println("Compte " + libelleLog + " déjà présent (" + email + ") — conservé tel quel.");
            return;
        }
        Utilisateur u = new Utilisateur();
        u.setNom(nom);
        u.setPrenom(prenom);
        u.setPhone(phone);
        u.setGenre("Homme");
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(motDePasseInitial));
        u.setRole(role);
        u.setEntite(entite);
        u.setEtat(true);
        utilisateurRepository.save(u);
        System.out.println("Compte " + libelleLog + " créé : " + email + " / " + motDePasseInitial);
    }

    private void migrerEmailDirecteurLegacy() {
        Optional<Utilisateur> oldDirecteur = utilisateurRepository.findByEmail("directeur@gmail.com");
        if (oldDirecteur.isPresent() && utilisateurRepository.findByEmail("dcire@gmail.com").isEmpty()) {
            Utilisateur u = oldDirecteur.get();
            u.setEmail("dcire@gmail.com");
            roleRepository.findByNom("DIRECTEUR").ifPresent(u::setRole);
            utilisateurRepository.save(u);
            System.out.println("Migration : directeur@gmail.com → dcire@gmail.com");
        }
    }

    private void creerDirecteurStructure(
            String roleNom, String email, String prenom, String motClefDirection) {
        Role role = roleRepository.findByNom(roleNom).orElseThrow();
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            System.out.println("Compte " + roleNom + " déjà présent (" + email + ").");
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
        u.setEtat(true);
        entOpt.ifPresent(u::setEntite);
        utilisateurRepository.save(u);
        System.out.println("Compte " + roleNom + " créé : " + email + " / motdepasse123");
    }

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
        return switch (motClefDirection) {
            case "FONDATION" -> n.contains("FONDATION");
            case "RSE" -> n.contains("RSE") || (n.contains("RESPONSABIL") && n.contains("SOCIALE"));
            case "DCI" -> n.contains("DCI") && !n.contains("DCIRE");
            default -> false;
        };
    }
}
