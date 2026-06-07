package com.odk.Service.Interface.Service;

import com.odk.Entity.Entite;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.TypeEntite;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rôles responsables d'entité ODC (Kalanso, FabLab, Orange Fab, Multimedia).
 */
public final class ResponsableEntiteSupport {

    public static final String ROLE_ODK = "RESPONSABLE_ODK";
    public static final String ROLE_FABLAB = "RESPONSABLE_FABLAB";
    public static final String ROLE_OFAB = "RESPONSABLE_OFAB";
    public static final String ROLE_MULTIMEDIA = "RESPONSABLE_MULTIMEDIA";

    public static final Set<String> ROLES_SERVICE = Set.of(
            ROLE_ODK, ROLE_FABLAB, ROLE_OFAB, ROLE_MULTIMEDIA);

    private ResponsableEntiteSupport() {
    }

    public static boolean estRoleResponsableEntite(String roleNom) {
        if (roleNom == null || roleNom.isBlank()) {
            return false;
        }
        return ROLES_SERVICE.contains(roleNom.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean estUtilisateurResponsableEntite(Utilisateur u) {
        return u != null && u.getRole() != null && estRoleResponsableEntite(u.getRole().getNom());
    }

    public static String roleNom(Utilisateur u) {
        if (u == null || u.getRole() == null || u.getRole().getNom() == null) {
            return "";
        }
        return u.getRole().getNom().trim().toUpperCase(Locale.ROOT);
    }

    public static void assertRoleResponsableEntite(Utilisateur u) {
        if (!estUtilisateurResponsableEntite(u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Action réservée aux responsables d'entité ODC.");
        }
    }

    /** Entités dont le nom correspond au rôle responsable (matching souple). */
    public static boolean entiteCorrespondAuRole(Entite entite, String roleNom) {
        if (entite == null || entite.getNom() == null || roleNom == null) {
            return false;
        }
        String n = normalizeNomEntite(entite.getNom());
        String role = roleNom.trim().toUpperCase(Locale.ROOT);
        return switch (role) {
            case ROLE_ODK -> n.contains("KALANSO");
            case ROLE_FABLAB -> n.contains("FABLAB") || n.contains("FAB LAB");
            case ROLE_OFAB -> n.contains("ORANGE FAB")
                    || n.contains("OFAB")
                    || (n.contains("FAB") && !n.contains("FABLAB") && !n.contains("FAB LAB"));
            case ROLE_MULTIMEDIA -> n.contains("MULTIMEDIA");
            default -> false;
        };
    }

    public static boolean entiteCorrespondAuRoleUtilisateur(Entite entite, Utilisateur u) {
        return entiteCorrespondAuRole(entite, roleNom(u));
    }

    /** Rôle attendu pour une activité selon son entité (null si non reconnue). */
    public static String rolePourEntiteActivite(Entite entite) {
        if (entite == null || entite.getNom() == null) {
            return ROLE_ODK;
        }
        if (entiteCorrespondAuRole(entite, ROLE_FABLAB)) {
            return ROLE_FABLAB;
        }
        if (entiteCorrespondAuRole(entite, ROLE_OFAB)) {
            return ROLE_OFAB;
        }
        if (entiteCorrespondAuRole(entite, ROLE_MULTIMEDIA)) {
            return ROLE_MULTIMEDIA;
        }
        return ROLE_ODK;
    }

    public static String libelleRole(String roleNom) {
        if (roleNom == null) {
            return "Responsable";
        }
        return switch (roleNom.trim().toUpperCase(Locale.ROOT)) {
            case ROLE_ODK -> "Responsable ODK (Kalanso)";
            case ROLE_FABLAB -> "Responsable FabLab";
            case ROLE_OFAB -> "Responsable Orange Fab";
            case ROLE_MULTIMEDIA -> "Responsable Multimedia";
            default -> "Responsable";
        };
    }

    public static String cheminDashboardFrontend(String roleNom) {
        return switch (roleNom == null ? "" : roleNom.trim().toUpperCase(Locale.ROOT)) {
            case ROLE_FABLAB -> "/responsable-fablab/dashboard";
            case ROLE_OFAB -> "/responsable-ofab/dashboard";
            case ROLE_MULTIMEDIA -> "/responsable-multimedia/dashboard";
            default -> "/responsable-odk/dashboard";
        };
    }

    /** IDs d'entités rattachées au responsable (entité utilisateur + services enfants éventuels). */
    public static boolean courrierVisiblePourResponsable(
            com.odk.Entity.Courrier courrier, Utilisateur responsable, List<Entite> entitesMemeRole) {
        if (courrier == null || responsable == null) {
            return false;
        }
        if (courrier.isDelegueResponsableOdk()
                && ROLE_ODK.equals(roleNom(responsable))) {
            return true;
        }
        Entite detenteur = courrier.getEntite();
        Entite service = courrier.getServiceOdcAffecte();
        if (detenteur != null && entiteCorrespondAuRoleUtilisateur(detenteur, responsable)) {
            return true;
        }
        if (service != null && entiteCorrespondAuRoleUtilisateur(service, responsable)) {
            return true;
        }
        if (responsable.getEntite() != null) {
            Long userEntiteId = responsable.getEntite().getId();
            if (userEntiteId != null) {
                if (detenteur != null && userEntiteId.equals(detenteur.getId())) {
                    return true;
                }
                if (service != null && userEntiteId.equals(service.getId())) {
                    return true;
                }
            }
        }
        if (entitesMemeRole != null) {
            for (Entite e : entitesMemeRole) {
                if (e == null || e.getId() == null) {
                    continue;
                }
                if (detenteur != null && e.getId().equals(detenteur.getId())) {
                    return true;
                }
                if (service != null && e.getId().equals(service.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean estServiceOdcDivision(Entite e) {
        if (e == null || e.getNom() == null) {
            return false;
        }
        String n = normalizeNomEntite(e.getNom());
        return n.contains("KALANSO")
                || n.contains("FABLAB")
                || n.contains("FAB LAB")
                || n.contains("MULTIMEDIA")
                || n.contains("ORANGE FAB")
                || n.contains("OFAB")
                || (n.contains("FAB") && !n.contains("FABLAB") && !n.contains("FAB LAB"));
    }

    public static String normalizeNomEntite(String nom) {
        if (nom == null) {
            return "";
        }
        return nom.trim().toUpperCase(Locale.ROOT)
                .replace("É", "E")
                .replace("È", "E")
                .replace("Ê", "E")
                .replace("À", "A")
                .replace("Ô", "O")
                .replace("Û", "U")
                .replace("Î", "I")
                .replace("Ç", "C");
    }

    public static boolean utilisateurPeutArchiverCourrier(
            com.odk.Entity.Courrier courrier, Utilisateur u, List<Entite> entitesRole) {
        if (courrier == null || !estUtilisateurResponsableEntite(u)) {
            return false;
        }
        if (courrier.getStatut() == com.odk.Enum.StatutCourrier.ARCHIVER) {
            return false;
        }
        return courrierVisiblePourResponsable(courrier, u, entitesRole);
    }

    public static boolean estDirectionOdc(Entite e) {
        if (e == null || e.getType() != TypeEntite.DIRECTION || e.getNom() == null) {
            return false;
        }
        String n = normalizeNomEntite(e.getNom());
        return n.contains("ORANGE DIGITAL") || n.contains("ODC") || n.contains("KALANSO");
    }
}
