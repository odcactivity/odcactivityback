package com.odk.Service.Interface.Service;

import com.odk.Entity.Courrier;
import com.odk.Entity.HistoriqueCourrier;
import com.odk.Repository.CourrierRepository;
import com.odk.Repository.HistoriqueCourrierRepository;
import com.odk.Repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class HistoriqueCourrierService {

    private final CourrierRepository courrierRepository;
    private final HistoriqueCourrierRepository historiqueRepository;
    private final UtilisateurRepository utilisateurRepository;

    public List<HistoriqueCourrier> getHistoriqueCourrierAutorise(
            Long courrierId
    ) {

        Courrier courrier = courrierRepository.findById(courrierId)
                .orElseThrow(() -> new RuntimeException("Courrier non trouvé"));

        // Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
        //         .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // // Verifier les droits d'acces à l'historique....
        // //Veririfie si tu a le role responsable entite
        // boolean estResponsableEntite =
        //         courrier.getEntite() != null
        //         && courrier.getEntite().getResponsable() != null
        //         && courrier.getEntite().getResponsable().getId().equals(utilisateurId);
        // //Verifie si tu es Utilisateur affecté ....
        // boolean estUtilisateurAffecte =
        //         courrier.getUtilisateurAffecte() != null
        //         && courrier.getUtilisateurAffecte().getId().equals(utilisateurId);
        // //Verifie si le courrier existe ....
        // boolean aIntervenu =
        //         HistoriqueCourrierRepository.existsByCourrierIdAndUtilisateurId(
        //                 courrierId, utilisateurId);

        // if (!estResponsableEntite && !estUtilisateurAffecte && !aIntervenu) {
        //     throw new SecurityException("Accès refusé à l'historique de ce courrier");
        // }

        // Apres toutes ces verifications, Accès autorisé
        return historiqueRepository
                .findByCourrierIdOrderByDateActionAsc(courrierId);
    }
}