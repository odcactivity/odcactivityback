package com.odk.Service.Interface.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorage {

    private static final String IMAGE_DIR = "images/personnels/";


    public String saveImage(MultipartFile fichier) throws IOException {
        if (fichier == null || fichier.isEmpty()) throw new IOException("Fichier vide");

        Path dossierPath = Paths.get(IMAGE_DIR);
        if (!Files.exists(dossierPath)) Files.createDirectories(dossierPath);

        String originalName = fichier.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) throw new IOException("Nom de fichier invalide");

        String extension = getFileExtension(originalName);
        if (!extension.matches("(?i)jpg|jpeg|png")) throw new IOException("Format d'image non supporté");

        String fileName = UUID.randomUUID() + "." + extension;
        Path filePath = dossierPath.resolve(fileName);

        Files.copy(fichier.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return IMAGE_DIR + "/" + fileName;
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1) throw new IllegalArgumentException("Pas d'extension");
        return filename.substring(dot + 1);
    }
}
