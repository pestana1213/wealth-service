--liquibase formatted sql
--changeset wealth:012

  ALTER TABLE users ADD COLUMN issuer  VARCHAR(255);
  ALTER TABLE users ADD COLUMN subject VARCHAR(255);
  CREATE UNIQUE INDEX users_identity_unique ON users (issuer, subject);