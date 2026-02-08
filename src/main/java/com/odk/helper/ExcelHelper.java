package com.odk.helper;

import com.odk.Entity.Activite;
import com.odk.Entity.ActiviteParticipant;
import com.odk.Entity.ActiviteParticipantKey;
import com.odk.Entity.Etape;
import com.odk.Entity.Liste;
import com.odk.Entity.Participant;
import com.odk.Repository.ActiviteParticipantRepository;
import com.odk.Repository.ActiviteRepository;
import com.odk.Repository.BlackListRepository;
import com.odk.Repository.EtapeRepository;
import com.odk.Repository.ListeRepository;
import com.odk.Repository.ParticipantRepository;
import com.odk.dto.ImportReponse;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


@AllArgsConstructor
public class ExcelHelper {

    public static String TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static String[] HEADERs = {"Nom", "Prenom", "Email", "Phone", "Genre", "Activite" };
    static String SHEET = "Participants";
   static int nombredeblacklist=0;
   static int total=0;
   static int importe=0;
  


    public static boolean hasExcelFormat(MultipartFile file) {

        if (!TYPE.equals(file.getContentType())) {
            return false;
      }

        return true;
    }

    public static ByteArrayInputStream tutorialsToExcel(List<Participant> participants) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {
            Sheet sheet = workbook.createSheet(SHEET);

            // Header
            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < HEADERs.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(HEADERs[col]);
            }

