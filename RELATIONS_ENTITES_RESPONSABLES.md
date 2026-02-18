# 📋 Relations Entités - Responsables dans ODC Activity

## 🏗️ **Structure des Entités**

### **1. Entité (Entite.java)**
```java
@Entity
public class Entite {
    private Long id;
    private String nom;
    private String logo;
    private String description;
    private TypeEntite type; // DIRECTION ou SERVICE
    
    // 🔗 Relation avec le responsable
    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private Utilisateur responsable;
    
    // 🔗 Relation hiérarchique
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Entite parent;
    
    @OneToMany(mappedBy = "parent")
    private List<Entite> sousEntite;
}
```

### **2. Utilisateur (Utilisateur.java)**
```java
@Entity
public class Utilisateur {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String phone;
    private String password;
    private String genre;
    private Boolean etat;
    
    // 🔗 Relation avec l'entité d'affectation
    @ManyToOne
    @JoinColumn(name = "entite_id")
    private Entite entite;
    
    // 🔗 Relation avec le rôle
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;
}
```

## 🔄 **Mécanisme de Liaison**

### **Lors de la création d'une entité (EntiteOdcService.java)**

```java
public EntiteDTO ajouter(EntiteDTO dto, MultipartFile fichier) {
    // 1. Validation de la hiérarchie
    validerHierarchieEntite(dto);
    
    // 2. Conversion en entité
    Entite entite = EntiteMapper.toEntity(dto);
    
    // 3. 🔗 Affectation du RESPONSABLE
    if (dto.getResponsable() != null) {
        utilisateurRepository.findById(dto.getResponsable())
                .ifPresent(entite::setResponsable);
    }
    
    // 4. 🔗 Affectation du PARENT (pour les services)
    if (dto.getParentId() != null && dto.getType() == TypeEntite.SERVICE) {
        entiteOdcRepository.findById(dto.getParentId())
                .filter(parent -> parent.getType() == TypeEntite.DIRECTION)
                .ifPresentOrElse(
                    entite::setParent,
                    () -> {
                        throw new IllegalArgumentException("Le parent doit être une direction de type DIRECTION");
                    }
                );
    }
    
    return entiteOdcRepository.save(entite);
}
```

## 📊 **Règles de Hiérarchie**

### **Validation stricte (validerHierarchieEntite)**

```java
private void validerHierarchieEntite(EntiteDTO dto) {
    // 📋 Règle 1: Une DIRECTION ne peut pas avoir de parent
    if (dto.getType() == TypeEntite.DIRECTION && dto.getParentId() != null) {
        throw new IllegalArgumentException("Une direction ne peut pas avoir de parent (parentId doit être null)");
    }
    
    // 📋 Règle 2: Un SERVICE doit avoir un parent
    if (dto.getType() == TypeEntite.SERVICE && dto.getParentId() == null) {
        throw new IllegalArgumentException("Un service doit avoir un parent (parentId obligatoire)");
    }
    
    // 📋 Règle 3: Le parent d'un SERVICE doit être une DIRECTION
    if (dto.getType() == TypeEntite.SERVICE) {
        Optional<Entite> parentOpt = entiteOdcRepository.findById(dto.getParentId());
        if (parentOpt.isEmpty()) {
            throw new IllegalArgumentException("L'entité parent avec l'ID " + dto.getParentId() + " n'existe pas");
        }
        
        Entite parent = parentOpt.get();
        if (parent.getType() != TypeEntite.DIRECTION) {
            throw new IllegalArgumentException("Un service doit avoir comme parent une direction, pas un autre service");
        }
    }
}
```

## 🎯 **Scénarios de Liaison**

### **Scénario 1: Création d'une Direction**
```java
EntiteDTO directionDTO = new EntiteDTO();
directionDTO.setNom("Direction Générale");
directionDTO.setType(TypeEntite.DIRECTION);
directionDTO.setParentId(null); // Obligatoire pour une direction
directionDTO.setResponsable(1L); // ID de l'utilisateur responsable

// ✅ Résultat: Direction créée sans parent, avec responsable affecté
```

### **Scénario 2: Création d'un Service**
```java
EntiteDTO serviceDTO = new EntiteDTO();
serviceDTO.setNom("Service Informatique");
serviceDTO.setType(TypeEntite.SERVICE);
serviceDTO.setParentId(1L); // ID de la direction parente (obligatoire)
serviceDTO.setResponsable(2L); // ID du responsable du service

// ✅ Résultat: Service créé avec parent=Direction, responsable affecté
```

