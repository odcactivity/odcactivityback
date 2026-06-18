package com.odk.Service.Interface.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class EmailService {

    private JavaMailSender mailSender;
    public void sendSimpleEmail(String to, String subject, String body) {
        // Crée un objet MimeMessage pour gérer l'envoi d'un email plus sophistiqué (HTML).
        MimeMessage mimeMessage = mailSender.createMimeMessage();

        try {
            // Utilise MimeMessageHelper pour construire l'e-mail avec prise en charge de HTML.
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(to);  // Définit le destinataire.
            helper.setSubject(subject);  // Définit l'objet de l'e-mail.
            helper.setText(body, true);  // Le paramètre 'true' indique que le corps est en HTML.

            // Envoie l'e-mail.
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // En cas d'erreur, une RuntimeException est levée.
            throw new RuntimeException("Échec de l'envoi de l'email", e);
        }
    }
    public void sendEmailWithAttachments(String to, String subject, String body, java.util.List<java.io.File> files) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            if (files != null) {
                for (java.io.File file : files) {
                    if (file != null && file.exists()) {
                        helper.addAttachment(file.getName(), file);
                    }
                }
            }
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Échec de l'envoi de l'email avec pièces jointes", e);
        }
    }
}
