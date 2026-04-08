package com.odk.Enum;

public enum StatutCourrier {
    ENVOYER,   //Courrier envoyé/reçu
    IMPUTER,  //Courrier imputé (envoyerOupartager) à une entité ou département...
    EN_COURS, //Courrier en cours de traitement...
    ARCHIVER,     //Courrier Archiver...
    /** Ancien flux / compatibilité — préférer ATTENTE_VALIDATION_DIRECTEUR_ODC */
    ATTENTE_VALIDATION_ODC,
    /** Brouillon admin : en attente de validation contenu par le directeur ODC */
    ATTENTE_VALIDATION_DIRECTEUR_ODC,
    /** Le directeur a demandé des corrections ; l’admin doit réviser */
    EN_REVISION_ADMIN_COURRIER,
    /** Validé côté ODC, transmis à la DCIRE (détenteur courant = direction DCIRE) */
    TRANSMIS_DCIRE,
    /**
     * Courrier adressé à une direction interne (Fondation / RSE / DCI) : en attente de validation
     * par le ou la directeur·rice de cette structure avant mise à disposition du service.
     */
    ATTENTE_VALIDATION_DIRECTEUR_STRUCTURE,
    REPONDU
}