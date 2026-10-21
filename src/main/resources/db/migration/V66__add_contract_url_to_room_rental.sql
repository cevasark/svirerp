-- V66__add_contract_url_to_room_rental.sql
-- Add contract_url column to room_rental to store a link to the rental contract document

ALTER TABLE room_rental
    ADD COLUMN contract_url VARCHAR(1000);
