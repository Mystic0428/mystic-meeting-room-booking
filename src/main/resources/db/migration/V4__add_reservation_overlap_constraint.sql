CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservations
    ADD CONSTRAINT no_overlapping_reservation
    EXCLUDE USING gist (
        room_id WITH =,
        tsrange(start_time, end_time) WITH &&
    )
    WHERE (status IN ('PROCESSING', 'APPROVED', 'CANCEL_REQUESTED'));