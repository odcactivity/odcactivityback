package com.odk.Service.Interface.Service;

import com.odk.Entity.Entite;
import com.odk.Entity.Role;
import com.odk.Entity.Utilisateur;
import com.odk.Repository.EntiteOdcRepository;
import com.odk.Repository.RoleRepository;
import com.odk.Repository.UtilisateurRepository;
import com.odk.Service.Interface.CrudService;
import com.odk.Utils.UtilService;
import com.odk.dto.UtilisateurDTO;
import com.odk.execption.IncorrectPasswordException;
import com.odk.execption.UtilisateurNotFoundException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UtilisateurService implements UserDetailsService, CrudService<Utilisateur, Long> {

    private UtilisateurRepository utilisateurRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private RoleRepository roleRepository;
    private EntiteOdcRepository entiteOdcRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = username == null ? "" : username.trim().toLowerCase();
        return utilisateurRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Aucun utilisateur pour l'email : " + normalizedEmail));
    }

    @Override
    public Utilisateur add(Utilisateur utilisateur) {        
        System.out.println("NOM========="+utilisateur.getNom());
        System.out.println("ENTITE========="+utilisateur.getEntite().getNom());
        System.out.println("rolE========="+utilisateur.getRole().getNom());
        if (!UtilService.isValidEmail(utilisateur.getEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Votre mail est invalide");
        }

        Optional<Utilisateur> utilisateur1 = this.utilisateurRepository.findByEmail(utilisateur.getEmail());
        if (utilisateur1.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Votre mail est déjà utilisé");
        }

        // Définir un mot de passe par défaut si aucun mot de passe n'est fourni
        String defaultPassword = "motdepasse123";
        String rawPassword = utilisateur.getPassword() != null ? utilisateur.getPassword() : defaultPassword;

        // Encoder le mot de passe pour le stockage
        String encodedPassword = passwordEncoder.encode(rawPassword);
        utilisateur.setPassword(encodedPassword);

        // Vérifiez si le rôle est null avant d'accéder à ses propriétés
        if (utilisateur.getRole() == null || utilisateur.getRole().getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Le rôle ne peut pas être null");
        }

        // Rechercher le rôle par son nom
        Role role = roleRepository.findById(utilisateur.getRole().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Le rôle " + utilisateur.getRole().getId() + " n'existe pas"));

        utilisateur.setRole(role);
        utilisateur.setEtat(true);
        Utilisateur savedUtilisateur = utilisateurRepository.save(utilisateur);


        // Construire le corps de l'email avec HTML pour une meilleure présentation
        StringBuilder emailBodyBuilder = new StringBuilder();
        emailBodyBuilder.append("<!DOCTYPE html>");
        emailBodyBuilder.append("<html lang=\"fr\">");
        emailBodyBuilder.append("<head>");
        emailBodyBuilder.append("<meta charset=\"UTF-8\">");
        emailBodyBuilder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        emailBodyBuilder.append("<title>Confirmation de Création de Compte</title>");
        emailBodyBuilder.append("<style>");
        emailBodyBuilder.append("  body { font-family: Arial, sans-serif; background-color: #f39c12; margin: 0; padding: 20px; }");
        emailBodyBuilder.append("  .container { background-color: #ffffff; padding: 20px; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
        emailBodyBuilder.append("  .header { text-align: center; padding-bottom: 20px; }");
        emailBodyBuilder.append("  .content { line-height: 1.6; }");
        emailBodyBuilder.append("  .footer { margin-top: 20px; font-size: 0.9em; color: #555555; text-align: center; }");
        emailBodyBuilder.append("</style>");
        emailBodyBuilder.append("</head>");
        emailBodyBuilder.append("<body>");
        emailBodyBuilder.append("<div class=\"container\">");
        emailBodyBuilder.append("<div class=\"header\">");
        emailBodyBuilder.append("<h2>Bienvenue chez Orange Digital Center</h2>");
        emailBodyBuilder.append("</div>");
        emailBodyBuilder.append("<div class=\"content\">");
        emailBodyBuilder.append("<p>Bonjour ").append(utilisateur.getPrenom()).append(" ").append(utilisateur.getNom()).append(",</p>");
        emailBodyBuilder.append("<p>Nous sommes ravis de vous compter parmi nous. Votre compte a été créé avec succès.</p>");
        emailBodyBuilder.append("<p><strong>Nom d'utilisateur :</strong> ").append(utilisateur.getEmail()).append("</p>");
        emailBodyBuilder.append("<p><strong>Mot de Passe :</strong> ").append(rawPassword).append("</p>");
        emailBodyBuilder.append("<p>Pour commencer, veuillez vous connecter à votre compte en utilisant vos identifiants.</p>");
        emailBodyBuilder.append("<p>Si vous avez des questions, n'hésitez pas à contacter notre support.</p>");
        emailBodyBuilder.append("</div>");
        emailBodyBuilder.append("<div class=\"footer\">");
        emailBodyBuilder.append("<p>L'équipe <strong>ODC</strong></p>");
        emailBodyBuilder.append("<p>Ceci est un email automatisé. Merci de ne pas y répondre.</p>");
        emailBodyBuilder.append("</div>");
        emailBodyBuilder.append("</div>");
        emailBodyBuilder.append("</body>");
        emailBodyBuilder.append("</html>");

        String emailBody = emailBodyBuilder.toString();
//        emailService.sendSimpleEmail(utilisateur.getEmail(), "Confirmation de création de compte", emailBody);


        return savedUtilisateur;
    }
    public UtilisateurDTO add2(UtilisateurDTO userdto) {
        if (userdto.getEmail() == null || userdto.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'email est obligatoire.");
        }
        String email = userdto.getEmail().trim().toLowerCase();
        if (!UtilService.isValidEmail(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Votre mail est invalide");
        }
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Cet email est déjà utilisé.");
        }

        Long roleId = resolveRoleIdFromDto(userdto);
        Long entiteId = resolveEntiteIdFromDto(userdto);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Le rôle sélectionné n'existe pas (id=" + roleId + "). Créez-le dans Administration > Rôle ou redéployez le backend."));
        Entite entite = entiteOdcRepository.findById(entiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "L'entité sélectionnée n'existe pas (id=" + entiteId + ")."));

        String defaultPassword = "motdepasse123";
        String rawPassword = userdto.getPassword() != null && !userdto.getPassword().isBlank()
                ? userdto.getPassword()
                : defaultPassword;
        String encodedPassword = passwordEncoder.encode(rawPassword);

        Utilisateur user = new Utilisateur();
        user.setNom(userdto.getNom());
        user.setPrenom(userdto.getPrenom());
        user.setEmail(email);
        user.setGenre(userdto.getGenre());
        user.setPassword(encodedPassword);
        user.setPhone(userdto.getPhone());
        user.setRole(role);
        user.setEntite(entite);
        user.setEtat(true);

        Utilisateur usersaved = utilisateurRepository.save(user);
        return new UtilisateurDTO(
                usersaved.getId(),
                usersaved.getNom(),
                usersaved.getPrenom(),
                usersaved.getEmail(),
                usersaved.getGenre(),
                usersaved.getPassword(),
                "",
                usersaved.getPhone(),
                usersaved.getRole(),
                usersaved.getEntite(),
                true);
    }

    private Long resolveRoleIdFromDto(UtilisateurDTO dto) {
        if (dto.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le rôle est obligatoire.");
        }
        if (dto.getRole().getId() != null) {
            return dto.getRole().getId();
        }
        if (dto.getRole().getNom() != null && !dto.getRole().getNom().isBlank()) {
            return roleRepository.findByNom(dto.getRole().getNom().trim())
                    .map(Role::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Le rôle « " + dto.getRole().getNom() + " » n'existe pas en base."));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant de rôle manquant.");
    }

    private Long resolveEntiteIdFromDto(UtilisateurDTO dto) {
        if (dto.getEntite() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'entité est obligatoire.");
        }
        if (dto.getEntite().getId() != null) {
            return dto.getEntite().getId();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant d'entité manquant.");
    }
    
   
    
    
    @Override
    public List<Utilisateur> List() {
//        return utilisateurRepository.findAll();
        return utilisateurRepository.findAllByEtat(true);
    }

    public List<UtilisateurDTO> getAllUtilisateur() {
        return utilisateurRepository.findAll().stream()
                .map(this::convertToDTO) // Conversion de l'entité à DTO
                .collect(Collectors.toList());
    }

    private UtilisateurDTO convertToDTO(Utilisateur utilisateur) {
        return new UtilisateurDTO(
                utilisateur.getId(),
                utilisateur.getNom(),
                utilisateur.getPrenom(),
                utilisateur.getEmail(),
                utilisateur.getPhone(),
                utilisateur.getGenre(),
                utilisateur.getPassword(),
                "",
                utilisateur.getRole(),
                (Entite) utilisateur.getEntite(),
                utilisateur.getEtat()
               
        );
    }

    @Override
    public Optional<Utilisateur> findById(Long id) {
        return utilisateurRepository.findById(id);
    }

    @Override
    public Utilisateur update(Utilisateur utilisateur, Long id) {
        return utilisateurRepository.findById(id).map(
                p -> {
                    p.setNom(utilisateur.getNom());
                    p.setEmail(utilisateur.getEmail());
                    p.setPrenom(utilisateur.getPrenom());
                    p.setPhone(utilisateur.getPhone());
                    p.setGenre(utilisateur.getGenre());

                    // Vérifiez si le rôle est null avant de le définir
                    if (utilisateur.getRole() != null) {
                        p.setRole(utilisateur.getRole());
                    }

                    if (utilisateur.getEntite() != null) {
                        p.setEntite(utilisateur.getEntite());
                    }

                    // Si le mot de passe est modifié, encodez-le
                    if (utilisateur.getPassword() != null) {
                        p.setPassword(passwordEncoder.encode(utilisateur.getPassword()));
                    }

                    return utilisateurRepository.save(p);
                }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Votre id n'existe pas"));
    }
    
    
    public Utilisateur updateDTO(UtilisateurDTO utilisateur, Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié.");
        }
        Utilisateur principal = utilisateurRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable."));
        boolean fullAccess = isAdminOrSuperadmin(principal);
        if (!fullAccess && !Objects.equals(principal.getId(), id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous ne pouvez modifier que votre propre profil.");
        }
        if (!fullAccess) {
            utilisateur.setRole(null);
            utilisateur.setEntite(null);
            utilisateur.setEtat(null);
        }

        System.out.println("mon userDTO++++++++++"+utilisateur);
        return utilisateurRepository.findById(id).map(
                p -> {
                    p.setNom(utilisateur.getNom());
                    p.setEmail(utilisateur.getEmail());
                    p.setPrenom(utilisateur.getPrenom());
                    p.setPhone(utilisateur.getPhone());
                    if (utilisateur.getEtat() != null) {
                        p.setEtat(utilisateur.getEtat());
                    }
                    if(utilisateur.getGenre()!=null){
                     p.setGenre(utilisateur.getGenre());
                    }

                     if(utilisateur.getEntite()!=null){
                    entiteOdcRepository.findById(utilisateur.getEntite().getId()).ifPresent(p::setEntite);

                }
                    if (utilisateur.getRole() != null && utilisateur.getRole().getId() != null) {
                        roleRepository.findById(utilisateur.getRole().getId()).ifPresent(p::setRole);
                    } else if (utilisateur.getRole() != null && utilisateur.getRole().getNom() != null) {
                        roleRepository.findByNom(utilisateur.getRole().getNom()).ifPresent(p::setRole);
                    }

                    boolean veutChangerMdp = utilisateur.getPassword() != null && !utilisateur.getPassword().isBlank()
                            && utilisateur.getNewpassword() != null && !utilisateur.getNewpassword().isBlank();
                    if (veutChangerMdp) {
                        if (!passwordEncoder.matches(utilisateur.getPassword(), p.getPassword())) {
                            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Mot de passe actuel incorrect.");
                        }
                        p.setPassword(passwordEncoder.encode(utilisateur.getNewpassword()));
                    }
                    return utilisateurRepository.save(p);
                }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Votre id n'existe pas"));
    }

    private static boolean isAdminOrSuperadmin(Utilisateur u) {
        if (u.getRole() == null || u.getRole().getNom() == null) {
            return false;
        }
        String n = u.getRole().getNom().trim().toUpperCase();
        return "SUPERADMIN".equals(n) || "ADMIN".equals(n);
    }
    
    

    @Override
    public void delete(Long id) {
        Optional<Utilisateur> optionalUtilisateur = utilisateurRepository.findById(id);
        optionalUtilisateur.ifPresent(personnel -> utilisateurRepository.deleteById(id));

    }

    public long getNombreUtilisateurs() {
        return utilisateurRepository.count(); // Retourne le nombre d'utilisateurs
    }

    public void modifierMotDePasse(Map<String, String> parametres) {
        String ancienMotDePasse = parametres.get("ancienPassword");
        String nouveauMotDePasse = parametres.get("newPassword");

        // Obtenir l'utilisateur connecté
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName(); // On suppose que le nom de l'utilisateur est son email

        Optional<Utilisateur> optionalUtilisateur = utilisateurRepository.findByEmail(email);

        if (optionalUtilisateur.isPresent()) {
            Utilisateur utilisateur = optionalUtilisateur.get();

            // Vérifier si l'ancien mot de passe est correct
            if (passwordEncoder.matches(ancienMotDePasse, utilisateur.getPassword())) {
                // Vérifiez si le nouveau mot de passe est différent de l'ancien
                if (!ancienMotDePasse.equals(nouveauMotDePasse)) {
                    utilisateur.setPassword(passwordEncoder.encode(nouveauMotDePasse));
                    utilisateurRepository.save(utilisateur);
                } else {
                    throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Le nouveau mot de passe ne peut pas être le même que l'ancien.");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "L'ancien mot de passe est incorrect.");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur avec cet email n'existe pas.");
        }
    }



}
