--liquibase formatted sql
--changeset wealth:008

ALTER TABLE account_valuations ADD COLUMN invested_money NUMERIC(19, 2);