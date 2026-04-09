package com.odk.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourrierDashboardBucketDTO {
    private String label;
    private String debut;
    private String fin;
    private long emis;
    private long repondu;
    private long enAttente;
    private long recu;
    private long valide;
    private List<CourrierDashboardDetailRowDTO> details = new ArrayList<>();
}
