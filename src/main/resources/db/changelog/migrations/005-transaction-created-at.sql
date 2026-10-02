--liquibase formatted sql
--changeset wealth:005
ALTER TABLE transactions ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
