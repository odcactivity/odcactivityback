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
import java.text.Normalizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odk.Entity.Courrier;
import com.odk.Entity.Entite;
import com.odk.Entity.Utilisateur;
import com.odk.Enum.StatutCourrier;
import com.odk.Enum.TypeEntite;
import com.odk.Repository.CourrierRepository;
import com.odk.Repository.EntiteOdcRepository;
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
    private final EntiteOdcRepository entiteOdcRepository;

    @Value("${app.courrier.dcire-direction-id:0}")
    private long configuredDcireDirectionId;

    public CourrierDashboardTotalsDTO totaux(Long structureId, Utilisateur principal) {
        Long effectiveStructureId = resolveEffectiveStructureId(structureId, principal);
        List<Courrier> list = loadCourriers();
        applyCourrierDashboardStructureFilter(list, effectiveStructureId, principal);
        EnumMap<Cat, Long> m = new EnumMap<>(Cat.class);
        for (Cat c : Cat.values()) {
            m.put(c, 0L);
        }
        for (Courrier c : list) {
            try {
                categorie(c, effectiveStructureId).ifPresent(cat -> m.merge(cat, 1L, Long::sum));
            } catch (RuntimeException ex) {
                // Ne bloque pas le dashboard pour un courrier incohérent.
            }
        }
        return new CourrierDashboardTotalsDTO(
                m.get(Cat.emis),
                m.get(Cat.repondu),
                m.get(Cat.enAttente),
                m.get(Cat.recu),
                m.get(Cat.valide));
    }

    public CourrierDashboardSerieDTO serie(String periode, Long structureId, Utilisateur principal) {
        String p = periode == null ? "semaine" : periode.trim().toLowerCase(Locale.ROOT);
        Long effectiveStructureId = resolveEffectiveStructureId(structureId, principal);
        List<Courrier> list = loadCourriers();
        applyCourrierDashboardStructureFilter(list, effectiveStructureId, principal);

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
                try {
                    LocalDate dr = receptionDate(c);
                    if (dr == null || dr.isBefore(b.start) || dr.isAfter(b.end)) {
                        continue;
                    }
                    Optional<Cat> cat = categorie(c, effectiveStructureId);
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
                    safeDetailRow(c, cat.get()).ifPresent(d -> row.getDetails().add(d));
                } catch (RuntimeException ex) {
                    // Skip d'un enregistrement invalide, sans interrompre toute la série.
                }
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
        Entite cur = e;
        while (cur != null && guard++ < 32) {
            try {
                if (directionId.equals(cur.getId())) {
                    return true;
                }
                cur = cur.getParent();
            } catch (RuntimeException ex) {
                return false;
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

    private void applyCourrierDashboardStructureFilter(List<Courrier> list, Long effectiveStructureId, Utilisateur principal) {
        if (isDcireHubDirectorRole(principal)) {
            list.removeIf(c -> !matchesDcireHubDashboard(c, effectiveStructureId));
        } else {
            list.removeIf(c -> !matchesStructureFilter(c, effectiveStructureId));
        }
    }

    /**
     * Directeur historique « DCIRE » (rôle {@code DIRECTEUR}) ou rôle explicite {@code DCIRE}.
     */
    private boolean isDcireHubDirectorRole(Utilisateur principal) {
        if (principal == null || principal.getRole() == null || principal.getRole().getNom() == null) {
            return false;
        }
        String role = principal.getRole().getNom().trim().toUpperCase(Locale.ROOT);
        return "DIRECTEUR".equals(role) || "DCIRE".equals(role);
    }

    /**
     * Stats courrier côté hub DCIRE uniquement : détenteur = direction DCIRE (ou service directement sous elle),
     * ou origine / direction initiale = DCIRE. On n’utilise pas {@link #entiteLieADirection(Entite, Long)} sur le
     * détenteur, sinon tout courrier d’une direction « fille » (ODC, Fondation, RSE, DCI) remonterait au parent DCIRE
     * en base et fausserait les totaux (même chiffres que le dash admin ODC).
     */
    private boolean matchesDcireHubDashboard(Courrier c, Long dcireId) {
        if (dcireId == null) {
            return false;
        }
        if (c.getEntite() != null && dcireId.equals(c.getEntite().getId())) {
            return true;
        }
        if (entiteServiceDirectementSousDirection(c.getEntite(), dcireId)) {
            return true;
        }
        if (c.getStructureOrigine() != null && dcireId.equals(c.getStructureOrigine().getId())) {
            return true;
        }
        if (c.getDirectionInitial() != null && dcireId.equals(c.getDirectionInitial().getId())) {
            return true;
        }
        return false;
    }

    private boolean entiteServiceDirectementSousDirection(Entite e, Long directionId) {
        if (e == null || directionId == null) {
            return false;
        }
        if (e.getType() != TypeEntite.SERVICE) {
            return false;
        }
        Entite p = e.getParent();
        return p != null && directionId.equals(p.getId());
    }

    private Long resolveEffectiveStructureId(Long requestedStructureId, Utilisateur principal) {
        if (principal == null) {
            return requestedStructureId;
        }
        String role = "";
        if (principal.getRole() != null && principal.getRole().getNom() != null) {
            role = principal.getRole().getNom().trim().toUpperCase(Locale.ROOT);
        }
        Long myStructureId = principal.getEntite() != null ? principal.getEntite().getId() : null;
        // Directeur ODC : même lecture que l’admin ODC pour les stats courrier.
        // Si un filtre structure ODC est demandé, on le respecte ; sinon on retombe sur sa direction.
        if ("DIRECTEUR_ODC".equals(role)) {
            if (requestedStructureId != null) {
                return requestedStructureId;
            }
            if (myStructureId != null) {
                return myStructureId;
            }
        }
        /*
         * Hub DCIRE (rôle DIRECTEUR historique, ou DCIRE explicite) : les stats courrier doivent refléter
         * uniquement le périmètre DCIRE (émis / reçus / etc. au hub), jamais l’ensemble ODC+Fondation+RSE+DCI.
         * Si le compte a une entité « trop large » (ex. Orange Digital Center), utiliser myStructureId
         * faussait les totaux (même affichage que l’admin sur « Toutes »).
         */
        if ("DIRECTEUR".equals(role) || "DCIRE".equals(role)) {
            Long dcireId = resolveDcireDirectionIdForDashboard();
            if (dcireId != null) {
                return dcireId;
            }
            if (myStructureId != null) {
                return myStructureId;
            }
        }
        return requestedStructureId;
    }

    /**
     * Identifiant de la direction « hub » DCIRE (config ou heuristique nom), pour filtrer les stats courrier.
     */
    private Long resolveDcireDirectionIdForDashboard() {
        if (configuredDcireDirectionId > 0) {
            return entiteOdcRepository.findById(configuredDcireDirectionId)
                    .filter(e -> e.getType() == TypeEntite.DIRECTION)
                    .map(Entite::getId)
                    .orElse(null);
        }
        return entiteOdcRepository.findByType(TypeEntite.DIRECTION).stream()
                .filter(this::nomIndiqueDcire)
                .map(Entite::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean nomIndiqueDcire(Entite e) {
        if (e == null || e.getNom() == null) {
            return false;
        }
        String n = normalizeNomEntitePourDcire(e.getNom());
        if (n.contains("DCIRE")) {
            return true;
        }
        return n.replace('-', ' ').contains("DCI RE");
    }

    private static String normalizeNomEntitePourDcire(String nom) {
        String decomposed = Normalizer.normalize(nom, Normalizer.Form.NFD);
        String sansAccents = decomposed.replaceAll("\\p{M}+", "");
        return sansAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private LocalDate receptionDate(Courrier c) {
        if (c.getDateReception() == null) {
            return null;
        }
        return c.getDateReception().toInstant().atZone(TZ).toLocalDate();
    }

    private Optional<Cat> categorie(Courrier c, Long structureId) {
        StatutCourrier s = c.getStatut();
        if (s == null || s == StatutCourrier.ARCHIVER) {
            return Optional.empty();
        }
        // "Émis" = courrier parti de la structure courante et déjà sorti de son portefeuille courant.
        if (structureId != null && estCourrierEmisParStructure(c, structureId)) {
            return Optional.of(Cat.emis);
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

    private boolean estCourrierEmisParStructure(Courrier c, Long structureId) {
        if (structureId == null) {
            return false;
        }
        boolean origineMatch = entiteLieADirection(c.getStructureOrigine(), structureId);
        if (!origineMatch) {
            return false;
        }
        return !entiteLieADirection(c.getEntite(), structureId);
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

    /**
     * Protège la série « évolution » contre des données relationnelles partielles
     * (entité supprimée, proxy cassé, etc.) : on conserve les compteurs même si
     * une ligne de détail est illisible.
     */
    private Optional<CourrierDashboardDetailRowDTO> safeDetailRow(Courrier c, Cat cat) {
        try {
            return Optional.of(detailRow(c, cat));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
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
        String origine = safeNom(c.getStructureOrigine());
        if (origine == null) {
            origine = safeNom(c.getDirectionInitial());
        }
        String destination = safeNom(c.getEntite());
        if (origine != null && destination != null) {
            if (origine.equalsIgnoreCase(destination)) {
                return origine;
            }
            return origine + " -> " + destination;
        }
        if (origine != null) {
            return origine;
        }
        if (destination != null) {
            return destination;
        }
        return "—";
    }

    private static String safeNom(Entite e) {
        if (e == null) {
            return null;
        }
        try {
            String n = e.getNom();
            if (n == null || n.isBlank()) {
                return null;
            }
            return n.trim();
        } catch (RuntimeException ex) {
            return null;
        }
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
