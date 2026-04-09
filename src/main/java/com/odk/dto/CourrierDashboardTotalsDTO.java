package com.odk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourrierDashboardTotalsDTO {
    private long emis;
    private long repondu;
    private long enAttente;
    private long recu;
    private long valide;
}
