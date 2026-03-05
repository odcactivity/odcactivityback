package com.odk.Controller;

import com.odk.Service.Interface.Service.ReportingService;
import com.odk.dto.ReportingDTO;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reporting")
@AllArgsConstructor

public class ReportingController {

    private final ReportingService reportingService;

    /**
     * 🔹 Reporting complet avec filtres optionnels
     *
     * Exemple d'appel :
     * /reporting
     * /reporting?entiteId=1
     * /reporting?activiteId=3
     * /reporting?annee=2025
     * /reporting?entiteId=1&activiteId=3&annee=2025
     */
    @GetMapping
    public ResponseEntity<List<ReportingDTO>> getReporting(
            @RequestParam(required = false) Long entiteId,
            @RequestParam(required = false) Long activiteId,
            @RequestParam(required = false) Integer annee
    ) {

        List<ReportingDTO> result =
                reportingService.getParticipantsFiltered(entiteId, activiteId, annee);

        return ResponseEntity.ok(result);
    }
}