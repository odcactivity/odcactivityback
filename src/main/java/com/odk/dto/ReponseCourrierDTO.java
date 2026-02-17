package com.odk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReponseCourrierDTO {
    private Long courrierId;
    private String email;
    private String objet;
    private String message;
    private MultipartFile file;
    private List<MultipartFile> attachments;
}
