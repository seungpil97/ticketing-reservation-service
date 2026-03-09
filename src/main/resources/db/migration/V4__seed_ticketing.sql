INSERT INTO event (name, venue, status)
VALUES ('Concert 2026', 'Seoul Arena', 'ON_SALE');

INSERT INTO showtime (event_id, show_at)
VALUES
    (1, '2026-03-10 19:00:00'),
    (1, '2026-03-11 19:00:00');

INSERT INTO seat (seat_number, grade, row_label, seat_no)
VALUES
    ('A1', 'VIP', 'A', 1),
    ('A2', 'VIP', 'A', 2),
    ('A3', 'VIP', 'A', 3),
    ('A4', 'VIP', 'A', 4),
    ('A5', 'R', 'A', 5),
    ('A6', 'R', 'A', 6),
    ('A7', 'R', 'A', 7),
    ('A8', 'R', 'A', 8),
    ('A9', 'R', 'A', 9),
    ('A10', 'R', 'A', 10),
    ('A11', 'S', 'A', 11),
    ('A12', 'S', 'A', 12),
    ('A13', 'S', 'A', 13),
    ('A14', 'S', 'A', 14),
    ('A15', 'S', 'A', 15),
    ('A16', 'S', 'A', 16),
    ('A17', 'S', 'A', 17),
    ('A18', 'S', 'A', 18),
    ('A19', 'S', 'A', 19),
    ('A20', 'S', 'A', 20);

INSERT INTO seat_grade_price (event_id, grade, price)
VALUES
    (1, 'VIP', 150000),
    (1, 'R', 120000),
    (1, 'S', 90000);

INSERT INTO showtime_seat (showtime_id, seat_id, status)
SELECT 1, id, 'AVAILABLE'
FROM seat
ORDER BY id;

INSERT INTO showtime_seat (showtime_id, seat_id, status)
SELECT 2, id, 'AVAILABLE'
FROM seat
ORDER BY id;