package com.odk.Service.Interface.Service;

import com.odk.Entity.Courrier;
import com.odk.Repository.CourrierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Service pour gérer les rappels automatiques des courriers avant leur date limite
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourrierRappelService {

    private final CourrierRepository courrierRepository;
    private final EmailService emailService;

    /**
     * Vérifie tous les jours à 8h du matin les courriers qui approchent de leur date limite
     */
    @Scheduled(cron = "0 0 8 * * ?") // Tous les jours à 8h00
    public void verifierRappelsCourriers() {
        log.info("🔔 Début de la vérification des rappels de courriers");
        
        LocalDate aujourdHui = LocalDate.now();
        List<Courrier> courriersARappeler = courrierRepository.findCourriersPourRappel(aujourdHui);
        
        log.info("📊 {} courriers trouvés pour rappel aujourd'hui", courriersARappeler.size());
        
        for (Courrier courrier : courriersARappeler) {
            try {
                envoyerRappelCourrier(courrier);
                marquerRappelEnvoye(courrier);
            } catch (Exception e) {
                log.error("❌ Erreur lors de l'envoi du rappel pour le courrier {}: {}", 
                         courrier.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Fin de la vérification des rappels de courriers");
    }

    /**
     * Envoie un email de rappel pour un courrier
     */
    private void envoyerRappelCourrier(Courrier courrier) {
        // 1. Email au responsable de l'entité
        if (courrier.getEntite().getResponsable() != null && 
            courrier.getEntite().getResponsable().getEmail() != null) {
            
            String emailBody = buildEmailRappelResponsable(courrier);
            emailService.sendSimpleEmail(
                courrier.getEntite().getResponsable().getEmail(),
                "⏰ RAPPEL : Courrier " + courrier.getNumero() + " - Date limite imminente",
                emailBody
            );
        }

        // 2. Email à l'utilisateur affecté
        if (courrier.getUtilisateurAffecte() != null && 
            courrier.getUtilisateurAffecte().getEmail() != null) {
            
            String emailBody = buildEmailRappelUtilisateur(courrier);
            emailService.sendSimpleEmail(
                courrier.getUtilisateurAffecte().getEmail(),
                "⏰ RAPPEL : Courrier " + courrier.getNumero() + " à traiter",
                emailBody
            );
        }

        log.info("📧 Rappel envoyé pour le courrier {} à {}", 
                 courrier.getId(), getCiblesRappel(courrier));
    }

    /**
     * Marque le rappel comme envoyé pour éviter les doublons
     */
    private void marquerRappelEnvoye(Courrier courrier) {
        courrier.setRappelEnvoye(true);
        courrierRepository.save(courrier);
    }

    /**
     * Construit l'email de rappel pour le responsable
     */
    private String buildEmailRappelResponsable(Courrier courrier) {
        long joursRestants = calculerJoursRestants(courrier.getDateLimite());
        NiveauUrgence niveauUrgence = getNiveauUrgence(joursRestants);
        
        return "<!DOCTYPE html><html><body>"
                + "<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5;'>"
                + "<div style='background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-left: 5px solid " + niveauUrgence.getCouleur() + ";'>"
                + "<h2 style='color: " + niveauUrgence.getCouleur() + "; margin-bottom: 20px;'>⏰ RAPPEL URGENT</h2>"
                + "<div style='background-color: " + niveauUrgence.getBgColor() + "; padding: 15px; border-radius: 4px; margin-bottom: 20px;'>"
                + "<p style='margin: 0; font-weight: bold; font-size: 1.1em;'>"
                + "📋 Courrier : " + courrier.getNumero() + "<br>"
                + "📅 Date limite : " + courrier.getDateLimite() + "<br>"
                + "⏳ Jours restants : " + joursRestants + " jour(s)"
                + "</p>"
                + "</div>"
                + "<table style='width: 100%; border-collapse: collapse; margin-bottom: 20px;'>"
                + "<tr style='background-color: #ecf0f1;'><td style='padding: 10px; font-weight: bold;'>Expéditeur :</td><td style='padding: 10px;'>" + courrier.getExpediteur() + "</td></tr>"
                + "<tr><td style='padding: 10px; font-weight: bold;'>Objet :</td><td style='padding: 10px;'>" + courrier.getObjet() + "</td></tr>"
                + "<tr style='background-color: #ecf0f1;'><td style='padding: 10px; font-weight: bold;'>Entité responsable :</td><td style='padding: 10px;'>" + courrier.getEntite().getNom() + "</td></tr>"
                + "<tr><td style='padding: 10px; font-weight: bold;'>Affecté à :</td><td style='padding: 10px;'>" + 
                (courrier.getUtilisateurAffecte() != null ? courrier.getUtilisateurAffecte().getNom() + " " + courrier.getUtilisateurAffecte().getPrenom() : "Non affecté") + "</td></tr>"
                + "</table>"
                + "<div style='background-color: #fff3cd; padding: 15px; border-radius: 4px; border-left: 4px solid #856404;'>"
                + "<h3 style='color: #856404; margin-top: 0;'>📌 Action requise</h3>"
                + "<p style='margin: 0;'>Veuillez vous assurer que ce courrier est traité avant la date limite.</p>"
                + "</div>"
                + "<hr style='margin: 20px 0; border: none; border-top: 1px solid #dee2e6;'>"
                + "<p style='font-size: 0.9em; color: #6c757d; margin: 0;'>"
                + "📧 Cet email est un rappel automatique. "
                + (courrier.isRappelEnvoye() ? "Un premier rappel a déjà été envoyé." : "Premier rappel.")
                + "</p>"
                + "</div></div></body></html>";
    }

    /**
     * Construit l'email de rappel pour l'utilisateur affecté
     */
    private String buildEmailRappelUtilisateur(Courrier courrier) {
        long joursRestants = calculerJoursRestants(courrier.getDateLimite());
        NiveauUrgence niveauUrgence = getNiveauUrgence(joursRestants);
        
        return "<!DOCTYPE html><html><body>"
                + "<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5;'>"
                + "<div style='background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-left: 5px solid " + niveauUrgence.getCouleur() + ";'>"
                + "<h2 style='color: " + niveauUrgence.getCouleur() + "; margin-bottom: 20px;'>⏰ RAPPEL DE TRAITEMENT</h2>"
                + "<div style='background-color: " + niveauUrgence.getBgColor() + "; padding: 15px; border-radius: 4px; margin-bottom: 20px;'>"
                + "<p style='margin: 0; font-weight: bold; font-size: 1.1em;'>"
                + "📋 Courrier : " + courrier.getNumero() + "<br>"
                + "📅 Date limite : " + courrier.getDateLimite() + "<br>"
                + "⏳ Jours restants : " + joursRestants + " jour(s)"
                + "</p>"
                + "</div>"
                + "<div style='background-color: #d1ecf1; padding: 15px; border-radius: 4px; border-left: 4px solid #0c5460;'>"
                + "<h3 style='color: #0c5460; margin-top: 0;'>📝 Détails du courrier</h3>"
                + "<p style='margin: 5px 0;'><strong>Expéditeur :</strong> " + courrier.getExpediteur() + "</p>"
                + "<p style='margin: 5px 0;'><strong>Objet :</strong> " + courrier.getObjet() + "</p>"
                + "<p style='margin: 5px 0;'><strong>Entité :</strong> " + courrier.getEntite().getNom() + "</p>"
                + "</div>"
                + "<div style='background-color: #fff3cd; padding: 15px; border-radius: 4px; border-left: 4px solid #856404;'>"
                + "<h3 style='color: #856404; margin-top: 0;'>⚡ Action immédiate requise</h3>"
                + "<p style='margin: 0;'>Merci de traiter ce courrier dans les plus brefs délais.</p>"
                + "</div>"
                + "<hr style='margin: 20px 0; border: none; border-top: 1px solid #dee2e6;'>"
                + "<p style='font-size: 0.9em; color: #6c757d; margin: 0;'>"
                + "📧 Ceci est un rappel automatique. Veuillez contacter votre responsable si besoin."
                + "</p>"
                + "</div></div></body></html>";
    }

    /**
     * Calcule le nombre de jours restants avant la date limite
     */
    private long calculerJoursRestants(Date dateLimite) {
        LocalDate dateLimiteLocal = dateLimite.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate aujourdHui = LocalDate.now();
        return ChronoUnit.DAYS.between(aujourdHui, dateLimiteLocal);
    }

    /**
     * Détermine le niveau d'urgence selon les jours restants
     */
    private NiveauUrgence getNiveauUrgence(long joursRestants) {
        if (joursRestants <= 0) {
            return new NiveauUrgence("#dc3545", "#f8d7da", "URGENT - Dépassé");
        } else if (joursRestants <= 2) {
            return new NiveauUrgence("#dc3545", "#f8d7da", "URGENT - Moins de 3 jours");
        } else if (joursRestants <= 5) {
            return new NiveauUrgence("#fd7e14", "#fff3cd", "IMPORTANT - Moins d'une semaine");
        } else {
            return new NiveauUrgence("#0c5460", "#d1ecf1", "INFO - Plus d'une semaine");
        }
    }

    /**
     * Retourne la liste des destinataires du rappel pour les logs
     */
    private String getCiblesRappel(Courrier courrier) {
        StringBuilder cibles = new StringBuilder();
        
        if (courrier.getEntite().getResponsable() != null) {
            cibles.append("Responsable(").append(courrier.getEntite().getResponsable().getEmail()).append(")");
        }
        
        if (courrier.getUtilisateurAffecte() != null) {
            if (cibles.length() > 0) cibles.append(", ");
            cibles.append("Utilisateur(").append(courrier.getUtilisateurAffecte().getEmail()).append(")");
        }
        
        return cibles.toString();
    }

    /**
     * Classe pour représenter le niveau d'urgence
     */
    private static class NiveauUrgence {
        private final String couleur;
        private final String bgColor;
        private final String libelle;

        public NiveauUrgence(String couleur, String bgColor, String libelle) {
            this.couleur = couleur;
            this.bgColor = bgColor;
            this.libelle = libelle;
        }

        public String getCouleur() { return couleur; }
        public String getBgColor() { return bgColor; }
        public String getLibelle() { return libelle; }
    }
}
