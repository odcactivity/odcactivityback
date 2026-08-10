package com.odk.Controller;

import com.odk.Entity.Courrier;
import com.odk.Entity.Entite;
import com.odk.Entity.HistoriqueCourrier;
import com.odk.Service.Interface.Service.HistoriqueCourrierService;
import com.odk.dto.HistoriqueCourrierDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/historique")
@RequiredArgsConstructor
public class HistoriqueCourrierController {

    private static final Pattern EMAIL_DELEGATION =
            Pattern.compile("délégué par e-mail à\\s*:?\\s*([^\\s—]+@[^\\s—]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_EMISSION =
            Pattern.compile("(?:vers|à)\\s*:?\\s*([^\\s—]+@[^\\s—]+)", Pattern.CASE_INSENSITIVE);

    private final HistoriqueCourrierService historiqueService;

    @GetMapping("/courrier/{courrierId}")
    public ResponseEntity<List<HistoriqueCourrierDTO>> getHistorique(
            @PathVariable Long courrierId
    ) {
        List<HistoriqueCourrier> historiques =
                historiqueService.getHistoriqueCourrierAutorise(courrierId);

        Courrier courrierRef = historiques.isEmpty() ? null : historiques.get(0).getCourrier();

        List<HistoriqueCourrierDTO> dtos = historiques.stream()
                .map(h -> mapToDto(h, courrierRef))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    private HistoriqueCourrierDTO mapToDto(HistoriqueCourrier h, Courrier courrierRef) {
        HistoriqueCourrierDTO dto = new HistoriqueCourrierDTO();
        dto.setStatut(h.getStatut() != null ? h.getStatut().name() : null);
        dto.setCommentaire(h.getCommentaire());
        dto.setDateAction(h.getDateAction());

        dto.setUtilisateur(
                h.getUtilisateur() != null
                        ? formatNomUtilisateur(h.getUtilisateur().getPrenom(), h.getUtilisateur().getNom())
                        : "Système"
        );

        dto.setEntite(h.getEntite() != null ? h.getEntite().getNom() : null);
        dto.setAncienneEntiteNom(nomEntite(h.getAncienneEntite()));
        dto.setNouvelleEntiteNom(nomEntite(h.getNouvelleEntite()));

        Courrier c = h.getCourrier() != null ? h.getCourrier() : courrierRef;
        if (c != null) {
            dto.setExpediteurCourrier(c.getExpediteur());
            dto.setStructureOrigineNom(nomEntite(c.getStructureOrigine()));
            dto.setEntiteDetentionNom(nomEntite(c.getEntite()));
        }

        String comment = h.getCommentaire() != null ? h.getCommentaire() : "";
        String de = resolveHistoriqueDe(h, c, comment);
        String a = resolveHistoriqueA(h, c, comment);

        dto.setExpediteurCourrier(de);
        if (a != null && !a.isBlank()) {
            dto.setNouvelleEntiteNom(a);
            dto.setEntiteDetentionNom(a);
        }

        return dto;
    }

    private String resolveHistoriqueDe(HistoriqueCourrier h, Courrier c, String comment) {
        if (comment.contains("délégué par e-mail")) {
            String structure = nomEntite(h.getAncienneEntite());
            if (structure != null) {
                return structure;
            }
            if (c != null && c.getEntite() != null && !nomIndiqueDcire(c.getEntite())) {
                return nomEntite(c.getEntite());
            }
        }
        if (comment.contains("Émis par la DCIRE") || comment.contains("expédition KEÏTA")) {
            return "KEÏTA DCIRE";
        }
        if (comment.contains("transmis à la DCIRE") && c != null && c.getStructureOrigine() != null) {
            return nomEntite(c.getStructureOrigine());
        }
        if (comment.contains("Réponse") && comment.contains("DCIRE") && c != null && c.getDirectionInitial() != null) {
            if (estDirectionOdcNom(c.getDirectionInitial())) {
                return nomEntite(c.getDirectionInitial());
            }
        }
        if (nomEntite(h.getAncienneEntite()) != null) {
            return nomEntite(h.getAncienneEntite());
        }
        if (c != null && c.getExpediteur() != null && !c.getExpediteur().isBlank()) {
            return c.getExpediteur().trim();
        }
        return nomEntite(h.getEntite());
    }

    private String resolveHistoriqueA(HistoriqueCourrier h, Courrier c, String comment) {
        Matcher deleg = EMAIL_DELEGATION.matcher(comment);
        if (deleg.find()) {
            return deleg.group(1).trim();
        }
        Matcher email = EMAIL_EMISSION.matcher(comment);
        if (email.find()) {
            return email.group(1).trim();
        }
        if (comment.contains("Émis par la DCIRE") || comment.contains("expédition KEÏTA")) {
            String cible = nomEntite(h.getNouvelleEntite());
            if (cible != null && !nomIndiqueDcireNom(cible)) {
                return cible;
            }
            if (c != null) {
                if (c.getDirectionInitial() != null && !nomIndiqueDcire(c.getDirectionInitial())) {
                    return nomEntite(c.getDirectionInitial());
                }
                if (c.getEntite() != null && !nomIndiqueDcire(c.getEntite())) {
                    return nomEntite(c.getEntite());
                }
            }
        }
        if (comment.contains("transmis à la DCIRE")) {
            return "DCIRE";
        }
        if (nomEntite(h.getNouvelleEntite()) != null) {
            return nomEntite(h.getNouvelleEntite());
        }
        if (c != null && c.getEntite() != null) {
            return nomEntite(c.getEntite());
        }
        return null;
    }

    private static String nomEntite(Entite e) {
        if (e == null || e.getNom() == null || e.getNom().isBlank()) {
            return null;
        }
        return e.getNom().trim();
    }

    private static String formatNomUtilisateur(String prenom, String nom) {
        String p = prenom != null ? prenom.trim() : "";
        String n = nom != null ? nom.trim() : "";
        String full = (p + " " + n).trim();
        return full.isBlank() ? "Système" : full;
    }

    private static boolean nomIndiqueDcire(Entite e) {
        return e != null && nomIndiqueDcireNom(e.getNom());
    }

    private static boolean nomIndiqueDcireNom(String nom) {
        if (nom == null) {
            return false;
        }
        String n = nom.toUpperCase(java.util.Locale.ROOT);
        return n.contains("DCIRE") || n.replace(' ', '-').contains("DCI-RE");
    }

    private static boolean estDirectionOdcNom(Entite e) {
        if (e == null || e.getNom() == null) {
            return false;
        }
        String n = e.getNom().toUpperCase(java.util.Locale.ROOT);
        return n.contains("ORANGE DIGITAL CENTER") || n.contains("ODC");
    }
}
