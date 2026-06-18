package com.odk.Service.Interface.Service;

import com.odk.Entity.Activite;
import com.odk.Entity.Courrier;
import com.odk.Enum.Statut;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.CourrierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RapportActiviteService {

    private final ActiviteRepository activiteRepository;
    private final CourrierRepository courrierRepository;

    public List<Activite> listerPourExport(Long entiteId, Long activiteId, Long courrierId, int annee, Integer mois) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, annee);
        if (mois != null && mois >= 1 && mois <= 12) {
            cal.set(Calendar.MONTH, mois - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            Date debut = cal.getTime();
            cal.add(Calendar.MONTH, 1);
            Date fin = cal.getTime();
            return filterExclus(activiteRepository.findPourRapportGlobal(entiteId, activiteId, courrierId, debut, fin));
        }
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date debutAnnee = cal.getTime();
        cal.add(Calendar.YEAR, 1);
        Date finAnnee = cal.getTime();
        return filterExclus(activiteRepository.findPourRapportGlobal(entiteId, activiteId, courrierId, debutAnnee, finAnnee));
    }

    public List<Courrier> listerCourriersPourExport(Long entiteId, Long courrierId, int annee, Integer mois) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, annee);
        Date debut;
        Date fin;
        if (mois != null && mois >= 1 && mois <= 12) {
            cal.set(Calendar.MONTH, mois - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            debut = cal.getTime();
            cal.add(Calendar.MONTH, 1);
            fin = cal.getTime();
        } else {
            cal.set(Calendar.MONTH, Calendar.JANUARY);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            debut = cal.getTime();
            cal.add(Calendar.YEAR, 1);
            fin = cal.getTime();
        }

        return courrierRepository.findAll().stream()
                .filter(c -> c.getDateReception() != null 
                        && !c.getDateReception().before(debut) 
                        && c.getDateReception().before(fin))
                .filter(c -> entiteId == null || 
                        (c.getEntite() != null && entiteId.equals(c.getEntite().getId())) ||
                        (c.getStructureOrigine() != null && entiteId.equals(c.getStructureOrigine().getId())) ||
                        (c.getDirectionInitial() != null && entiteId.equals(c.getDirectionInitial().getId())) ||
                        (c.getCibleInterneDirection() != null && entiteId.equals(c.getCibleInterneDirection().getId())) ||
                        (c.getServiceOdcAffecte() != null && entiteId.equals(c.getServiceOdcAffecte().getId())))
                .filter(c -> courrierId == null || courrierId.equals(c.getId()))
                .collect(Collectors.toList());
    }

    private List<Activite> filterExclus(List<Activite> raw) {
        return raw.stream()
                .filter(a -> a.getStatut() != Statut.En_Validation_Directeur_ODC && a.getStatut() != Statut.Rejetee)
                .collect(Collectors.toList());
    }
}
