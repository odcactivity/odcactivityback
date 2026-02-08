package com.odk.Service.Interface.Service;


import com.odk.Entity.SupportActivite;
import com.odk.Enum.TypeSupport;
import com.odk.Repository.SupportActiviteRepository;
import com.odk.dto.StatsParTypeDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatsFichierService {

//-------------------------Injection des dépendences---------------------------------//

    private final SupportActiviteRepository supportActivteRepository;

//-------------------Constructeur de la classe service------------------------------//
//---------------------------------------------------------------------------------//
    public StatsFichierService(SupportActiviteRepository supportActiviteRepository) {
        this.supportActivteRepository=supportActiviteRepository;
    }

//--------------------Le Calcul des taille de fichier -----------------------------------//
//--------------------------------------------------------------------------------//
 public StatsParTypeDTO calculerStatsParType() {
    List<SupportActivite> supports = supportActivteRepository.findAll();

    long tailleRapport = 0;
    long tailleImage = 0;
    long tailleVideo = 0;

    for (SupportActivite support : supports) {
        long taille = (support.getTaille() != null) ? support.getTaille() : 0;

        if (support.getType() == TypeSupport.RAPPORT) {
            tailleRapport += taille;
        } else if (support.getType() == TypeSupport.IMAGE) {
            tailleImage += taille;
        } else if (support.getType() == TypeSupport.VIDEO) {
            tailleVideo += taille;
        }
    }

    return new StatsParTypeDTO(tailleRapport, tailleImage, tailleVideo);
}

}