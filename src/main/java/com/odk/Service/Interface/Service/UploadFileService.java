package com.odk.Service.Interface.Service;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Calendar;

@Service
public class UploadFileService {

    @Autowired
    private S3Client s3Client;

    private final String bucketName = "odc-activite-assets";
    private static final String REGION = "us-east-1";
    private static final String LOGOS_FOLDER = "personnels";

    /**
     * Upload un logo d'entité vers S3 et retourne l'URL publique.
     * Les images sont stockées dans le bucket, pas sur le disque du serveur (persistant, pas de perte au redéploiement).
     */
    public String uploadLogoAndReturnUrl(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IOException("Fichier logo vide");
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !extension.matches("(?i)jpg|jpeg|png")) throw new IOException("Format d'image non supporté (jpg, png)");
        String filename = Calendar.getInstance().getTimeInMillis() + "." + extension;
        String key = LOGOS_FOLDER + "/" + filename;
        try (InputStream is = file.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(is, file.getSize()));
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, REGION, key);
    }

    public String uploadFile(MultipartFile file, String folderName) throws IOException {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String filename = Calendar.getInstance().getTimeInMillis() + "." + extension;
        String key = folderName + "/" + filename;

        try (InputStream is = file.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(is, file.getSize()));
        }
        return filename;
    }

    public String uploadInputStream(InputStream inputStream, String folderName, String fileName, long contentLength, String contentType) throws IOException {
        String key = folderName + "/" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

        return fileName;
    }

    public String uploadBase64File(String base64File, String folderName, String fileType) throws IOException {
        String filename = Calendar.getInstance().getTimeInMillis() + "." + fileType;

        if (base64File.contains(",")) {
            base64File = base64File.split(",")[1];
        }

        byte[] bytes = Base64.getDecoder().decode(base64File);

        try (InputStream is = new ByteArrayInputStream(bytes)) {
            uploadInputStream(is, folderName, filename, bytes.length, "image/" + fileType);
        }

        return filename;
    }

    public byte[] getFileBytesFromS3(String s3Key) {
        try {
            if (s3Key == null || s3Key.isBlank()) return null;

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return objectBytes.asByteArray();

        } catch (S3Exception e) {
            return null;
        }
    }
}