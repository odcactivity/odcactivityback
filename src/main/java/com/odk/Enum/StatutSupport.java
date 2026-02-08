package com.odk.Enum;

public enum StatutSupport {
    
    En_ATTENTE("En_Attente"),
    VALIDER("validé"),
    A_CORRIGER("A corriger"),
    REFUSER("Refusé");  

    public final String libelle;
    
    StatutSupport(String libelle){
        this.libelle=libelle;
    }
     // Getter pour le libellé lisible
    public String getLibelle(){
        return libelle;
    }

    // Méthode pour récupérer l'enum depuis un libellé
    public static StatutSupport fromLibelle(String libelle) {
        for (StatutSupport s : StatutSupport.values()) {
            if (s.getLibelle().equalsIgnoreCase(libelle)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Statut inconnu : " + libelle);
    }

}
