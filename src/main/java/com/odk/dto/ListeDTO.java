package com.odk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.odk.Entity.Etape;
import com.odk.Entity.Liste;
import com.odk.Entity.Participant;
import lombok.Data;


import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListeDTO {
    private Long id;
    @JsonProperty("dateHeure")
    private LocalDateTime dateHeure;
    private boolean listeDebut;
    private boolean listeResultat;
    private EtapeDTO etape;    
    private List<ParticipantDTO> participants;
    

    // Constructeur pour mapper Liste -> ListeDTO
    public ListeDTO(ListeDTO liste) {
        this.id = liste.getId();
        this.dateHeure = liste.getDateHeure();
        this.listeDebut = liste.isListeDebut();
        this.listeResultat = liste.isListeResultat();
        this.etape = liste.getEtape();
        this.participants=liste.getParticipants();
    }

}
