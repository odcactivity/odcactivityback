-- À exécuter sur la base MySQL si la migration automatique au démarrage échoue
-- (utilisateur RDS sans droit ALTER, etc.).
-- Corrige : Data truncated for column 'statut'

ALTER TABLE courrier MODIFY COLUMN statut VARCHAR(64) NOT NULL;
ALTER TABLE historique_courrier MODIFY COLUMN statut VARCHAR(64) NOT NULL;
ALTER TABLE reponse_courrier MODIFY COLUMN statut VARCHAR(64) NOT NULL;
