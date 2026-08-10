package com.odk.Service.Interface.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String defaultFromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendSimpleEmail(String to, String subject, String body) {
        sendSimpleEmail(to, subject, body, null, null);
    }

    /**
     * @param expediteurLibelle nom affiché (ex. KEÏTA DCIRE, Responsable Multimedia) — jamais une adresse brute
     * @param replyToEmail      adresse de réponse (ex. email du responsable connecté)
     */
    public void sendSimpleEmail(String to, String subject, String body,
                                String expediteurLibelle, String replyToEmail) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            applyFromAndReplyTo(helper, expediteurLibelle, replyToEmail);
            mailSender.send(mimeMessage);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Échec de l'envoi de l'email", e);
        }
    }

    public void sendEmailWithAttachments(String to, String subject, String body, java.util.List<java.io.File> files) {
        sendEmailWithAttachments(to, subject, body, files, null, null);
    }

    public void sendEmailWithAttachments(String to, String subject, String body, java.util.List<java.io.File> files,
                                         String expediteurLibelle, String replyToEmail) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            applyFromAndReplyTo(helper, expediteurLibelle, replyToEmail);
            if (files != null) {
                for (java.io.File file : files) {
                    if (file != null && file.exists()) {
                        helper.addAttachment(file.getName(), file);
                    }
                }
            }
            mailSender.send(mimeMessage);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Échec de l'envoi de l'email avec pièces jointes", e);
        }
    }

    /**
     * Nom affiché dans le client mail : on n'utilise jamais une adresse e-mail brute comme libellé
     * (sinon le client affiche « email@x.com &lt;smtp@gmail.com&gt; »).
     */
    public static String resolveDisplayName(String expediteurSaisi, String fallbackLibelle) {
        if (expediteurSaisi != null && !expediteurSaisi.isBlank() && !expediteurSaisi.contains("@")) {
            return expediteurSaisi.trim();
        }
        if (fallbackLibelle != null && !fallbackLibelle.isBlank() && !fallbackLibelle.contains("@")) {
            return fallbackLibelle.trim();
        }
        return "Orange Digital Center";
    }

    /** Adresse de réponse : priorité à l'email saisi dans le formulaire, sinon email de l'auteur connecté. */
    public static String resolveReplyTo(String expediteurSaisi, String auteurEmail) {
        if (expediteurSaisi != null && expediteurSaisi.contains("@")) {
            return expediteurSaisi.trim();
        }
        if (auteurEmail != null && auteurEmail.contains("@")) {
            return auteurEmail.trim();
        }
        return null;
    }

    private void applyFromAndReplyTo(MimeMessageHelper helper, String expediteurLibelle, String replyToEmail)
            throws MessagingException, java.io.UnsupportedEncodingException {
        String from = defaultFromEmail != null && !defaultFromEmail.isBlank()
                ? defaultFromEmail.trim()
                : "noreply@odc.local";
        String displayName = resolveDisplayName(expediteurLibelle, null);
        helper.setFrom(from, displayName);
        String replyTo = resolveReplyTo(expediteurLibelle, replyToEmail);
        if (replyTo != null) {
            helper.setReplyTo(replyTo);
        } else if (replyToEmail != null && replyToEmail.contains("@")) {
            helper.setReplyTo(replyToEmail.trim());
        }
    }
}
