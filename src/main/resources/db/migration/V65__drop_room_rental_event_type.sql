-- V65__drop_room_rental_event_type.sql
-- Remove unneeded event_type column from room_rental table

ALTER TABLE room_rental
    DROP COLUMN event_type;
