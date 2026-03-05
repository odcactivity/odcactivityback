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
@RequestMapping("/reporting")
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ReportingHebdoController {

    private final ReportingHebdoService reportingService;

    @GetMapping("/activites")
    public ResponseEntity<List<ReportingHebdoActiviteDTO>> getActivitesHebdo(
            @RequestParam Long entiteId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateDebut,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFin
    ) {

        List<ReportingHebdoActiviteDTO> result =
                reportingService.getActivitesHebdo(entiteId, dateDebut, dateFin);

        return ResponseEntity.ok(result);
    }
}