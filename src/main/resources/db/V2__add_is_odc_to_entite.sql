-- Migration to add is_odc flag to entite table
-- Executed on AWS Elastic Beanstalk (MySQL)
ALTER TABLE entite
    ADD COLUMN is_odc BOOLEAN NOT NULL DEFAULT FALSE;