            int rowIdx = 1;
            for (Participant participant : participants) {
                Row row = sheet.createRow(rowIdx++);

//                row.createCell(0).setCellValue(participant.getId());
                row.createCell(0).setCellValue(participant.getNom());
                row.createCell(1).setCellValue(participant.getPrenom());
                row.createCell(2).setCellValue(participant.getEmail());
                row.createCell(3).setCellValue(participant.getPhone());
                row.createCell(5).setCellValue(participant.getGenre());
                row.createCell(6).setCellValue(participant.getActivite() != null ? participant.getActivite().getNom() : "N/A");
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("fail to import data to Excel file: " + e.getMessage());
        }
    }

    public static List<Participant> excelToTutorials(Etape e,MultipartFile is, ActiviteRepository activiteRepository, ActiviteParticipantRepository activiteParticipantRepository, ParticipantRepository participantRepository,BlackListRepository blackListRepository ) throws IOException {

        XSSFWorkbook workbook = new XSSFWorkbook(is.getInputStream());
        XSSFSheet worksheet = (XSSFSheet) workbook.getSheetAt(0);
        List<Participant> participants = new ArrayList<>();

        for(int i=1;i<worksheet.getPhysicalNumberOfRows() ;i++) {
            Participant participant = new Participant();

            XSSFRow row = worksheet.getRow(i);

//            participant.setId((long) row.getCell(0).getNumericCellValue());
            participant.setNom(row.getCell(0).getStringCellValue());
            participant.setPrenom(row.getCell(1).getStringCellValue());
            participant.setEmail(row.getCell(2).getStringCellValue());
            participant.setGenre(row.getCell(3).getStringCellValue());
            // Vérifiez si la cellule du téléphone est de type numérique ou chaîne
            if (row.getCell(3).getCellType() == CellType.NUMERIC) {
                // Si c'est un nombre, on le convertit en String
                participant.setPhone(String.valueOf((int) row.getCell(4).getNumericCellValue()));
            } else if (row.getCell(4).getCellType() == CellType.STRING) {
                // Si c'est déjà une chaîne de caractères
                participant.setPhone(row.getCell(4).getStringCellValue());
            } else {
                throw new RuntimeException("Type de cellule inattendu pour le téléphone");
            }
          

            // Vérification si la cellule de l'activité est non nulle et de type chaîne
//            if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.STRING) {
//                String nomActivite = row.getCell(5).getStringCellValue();
            if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.STRING) {
                String nomActivite = row.getCell(5).getStringCellValue().trim(); // Suppression des espaces
//                Optional<Activite> activiteOptional = activiteRepository.findByNom(nomActivite);
                Optional<Activite> activiteOptional = activiteRepository.findByNomIgnoreCase(nomActivite); //Comparaison insensible a la case.

                if (activiteOptional.isPresent()) {
                    if(e.getActivite()==null || !Objects.equals(activiteOptional.get().getId(), e.getActivite().getId())){
                     throw new RuntimeException("L'etape \"" + e.getNom() + " \" n'est pas liée à l'activite \""+nomActivite+" \"");
  
                    }
//                    System.out.println(activiteOptional.get().getNom());
                    participant.setActivite(activiteOptional.get());
                    if(blackListRepository.findByEmail(participant.getEmail()).get()!=null || blackListRepository.findByPhone(participant.getPhone()).get()!=null){
                        nombredeblacklist++;
                    }else{
                         // Enregistrez le participant d'abord pour obtenir un ID valide
                    participantRepository.save(participant);
                    // Création de la clé composite pour l'entité ActiviteParticipant
                    ActiviteParticipant activiteParticipant = new ActiviteParticipant();
                    ActiviteParticipantKey key = new ActiviteParticipantKey();
                    key.setActiviteId(activiteOptional.get().getId());
                    key.setParticipantId(participant.getId()); // Participant ID devrait maintenant être défini

                    activiteParticipant.setId(key);
                    activiteParticipant.setActivite(activiteOptional.get());
                    activiteParticipant.setParticipant(participant);
                    activiteParticipant.setDate(LocalDate.now()); // Définissez la date

                    // Enregistrement de l'ActiviteParticipant
                    activiteParticipantRepository.save(activiteParticipant);
                    
                    }
                   

                    
                } else {
                    throw new RuntimeException("L'activité " + nomActivite + " n'existe pas dans la base de données.");
                }
            } else {
                throw new RuntimeException("La cellule de l'activité est vide ou contient un type incorrect.");
            }



            participants.add(participant);
        }

        return participants;
    }

    public static ImportReponse excelToTutorialsNew(Etape e,MultipartFile is, ActiviteRepository activiteRepository, ActiviteParticipantRepository activiteParticipantRepository, ParticipantRepository participantRepository,BlackListRepository blackListRepository,boolean typeliste,ListeRepository listeRepository, EtapeRepository etapeRepository) throws IOException {

        XSSFWorkbook workbook = new XSSFWorkbook(is.getInputStream());
        XSSFSheet worksheet = (XSSFSheet) workbook.getSheetAt(0);
        List<Participant> participants = new ArrayList<>();

        for(int i=1;i<worksheet.getPhysicalNumberOfRows() ;i++) {
            Participant participant = new Participant();
            XSSFRow row = worksheet.getRow(i);
            total++;
//            participant.setId((long) row.getCell(0).getNumericCellValue());
            participant.setNom(row.getCell(0).getStringCellValue());
            participant.setPrenom(row.getCell(1).getStringCellValue());
            participant.setEmail(row.getCell(2).getStringCellValue());
            participant.setGenre(row.getCell(3).getStringCellValue());
            // Vérifiez si la cellule du téléphone est de type numérique ou chaîne
            if (row.getCell(3).getCellType() == CellType.NUMERIC) {
                // Si c'est un nombre, on le convertit en String
                participant.setPhone(String.valueOf((int) row.getCell(4).getNumericCellValue()));
            } else if (row.getCell(4).getCellType() == CellType.STRING) {
                // Si c'est déjà une chaîne de caractères
                participant.setPhone(row.getCell(4).getStringCellValue());
            } else {
                throw new RuntimeException("Type de cellule inattendu pour le téléphone");
            }
          

           
            if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.STRING) {
                String nomActivite = row.getCell(5).getStringCellValue().trim(); // Suppression des espaces
//                Optional<Activite> activiteOptional = activiteRepository.findByNom(nomActivite);
                Optional<Activite> activiteOptional = activiteRepository.findByNomIgnoreCase(nomActivite); //Comparaison insensible a la case.

                if (activiteOptional.isPresent()) {
                    if(e.getActivite()==null || !Objects.equals(activiteOptional.get().getId(), e.getActivite().getId())){
                     throw new RuntimeException("L'etape \"" + e.getNom() + " \" n'est pas liée à l'activite \""+nomActivite+" \"");
  
                    }
//                    System.out.println(activiteOptional.get().getNom());
//                    System.out.println("les données de participant========="+participant.getEmail()+""+participant.getPhone());

                    participant.setActivite(activiteOptional.get());
                    if((blackListRepository.findByEmail(participant.getEmail()).isPresent() && blackListRepository.findByEmail(row.getCell(2).getStringCellValue()).get()!=null) || (blackListRepository.findByPhone(participant.getPhone()).isPresent() &&  blackListRepository.findByPhone(participant.getPhone()).get()!=null)){
                        nombredeblacklist++;

                    }else{
                         // Enregistrez le participant d'abord pour obtenir un ID valide
                    participantRepository.save(participant);
                    importe++;
                    participants.add(participant);
//                System.out.println("Fin import ========="+total+"-"+importe+"-"+nombredeblacklist);

                    // Création de la clé composite pour l'entité ActiviteParticipant
                    ActiviteParticipant activiteParticipant = new ActiviteParticipant();
                    ActiviteParticipantKey key = new ActiviteParticipantKey();
                    key.setActiviteId(activiteOptional.get().getId());
                    key.setParticipantId(participant.getId()); // Participant ID devrait maintenant être défini

                    activiteParticipant.setId(key);
                    activiteParticipant.setActivite(activiteOptional.get());
                    activiteParticipant.setParticipant(participant);
                    activiteParticipant.setDate(LocalDate.now()); // Définissez la date

                    // Enregistrement de l'ActiviteParticipant
                    activiteParticipantRepository.save(activiteParticipant);
                    
                    }
                                     
                } else {
                    throw new RuntimeException("L'activité " + nomActivite + " n'existe pas dans la base de données.");
                }
            } else {
                throw new RuntimeException("La cellule de l'activité est vide ou contient un type incorrect.");
            }
            
        }
        List<Liste> listes = e.getListes(); // Récupérer le Set de Listes
        Liste listeAMettreAJour = null;
