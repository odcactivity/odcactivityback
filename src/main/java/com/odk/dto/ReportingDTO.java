package com.odk.dto;

import java.util.Date;

public class ReportingDTO {
    private String nom;
    private String prenom;
    private String email;
    private String phone;
    private String genre;
    private String activiteNom;
    private String entiteNom;
    private Integer age;
    private Date dateDebut;
    private Date dateFin;

    // Constructeur complet
    public ReportingDTO(String nom, String prenom, String email, String phone, String genre,
                        String activiteNom, String entiteNom, Integer age, Date dateDebut, Date dateFin) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.phone = phone;
        this.genre = genre;
        this.activiteNom = activiteNom;
        this.entiteNom = entiteNom;
        this.age = age;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    // Getters et setters
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getActiviteNom() { return activiteNom; }
    public void setActiviteNom(String activiteNom) { this.activiteNom = activiteNom; }

    public String getEntiteNom() { return entiteNom; }
    public void setEntiteNom(String entiteNom) { this.entiteNom = entiteNom; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
