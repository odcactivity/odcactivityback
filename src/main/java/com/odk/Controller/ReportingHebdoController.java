package com.odk.Controller;

import com.odk.Service.Interface.Service.ReportingHebdoService;
import com.odk.dto.ReportingHebdoActiviteDTO;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/reportinghebdo")
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:4200",
        allowCredentials = "true")
public class ReportingHebdoController {

    private final ReportingHebdoService reportingHebdoService;

    /**
     * Endpoint Reporting Hebdomadaire
     * filtre : entité + date début + date fin
     */
    @GetMapping("/activites")
    public ResponseEntity<List<ReportingHebdoActiviteDTO>> getActivitesHebdo(
            @RequestParam("entiteId") Long entiteId,
            @RequestParam("dateDebut")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateDebut,
            @RequestParam("dateFin")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFin
    ) {

        Date start = Date.from(
                dateDebut.atStartOfDay(ZoneId.systemDefault()).toInstant()
        );

        Date end = Date.from(
                dateFin.atTime(23, 59, 59)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
        );

        List<ReportingHebdoActiviteDTO> result =
                reportingHebdoService.getActivitesHebdo(entiteId, start, end);

        return ResponseEntity.ok(result);
    }
}