//Verification du type de liste
        if (typeliste) {
            listeAMettreAJour = listes.stream().filter(Liste::isListeDebut).findFirst().orElse(null);
            if (listeAMettreAJour == null) {
                listeAMettreAJour = new Liste();
                listeAMettreAJour.setEtape(e);
                listeAMettreAJour.setListeDebut(true);
                listeAMettreAJour.setDateHeure(LocalDateTime.now());
                listes.add(listeAMettreAJour); // Ajouter la nouvelle Liste au Set
                listeRepository.save(listeAMettreAJour); // Enregistrer la Liste mise à jour
            }
        } else {
            listeAMettreAJour = listes.stream().filter(Liste::isListeResultat).findFirst().orElse(null);
            if (listeAMettreAJour == null) {
                listeAMettreAJour = new Liste();
                listeAMettreAJour.setEtape(e);
                listeAMettreAJour.setListeResultat(true);
                listeAMettreAJour.setDateHeure(LocalDateTime.now());
                listes.add(listeAMettreAJour); // Ajouter la nouvelle Liste au Set
                listeRepository.save(listeAMettreAJour); // Enregistrer la Liste mise à jour
            }
        }

        if (listeAMettreAJour != null) { // S'assurer que vous avez trouvé ou créé une Liste
            for (Participant participant : participants) {
                participant.setListe(listeAMettreAJour);
            }
            
//            etapeRepository.save(e); // Enregistrer l'Etape (se propage en cascade)
        }

        // Sauvegarder les participants et l'étape dans la base de données
        participantRepository.saveAll(participants);
        etapeRepository.save(e);
              
        return new ImportReponse(total, importe, (total-importe));
    }
}
