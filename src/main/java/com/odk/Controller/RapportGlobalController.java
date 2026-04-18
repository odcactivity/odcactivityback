package com.odk.Controller;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.odk.Entity.Activite;
import com.odk.Entity.Entite;
import com.odk.Service.Interface.Service.RapportActiviteService;
import com.odk.dto.RapportActiviteApercuDTO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rapport-global")
@RequiredArgsConstructor
public class RapportGlobalController {

    private final RapportActiviteService rapportActiviteService;
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");

    @GetMapping("/activites-apercu")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public List<RapportActiviteApercuDTO> apercuActivites(
            @RequestParam(required = false) Long entiteId,
            @RequestParam(required = false) Long activiteId,
            @RequestParam int annee,
            @RequestParam(required = false) Integer mois
    ) {
        return rapportActiviteService.listerPourExport(entiteId, activiteId, annee, mois).stream()
                .map(this::toApercu)
                .collect(Collectors.toList());
    }

    private RapportActiviteApercuDTO toApercu(Activite a) {
        String entiteNom = "";
        if (a.getEntite() != null && a.getEntite().getNom() != null) {
            entiteNom = a.getEntite().getNom();
        }
        return new RapportActiviteApercuDTO(
                a.getId(),
                a.getNom(),
                a.getTitre(),
                a.getDateDebut(),
                a.getDateFin(),
                a.getStatut(),
                entiteNom,
                a.getLieu()
        );
    }

    @GetMapping(value = "/activites.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public void exportActivitesCsv(
            @RequestParam(required = false) Long entiteId,
            @RequestParam(required = false) Long activiteId,
            @RequestParam int annee,
            @RequestParam(required = false) Integer mois,
            HttpServletResponse response
    ) throws IOException {
        List<Activite> list = rapportActiviteService.listerPourExport(entiteId, activiteId, annee, mois);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"rapport_activites.csv\"");
        PrintWriter w = response.getWriter();
        w.println("id;nom;titre;dateDebut;dateFin;statut;entite;lieu;description");
        for (Activite a : list) {
            String entiteNom = "";
            if (a.getEntite() != null) {
                entiteNom = a.getEntite().getNom() != null ? a.getEntite().getNom().replace(";", ",") : "";
            }
            String desc = a.getDescription() != null ? a.getDescription().replace(";", ",").replace("\n", " ") : "";
            w.printf("%d;%s;%s;%s;%s;%s;%s;%s;%s%n",
                    a.getId(),
                    safeCsv(a.getNom()),
                    safeCsv(a.getTitre()),
                    a.getDateDebut() != null ? DF.format(a.getDateDebut()) : "",
                    a.getDateFin() != null ? DF.format(a.getDateFin()) : "",
                    a.getStatut() != null ? a.getStatut().name() : "",
                    entiteNom,
                    safeCsv(a.getLieu()),
                    desc
            );
        }
        w.flush();
    }

    @GetMapping(value = "/activites.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('DIRECTEUR_ODC')")
    public void exportActivitesPdf(
            @RequestParam(required = false) Long entiteId,
            @RequestParam(required = false) Long activiteId,
            @RequestParam int annee,
            @RequestParam(required = false) Integer mois,
            HttpServletResponse response
    ) throws IOException {
        List<Activite> list = rapportActiviteService.listerPourExport(entiteId, activiteId, annee, mois);
        response.setHeader("Content-Disposition", "attachment; filename=\"rapport_activites.pdf\"");
        Document document = new Document();
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();
        document.add(new Paragraph("Rapport global des activités ODC"));
        document.add(new Paragraph("Période : " + (mois != null ? "mois " + mois + " / " : "") + annee));
        document.add(new Paragraph("Nombre d'activités : " + list.size()));
        document.add(new Paragraph(" "));
        for (Activite a : list) {
            Entite e = a.getEntite();
            String ligne = (a.getNom() != null ? a.getNom() : "") + " | "
                    + (a.getStatut() != null ? a.getStatut().name() : "") + " | "
                    + (e != null && e.getNom() != null ? e.getNom() : "") + " | "
                    + (a.getDateDebut() != null ? DF.format(a.getDateDebut()) : "");
            document.add(new Paragraph(ligne));
        }
        document.close();
    }

    private static String safeCsv(String s) {
        if (s == null) {
            return "";
        }
        return s.replace(";", ",").replace("\n", " ");
    }
}