### **Scénario 3: Structure Hiérarchique Complète**
```
Direction Générale (id: 1, type: DIRECTION, parent: null, responsable: user1)
├── Service Informatique (id: 2, type: SERVICE, parent: 1, responsable: user2)
├── Service Administratif (id: 3, type: SERVICE, parent: 1, responsable: user3)
└── Service Technique (id: 4, type: SERVICE, parent: 1, responsable: user4)
```

## 📧 **Impact sur les Emails**

### **Qui reçoit les emails ?**

#### **1. Réception d'un courrier**
```java
// Dans CourrierService.recevoirCourrier()
if (direction.getResponsable() != null && direction.getResponsable().getEmail() != null) {
    // 📧 Email au RESPONSABLE de la direction
    emailService.sendSimpleEmail(direction.getResponsable().getEmail(), 
        "Nouveau courrier reçu : " + courrier.getNumero(), emailBody);
}
```

#### **2. Imputation d'un courrier**
```java
// Dans CourrierService.imputerCourrier()
// 📧 Email à l'utilisateur affecté
if (utilisateur != null && utilisateur.getEmail() != null) {
    emailService.sendSimpleEmail(utilisateur.getEmail(), "Courrier à traiter", emailBody);
}

// 📧 Email au responsable du service
if (service.getResponsable() != null && service.getResponsable().getEmail() != null) {
    emailService.sendSimpleEmail(service.getResponsable().getEmail(), 
        "Courrier imputé à votre service", emailBody);
}
```

#### **3. Réponse à un courrier**
```java
// Dans ReponseCourrierService.envoyerNotificationsReponse()
// 📧 Email au responsable de l'entité du courrier
if (courrier.getEntite().getResponsable() != null) {
    emailService.sendSimpleEmail(courrier.getEntite().getResponsable().getEmail(), 
        "Réponse au courrier : " + courrier.getNumero(), emailBody);
}

// 📧 Email à l'utilisateur affecté au courrier
if (courrier.getUtilisateurAffecte() != null) {
    emailService.sendSimpleEmail(courrier.getUtilisateurAffecte().getEmail(), 
        "Réponse au courrier : " + courrier.getNumero(), emailBody);
}
```

#### **4. Rappels automatiques**
```java
// Dans CourrierRappelService.verifierRappelsCourriers()
// 📧 Email au responsable de l'entité
if (courrier.getEntite().getResponsable() != null) {
    emailService.sendSimpleEmail(courrier.getEntite().getResponsable().getEmail(), 
        "⏰ RAPPEL : Courrier " + courrier.getNumero(), emailBody);
}

// 📧 Email à l'utilisateur affecté
if (courrier.getUtilisateurAffecte() != null) {
    emailService.sendSimpleEmail(courrier.getUtilisateurAffecte().getEmail(), 
        "⏰ RAPPEL : Courrier " + courrier.getNumero(), emailBody);
}
```

## 🎯 **Points Clés**

### **1. Double Responsabilité**
- **Responsable d'entité** : Reçoit les emails de notification (réception, imputation, réponse, rappels)
- **Utilisateur affecté** : Reçoit les emails de traitement (imputation, réponse, rappels)

### **2. Hiérarchie Stricte**
- **Direction** : Entité racine, pas de parent possible
- **Service** : Doit obligatoirement avoir une direction comme parent
- **Validation** : Empêche les incohérences hiérarchiques

### **3. Traçabilité Complète**
- **Chaque courrier** : Lié à une entité (direction/service)
- **Chaque entité** : A un responsable désigné
- **Chaque action** : Notifiée aux bonnes personnes

### **4. Sécurité**
- **Validation stricte** : Empêche les structures invalides
- **Contrôle d'accès** : Seuls les responsables et utilisateurs concernés reçoivent les emails
- **Logs détaillés** : Traçabilité de toutes les notifications

## 🔄 **Workflow Complet**

1. **Création entité** → Affectation responsable → Validation hiérarchie
2. **Réception courrier** → Email responsable direction
3. **Imputation** → Email utilisateur + Email responsable service
4. **Traitement** → Email réponse aux responsables/utilisateurs
5. **Rappels** → Emails automatiques avant date limite
6. **Archivage** → Email utilisateur archiveur

Ce système garantit que **toutes les actions liées aux courriers** sont notifiées aux **bonnes personnes** ! 🎯
