--liquibase formatted sql
--changeset wealth:003
-- Existing users have no known email; preserve them without fabricating addresses.
-- New registrations require email through request and entity validation.
ALTER TABLE users ADD COLUMN email VARCHAR(254);
CREATE UNIQUE INDEX users_email_unique ON users (LOWER(email));
