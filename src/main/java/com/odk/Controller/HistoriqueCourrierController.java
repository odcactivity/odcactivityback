package com.odk.Controller;

import com.odk.Entity.HistoriqueCourrier;
import com.odk.Service.Interface.Service.HistoriqueCourrierService;
import com.odk.dto.HistoriqueCourrierDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/historique")
@RequiredArgsConstructor
public class HistoriqueCourrierController {

    private final HistoriqueCourrierService historiqueService;

    @GetMapping("/courrier/{courrierId}")
    public ResponseEntity<List<HistoriqueCourrierDTO>> getHistorique(
            @PathVariable Long courrierId
        //     @AuthenticationPrincipal Long utilisateurId
    ) {
        System.err.println("------=------->"+courrierId);
        // System.err.println("-----utilisateurId-=------->"+utilisateurId);
        List<HistoriqueCourrier> historiques =
                historiqueService.getHistoriqueCourrierAutorise(
                        courrierId);

        List<HistoriqueCourrierDTO> dtos = historiques.stream()
                .map(this::mapToDto)
                .toList();

        return ResponseEntity.ok(dtos);
    }
    private HistoriqueCourrierDTO mapToDto(HistoriqueCourrier h) {
    HistoriqueCourrierDTO dto = new HistoriqueCourrierDTO();
    dto.setStatut(h.getStatut().name());
    dto.setCommentaire(h.getCommentaire());
    dto.setDateAction(h.getDateAction());

    dto.setUtilisateur(
            h.getUtilisateur() != null ? h.getUtilisateur().getNom() : "Système"
    );

    dto.setEntite(
            h.getEntite() != null ? h.getEntite().getNom() : null
    );

    return dto;
}
}