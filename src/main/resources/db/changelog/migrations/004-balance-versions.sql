--liquibase formatted sql
--changeset wealth:004
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE buckets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
