package com.odk.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ParticipantDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String phone;
    private String genre;
    private ActiviteDTO activite; // Ajoutez le nom de l'activité
//    private Long activite; // Ajoutez le nom de l'activité
    private boolean checkedIn;
    private LocalDateTime checkInTime;
//    private ListeDTO liste;
     private Long liste;
     

    public ParticipantDTO(Long id, String nom, String prenom, String email, String phone, String genre, boolean checkedIn, LocalDateTime checkInTime) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.phone = phone;
        this.genre = genre;
        this.activite = getActivite();
        this.checkedIn = checkedIn;
        this.checkInTime = checkInTime;
    }

    public ParticipantDTO(ParticipantDTO participant) {
        this.id = participant.getId();
        this.nom = participant.getNom();
        this.prenom = participant.getPrenom();
        this.email = participant.getEmail();
        this.phone = participant.getPhone();
        this.genre = participant.getGenre();
        this.activite = participant.getActivite();
        this.checkedIn = participant.isCheckedIn();
        this.checkInTime = participant.getCheckInTime();
        this.liste= participant.getListe();

    }

    public ParticipantDTO(Long id, String nom) {
        this.id = id;
        this.nom = nom;
    }
}
