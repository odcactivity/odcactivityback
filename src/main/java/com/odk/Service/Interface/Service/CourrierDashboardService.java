package com.odk.Service.Interface.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odk.Entity.Courrier;
import com.odk.Entity.Entite;
import com.odk.Enum.StatutCourrier;
import com.odk.Repository.CourrierRepository;
import com.odk.dto.CourrierDashboardBucketDTO;
import com.odk.dto.CourrierDashboardDetailRowDTO;
import com.odk.dto.CourrierDashboardSerieDTO;
import com.odk.dto.CourrierDashboardTotalsDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourrierDashboardService {

    private static final ZoneId TZ = ZoneId.systemDefault();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<StatutCourrier> EN_ATTENTE = Set.of(
            StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_ODC,
            StatutCourrier.ATTENTE_VALIDATION_ODC,
            StatutCourrier.EN_REVISION_ADMIN_COURRIER,
            StatutCourrier.ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE);

    private static final String[] WEEKDAY_LABELS = { "lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim." };
    private static final String[] MONTH_LABELS = {
            "janv.", "févr.", "mars", "avr.", "mai", "juin",
            "juil.", "août", "sept.", "oct.", "nov.", "déc."
    };

    private enum Cat {
        emis, repondu, enAttente, recu, valide
    }

    private final CourrierRepository courrierRepository;

    public CourrierDashboardTotalsDTO totaux(Long structureId) {
        List<Courrier> list = loadCourriers();
        list.removeIf(c -> !matchesStructureFilter(c, structureId));
        EnumMap<Cat, Long> m = new EnumMap<>(Cat.class);
        for (Cat c : Cat.values()) {
            m.put(c, 0L);
        }
        for (Courrier c : list) {
            categorie(c).ifPresent(cat -> m.merge(cat, 1L, Long::sum));
        }
        return new CourrierDashboardTotalsDTO(
                m.get(Cat.emis),
                m.get(Cat.repondu),
                m.get(Cat.enAttente),
                m.get(Cat.recu),
                m.get(Cat.valide));
    }

    public CourrierDashboardSerieDTO serie(String periode, Long structureId) {
        String p = periode == null ? "semaine" : periode.trim().toLowerCase(Locale.ROOT);
        List<Courrier> list = loadCourriers();
        list.removeIf(c -> !matchesStructureFilter(c, structureId));

        LocalDate today = LocalDate.now(TZ);
        List<Bucket> buckets = switch (p) {
            case "mois" -> buildMonthBuckets(today);
            case "annee" -> buildYearBuckets(today);
            default -> buildWeekBuckets(today);
        };

        CourrierDashboardSerieDTO dto = new CourrierDashboardSerieDTO();
        dto.setPeriode(p);

        for (Bucket b : buckets) {
            CourrierDashboardBucketDTO row = new CourrierDashboardBucketDTO();
            row.setLabel(b.label);
            row.setDebut(b.start.format(ISO));
            row.setFin(b.end.format(ISO));
            row.setDetails(new ArrayList<>());

            for (Courrier c : list) {
                LocalDate dr = receptionDate(c);
                if (dr == null || dr.isBefore(b.start) || dr.isAfter(b.end)) {
                    continue;
                }
                Optional<Cat> cat = categorie(c);
                if (cat.isEmpty()) {
                    continue;
                }
                switch (cat.get()) {
                    case emis -> row.setEmis(row.getEmis() + 1);
                    case repondu -> row.setRepondu(row.getRepondu() + 1);
                    case enAttente -> row.setEnAttente(row.getEnAttente() + 1);
                    case recu -> row.setRecu(row.getRecu() + 1);
                    case valide -> row.setValide(row.getValide() + 1);
                }
                row.getDetails().add(detailRow(c, cat.get()));
            }
            dto.getBuckets().add(row);
        }
        return dto;
    }

    private List<Courrier> loadCourriers() {
        return new ArrayList<>(courrierRepository.findAllNonArchivedForDashboard(StatutCourrier.ARCHIVER));
    }

    /**
     * Une direction regroupe les courriers rattachés à elle-même ou à l’un de ses services (toute profondeur).
     * Le service est transactionnel : la chaîne {@code parent} peut être résolue par Hibernate si besoin.
     */
    private boolean entiteLieADirection(Entite e, Long directionId) {
        if (e == null || directionId == null) {
            return false;
        }
        int guard = 0;
        for (Entite cur = e; cur != null && guard++ < 32; cur = cur.getParent()) {
            if (directionId.equals(cur.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesStructureFilter(Courrier c, Long structureId) {
        if (structureId == null) {
            return true;
        }
        if (entiteLieADirection(c.getEntite(), structureId)) {
            return true;
        }
        if (entiteLieADirection(c.getStructureOrigine(), structureId)) {
            return true;
        }
        if (entiteLieADirection(c.getDirectionInitial(), structureId)) {
            return true;
        }
        return false;
    }

    private LocalDate receptionDate(Courrier c) {
        if (c.getDateReception() == null) {
            return null;
        }
        return c.getDateReception().toInstant().atZone(TZ).toLocalDate();
    }

    private Optional<Cat> categorie(Courrier c) {
        StatutCourrier s = c.getStatut();
        if (s == null || s == StatutCourrier.ARCHIVER) {
            return Optional.empty();
        }
        if (s == StatutCourrier.REPONDU) {
            return Optional.of(Cat.repondu);
        }
        if (EN_ATTENTE.contains(s)) {
            return Optional.of(Cat.enAttente);
        }
        if (s == StatutCourrier.ENVOYER) {
            return Optional.of(Cat.recu);
        }
        if (s == StatutCourrier.EN_COURS || s == StatutCourrier.IMPUTER) {
            return Optional.of(Cat.valide);
        }
        if (s == StatutCourrier.TRANSMIS_DCIRE) {
            return Optional.of(Cat.emis);
        }
        return Optional.empty();
    }

    private CourrierDashboardDetailRowDTO detailRow(Courrier c, Cat cat) {
        LocalDate dr = receptionDate(c);
        String dstr = dr != null ? dr.format(ISO) : "—";
        return new CourrierDashboardDetailRowDTO(
                cat.name(),
                libelle(cat),
                structureLabel(c),
                dstr);
    }

    private static String libelle(Cat cat) {
        return switch (cat) {
            case emis -> "Émis";
            case repondu -> "Répondu";
            case enAttente -> "En attente";
            case recu -> "Reçu";
            case valide -> "Validé";
        };
    }

    private static String structureLabel(Courrier c) {
        if (c.getEntite() != null && c.getEntite().getNom() != null && !c.getEntite().getNom().isBlank()) {
            return c.getEntite().getNom().trim();
        }
        if (c.getStructureOrigine() != null && c.getStructureOrigine().getNom() != null
                && !c.getStructureOrigine().getNom().isBlank()) {
            return c.getStructureOrigine().getNom().trim();
        }
        if (c.getDirectionInitial() != null && c.getDirectionInitial().getNom() != null
                && !c.getDirectionInitial().getNom().isBlank()) {
            return c.getDirectionInitial().getNom().trim();
        }
        return "—";
    }

    private List<Bucket> buildWeekBuckets(LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Bucket> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            out.add(new Bucket(WEEKDAY_LABELS[i], d, d));
        }
        return out;
    }

    private List<Bucket> buildMonthBuckets(LocalDate today) {
        LocalDate first = today.withDayOfMonth(1);
        int lastDay = today.lengthOfMonth();
        List<Bucket> out = new ArrayList<>();
        for (int day = 1; day <= lastDay; day++) {
            LocalDate d = first.withDayOfMonth(day);
            out.add(new Bucket(String.valueOf(day), d, d));
        }
        return out;
    }

    private List<Bucket> buildYearBuckets(LocalDate today) {
        int y = today.getYear();
        List<Bucket> out = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate start = LocalDate.of(y, month, 1);
            LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
            out.add(new Bucket(MONTH_LABELS[month - 1], start, end));
        }
        return out;
    }

    private record Bucket(String label, LocalDate start, LocalDate end) {
    }
}
