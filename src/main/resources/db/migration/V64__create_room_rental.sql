-- V64__create_room_rental.sql
-- Room rentals for church facilities (Parish Hall, meeting spaces)
-- Supports the 4-step trustee workflow: requested -> slot_confirmed -> deposit_paid -> confirmed

CREATE TABLE room_rental (
    id                     CHAR(36)       PRIMARY KEY DEFAULT (UUID()),
    room_name              VARCHAR(100)   NOT NULL DEFAULT 'Parish Hall',
    renter_person_id       CHAR(36)       NOT NULL,
    contact_trustee_id     CHAR(36),
    event_title            VARCHAR(150)   NOT NULL,
    start_time             DATETIME       NOT NULL,
    end_time               DATETIME       NOT NULL,
    estimated_attendees    INT,
    status                 VARCHAR(30)    NOT NULL DEFAULT 'requested'
                           CHECK (status IN ('requested', 'slot_confirmed', 'deposit_paid', 'confirmed', 'cancelled', 'completed')),
    total_fee              DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    deposit_amount         DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    deposit_paid_date      DATE,
    deposit_payment_method VARCHAR(30)    CHECK (deposit_payment_method IN ('cash', 'check', 'zelle', 'card', 'other')),
    final_amount_paid      DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    final_payment_date     DATE,
    final_payment_method   VARCHAR(30)    CHECK (final_payment_method IN ('cash', 'check', 'zelle', 'card', 'other')),
    notes                  TEXT,
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_rental_renter  FOREIGN KEY (renter_person_id)   REFERENCES person (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_room_rental_trustee FOREIGN KEY (contact_trustee_id) REFERENCES trustee (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_room_rental_times   ON room_rental (start_time, end_time);
CREATE INDEX idx_room_rental_status  ON room_rental (status);
CREATE INDEX idx_room_rental_trustee ON room_rental (contact_trustee_id);
