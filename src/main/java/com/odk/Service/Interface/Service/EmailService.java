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
     * @param expediteurLibelle nom affiché (ex. KEÏTA DCIRE, Responsable Multimedia)
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
            String from = defaultFromEmail != null && !defaultFromEmail.isBlank()
                    ? defaultFromEmail.trim()
                    : "noreply@odc.local";
            if (expediteurLibelle != null && !expediteurLibelle.isBlank()) {
                helper.setFrom(from, expediteurLibelle.trim());
            } else {
                helper.setFrom(from);
            }
            if (replyToEmail != null && replyToEmail.contains("@")) {
                helper.setReplyTo(replyToEmail.trim());
            }
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
            String from = defaultFromEmail != null && !defaultFromEmail.isBlank()
                    ? defaultFromEmail.trim()
                    : "noreply@odc.local";
            if (expediteurLibelle != null && !expediteurLibelle.isBlank()) {
                helper.setFrom(from, expediteurLibelle.trim());
            } else {
                helper.setFrom(from);
            }
            if (replyToEmail != null && replyToEmail.contains("@")) {
                helper.setReplyTo(replyToEmail.trim());
            }
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
}
