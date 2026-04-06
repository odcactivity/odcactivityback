package com.odk.Service.Interface.Service;

import com.odk.Entity.Activite;
import com.odk.Enum.Statut;
import com.odk.Repository.ActiviteRepository;
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

    public List<Activite> listerPourExport(Long entiteId, Long activiteId, int annee, Integer mois) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, annee);
        if (mois != null && mois >= 1 && mois <= 12) {
            cal.set(Calendar.MONTH, mois - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            Date debut = cal.getTime();
            cal.add(Calendar.MONTH, 1);
            Date fin = cal.getTime();
            return filterExclus(activiteRepository.findPourRapportGlobal(entiteId, activiteId, debut, fin));
        }
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date debutAnnee = cal.getTime();
        cal.add(Calendar.YEAR, 1);
        Date finAnnee = cal.getTime();
        return filterExclus(activiteRepository.findPourRapportGlobal(entiteId, activiteId, debutAnnee, finAnnee));
    }

    private List<Activite> filterExclus(List<Activite> raw) {
        return raw.stream()
                .filter(a -> a.getStatut() != Statut.En_Validation_Directeur_ODC && a.getStatut() != Statut.Rejetee)
                .collect(Collectors.toList());
    }
}
