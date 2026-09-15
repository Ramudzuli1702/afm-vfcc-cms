-- ═══════════════════════════════════════════════════════════════
-- AFM VFCC CMS — DEMO DATA
--
-- Entirely fictional sample data for evaluating the system: ~40 members,
-- a handful of welfare cases, events, and inventory items. No real church
-- or member records of any kind are included in this file.
--
-- HOW TO USE:
--   1. Install/run the CMS and complete the first-run Database Setup
--      wizard as normal (creates the schema + your own real admin account).
--   2. In the CMS: Settings > Backup > "Restore Backup..." > select this
--      file. NOTE: this REPLACES all current data in the database, so
--      only do this on a fresh/demo install, never on a real church DB.
--   3. Log in with one of the demo accounts below, or your own account
--      created in step 1 if you kept it.
--
-- DEMO LOGINS (desktop CMS + mobile app both use the same accounts):
--   Admin  — username: demo_admin   password: Demo@1234
--   Usher  — username: demo_usher   password: Usher@1234   (mobile app only)
--   *** Change or remove these accounts before using the system for real. ***
-- ═══════════════════════════════════════════════════════════════

SET FOREIGN_KEY_CHECKS = 0;
SET NAMES utf8mb4;

-- ── Sub-branches ──────────────────────────────────────────────
DELETE FROM sub_branches;
INSERT INTO sub_branches (id, name, is_active) VALUES
(1, 'Ha-Kutama', 1),
(2, 'Tshikwarani', 1),
(3, 'Makhado Central', 1),
(4, 'Thohoyandou', 1),
(5, 'Youth Campus', 1);

-- ── Ministries (same ids the Dashboard's ministry chart queries by name) ──
DELETE FROM ministries;
INSERT INTO ministries (id, name, description, is_active) VALUES
(1, 'Women''s Ministry', 'Fellowship and outreach for the women of the church', 1),
(2, 'Men''s Ministry', 'Fellowship and outreach for the men of the church', 1),
(3, 'Youth', 'Ministry for teenagers and young adults', 1),
(4, 'Sunday School', 'Bible teaching for children', 1);

-- ── Users (demo login accounts) ──────────────────────────────
DELETE FROM users;
INSERT INTO users (id, full_name, username, password_hash, email, phone, role_title, is_super_admin, is_active, account_role) VALUES
(1, 'Demo Admin', 'demo_admin', '$2a$12$8M7Q2FKk6yaq4h.hp1SS.O9LPmKN9ab1giiuvk6oyNP1jmZcRGUKK', 'demo_admin@example.com', '0710000001', 'Administrator', 1, 1, 'admin'),
(2, 'Demo Usher', 'demo_usher', '$2a$12$kHqE5BQEJFt19s3iLW2p6u01FThPDYwHLMzNEaGaNRjdicNVDJGNm', 'demo_usher@example.com', '0710000002', 'Usher', 0, 1, 'usher');

-- ── Members (40 fictional records) ───────────────────────────
DELETE FROM members;
INSERT INTO members
(id, full_name, date_of_birth, gender, marital_status, employment_status, sub_branch_id, phone, is_full_time, is_active, date_joined, baptism_date) VALUES
(1,  'Mulalo Mudau',          '1988-03-14', 'Male',   'Married',  'Employed',    1, '0710000101', 1, 1, '2019-02-10', '2019-06-01'),
(2,  'Lufuno Netshitenzhe',   '2001-07-22', 'Female', 'Single',   'Student',     2, '0710000102', 0, 1, '2021-08-15', NULL),
(3,  'Thendo Ramaru',         '1995-11-02', 'Male',   'Single',   'Employed',    1, '0710000103', 1, 1, '2018-04-03', '2018-09-09'),
(4,  'Ndivhuwo Sinthumule',   '1990-05-19', 'Female', 'Married',  'Employed',    3, '0710000104', 1, 1, '2017-01-22', '2017-05-14'),
(5,  'Khathutshelo Mathoho',  '1958-09-30', 'Male',   'Widowed',  'Retired',     1, '0710000105', 1, 1, '2010-03-01', '2010-08-08'),
(6,  'Vhonani Mulaudzi',      '1993-12-08', 'Female', 'Single',   'Employed',    4, '0710000106', 1, 1, '2020-06-18', NULL),
(7,  'Rendani Tshivhase',     '1985-02-27', 'Male',   'Married',  'Employed',    2, '0710000107', 1, 1, '2015-09-09', '2016-01-10'),
(8,  'Fhatuwani Nemakonde',   '1992-08-16', 'Female', 'Divorced', 'Employed',    3, '0710000108', 1, 1, '2019-11-11', NULL),
(9,  'Azwindini Ligavha',     '2003-01-05', 'Male',   'Single',   'Student',     5, '0710000109', 0, 1, '2022-02-14', NULL),
(10, 'Nyaladzi Nemutandani',  '1980-04-11', 'Female', 'Married',  'Unemployed',  1, '0710000110', 1, 1, '2012-07-07', '2012-12-25'),
(11, 'Tshifhiwa Mavhungu',    '1997-06-23', 'Male',   'Single',   'Employed',    2, '0710000111', 1, 1, '2021-03-03', NULL),
(12, 'Phumudzo Rasila',       '1989-10-14', 'Female', 'Married',  'Employed',    4, '0710000112', 1, 1, '2016-05-05', '2016-10-10'),
(13, 'Rotondwa Munyai',       '1998-03-29', 'Male',   'Single',   'Employed',    1, '0710000113', 1, 1, '2020-01-19', NULL),
(14, 'Mpho Baloyi',           '1994-07-07', 'Female', 'Single',   'Employed',    3, '0710000114', 1, 1, '2018-08-08', '2019-02-02'),
(15, 'Tshegofatso Mahlangu',  '1986-12-01', 'Male',   'Married',  'Employed',    2, '0710000115', 1, 1, '2014-06-06', '2015-01-01'),
(16, 'Lutendo Ravhuhali',     '1955-05-05', 'Female', 'Widowed',  'Retired',     1, '0710000116', 1, 1, '2009-09-09', '2010-03-03'),
(17, 'Murendeni Nefale',      '1991-02-18', 'Male',   'Married',  'Employed',    4, '0710000117', 1, 1, '2017-10-10', NULL),
(18, 'Shudufhadzo Ramabulana','2000-09-09', 'Female', 'Single',   'Student',     5, '0710000118', 0, 1, '2022-08-20', NULL),
(19, 'Ndamulelo Mmbara',      '1983-11-27', 'Male',   'Married',  'Employed',    2, '0710000119', 1, 1, '2013-04-14', '2013-09-09'),
(20, 'Takalani Mukwevho',     '1996-06-06', 'Female', 'Single',   'Employed',    3, '0710000120', 1, 1, '2019-07-17', NULL),
(21, 'Aluwani Tshikovhi',     '1987-01-30', 'Male',   'Married',  'Unemployed',  1, '0710000121', 1, 1, '2015-02-02', NULL),
(22, 'Zwivhuya Manavhela',    '1999-04-04', 'Female', 'Single',   'Employed',    4, '0710000122', 1, 1, '2021-01-01', NULL),
(23, 'Denga Rambau',          '2002-08-08', 'Male',   'Single',   'Student',     5, '0710000123', 0, 1, '2022-05-15', NULL),
(24, 'Mukondeleli Mukwevho',  '1990-10-20', 'Female', 'Married',  'Employed',    2, '0710000124', 1, 1, '2016-11-11', '2017-04-04'),
(25, 'Hulisani Netshifhefhe', '1984-03-03', 'Male',   'Married',  'Employed',    3, '0710000125', 1, 1, '2014-01-01', '2014-06-06'),
(26, 'Vhutshilo Nengovhela',  '1997-09-19', 'Female', 'Single',   'Employed',    1, '0710000126', 1, 1, '2020-09-09', NULL),
(27, 'Wanga Baloyi',          '1988-07-27', 'Male',   'Married',  'Employed',    4, '0710000127', 1, 1, '2018-03-03', '2018-08-08'),
(28, 'Ntsako Chauke',         '1995-01-15', 'Female', 'Single',   'Employed',    2, '0710000128', 1, 1, '2019-06-06', NULL),
(29, 'Rirhandzu Ngobeni',     '1986-05-25', 'Male',   'Married',  'Employed',    3, '0710000129', 1, 1, '2015-12-12', '2016-05-05'),
(30, 'Hlayisani Shirinda',    '1960-02-02', 'Female', 'Widowed',  'Retired',     1, '0710000130', 1, 1, '2011-01-01', '2011-07-07'),
(31, 'Kgothatso Sekgobela',   '2001-11-11', 'Male',   'Single',   'Student',     5, '0710000131', 0, 1, '2022-09-09', NULL),
(32, 'Refilwe Maake',         '1992-06-16', 'Female', 'Married',  'Employed',    4, '0710000132', 1, 1, '2017-07-07', '2018-01-01'),
(33, 'Katlego Mphahlele',     '1989-09-29', 'Male',   'Married',  'Employed',    2, '0710000133', 1, 1, '2016-02-02', '2016-08-08'),
(34, 'Lesedi Ramaphakela',    '1998-12-12', 'Female', 'Single',   'Employed',    3, '0710000134', 1, 1, '2020-04-04', NULL),
(35, 'Thabo Nkoana',          '1985-08-21', 'Male',   'Married',  'Employed',    1, '0710000135', 1, 1, '2013-11-11', '2014-04-04'),
(36, 'Dimakatso Sethole',     '1994-03-08', 'Female', 'Single',   'Unemployed',  4, '0710000136', 1, 1, '2019-10-10', NULL),
(37, 'Given Netshialwanda',   '1991-07-13', 'Male',   'Married',  'Employed',    2, '0710000137', 1, 1, '2017-03-03', '2017-09-09'),
(38, 'Precious Ndou',         '1996-10-26', 'Female', 'Single',   'Employed',    3, '0710000138', 1, 1, '2020-12-12', NULL),
(39, 'Ndivho Rabothata',      '1983-04-17', 'Male',   'Married',  'Employed',    1, '0710000139', 1, 1, '2012-10-10', '2013-03-03'),
(40, 'Vhutshilo Mmbengwa',    DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 24 YEAR), 'Female', 'Single', 'Student', 5, '0710000140', 0, 1, '2022-01-20', NULL);

-- Two members get a birthday that always falls a few days from "today" —
-- whenever this demo is restored, the Dashboard's Birthdays widget shows
-- something right away instead of looking empty.
UPDATE members SET date_of_birth = DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 4 DAY), INTERVAL 31 YEAR) WHERE id = 6;

-- ── Member ↔ Ministry assignments ────────────────────────────
DELETE FROM member_ministries;
INSERT INTO member_ministries (member_id, ministry_id, joined_date) VALUES
(2,3,'2021-08-15'),(4,1,'2017-01-22'),(6,1,'2020-06-18'),(8,1,'2019-11-11'),
(9,3,'2022-02-14'),(10,1,'2012-07-07'),(12,1,'2016-05-05'),(14,1,'2018-08-08'),
(16,1,'2009-09-09'),(18,3,'2022-08-20'),(20,1,'2019-07-17'),(22,1,'2021-01-01'),
(23,3,'2022-05-15'),(24,1,'2016-11-11'),(26,1,'2020-09-09'),(28,1,'2019-06-06'),
(30,1,'2011-01-01'),(31,3,'2022-09-09'),(32,1,'2017-07-07'),(34,1,'2020-04-04'),
(36,1,'2019-10-10'),(38,1,'2020-12-12'),(40,3,'2022-01-20'),
(1,2,'2019-02-10'),(3,2,'2018-04-03'),(5,2,'2010-03-01'),(7,2,'2015-09-09'),
(11,2,'2021-03-03'),(13,2,'2020-01-19'),(15,2,'2014-06-06'),(17,2,'2017-10-10'),
(19,2,'2013-04-14'),(21,2,'2015-02-02'),(25,2,'2014-01-01'),(27,2,'2018-03-03'),
(29,2,'2015-12-12'),(33,2,'2016-02-02'),(35,2,'2013-11-11'),(37,2,'2017-03-03'),(39,2,'2012-10-10');

-- ── Welfare workers + cases ───────────────────────────────────
DELETE FROM welfare_cases;
DELETE FROM welfare_workers;
INSERT INTO welfare_workers (id, member_id) VALUES (1, 5), (2, 16);

INSERT INTO welfare_cases (member_id, reason, assigned_worker_id, report, status, opened_at, completed_at) VALUES
(10, 'Lost employment; family needs food assistance while job-hunting.', 1,
     'Delivered a food parcel on the first visit. Following up on possible job leads through the Men''s Ministry network.',
     'In Progress', DATE_SUB(NOW(), INTERVAL 12 DAY), NULL),
(21, 'Recovering from surgery, needs prayer and occasional transport to check-ups.', 2,
     NULL, 'Pending', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL),
(30, 'Bereavement — lost a spouse, needs pastoral support and check-in visits.', 1,
     'Visited twice this month. Doing better, family from Thohoyandou is also assisting.',
     'In Progress', DATE_SUB(NOW(), INTERVAL 20 DAY), NULL),
(36, 'Long-term unemployment, requested help with basic groceries this month.', 2,
     'Grocery parcel delivered. Case closed — member found short-term work through a referral.',
     'Completed', DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(8,  'Marital counselling requested by the member.', 1,
     NULL, 'Pending', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- ── Calendar events (dates float relative to today, so the demo always
--    shows a realistic mix of upcoming and past events) ─────────────
DELETE FROM events;
INSERT INTO events (title, event_date, event_time, location, description, category, created_by) VALUES
('Sunday Service',               DATE_ADD(CURDATE(), INTERVAL 3 DAY),  '09:00:00', 'Main Auditorium',      'Regular Sunday worship service.', 'Service', 1),
('Sunday Service',               DATE_ADD(CURDATE(), INTERVAL 10 DAY), '09:00:00', 'Main Auditorium',      'Regular Sunday worship service.', 'Service', 1),
('Church Board Meeting',         DATE_ADD(CURDATE(), INTERVAL 5 DAY),  '17:30:00', 'Boardroom',             'Monthly board meeting — agenda TBC.', 'Meeting', 1),
('Community Outreach — Ha-Kutama', DATE_ADD(CURDATE(), INTERVAL 14 DAY), '08:00:00', 'Ha-Kutama Community Hall', 'Food parcels and prayer outreach.', 'Outreach', 1),
('Youth Night',                  DATE_ADD(CURDATE(), INTERVAL 7 DAY),  '18:00:00', 'Youth Campus',          'Worship night and games for the youth.', 'Youth', 1),
('Mid-Week Prayer Meeting',      DATE_ADD(CURDATE(), INTERVAL 2 DAY),  '18:00:00', 'Main Auditorium',      'Wednesday evening prayer service.', 'Service', 1),
('Ministers'' Fellowship',       DATE_SUB(CURDATE(), INTERVAL 6 DAY),  '10:00:00', 'Boardroom',             'Regional ministers'' fellowship gathering.', 'Meeting', 1),
('Harvest Thanksgiving',         DATE_ADD(CURDATE(), INTERVAL 25 DAY), '09:00:00', 'Main Auditorium',      'Annual harvest thanksgiving service.', 'Other', 1);

-- ── Inventory ─────────────────────────────────────────────────
DELETE FROM inventory_items;
INSERT INTO inventory_items
(item_name, category, quantity, unit, item_condition, location, custodian, purchase_date, purchase_value, low_stock_threshold, notes, created_by) VALUES
('Yamaha PSR Keyboard',        'Musical Instruments', 2,  'pcs',  'Good',        'Main Auditorium Stage',  'Music Team',        '2022-03-10', 8500.00, 1, 'One is the backup unit.', 1),
('Acoustic Drum Kit',          'Musical Instruments', 1,  'set',  'Fair',        'Main Auditorium Stage',  'Music Team',        '2019-06-01', 12000.00, 0, 'Needs new cymbals soon.', 1),
('Electric Guitar',            'Musical Instruments', 2,  'pcs',  'Good',        'Main Auditorium Stage',  'Music Team',        '2021-01-15', 4200.00, 1, NULL, 1),
('Wireless Microphone Set',    'Musical Instruments', 6,  'pcs',  'Good',        'Sound Booth',            'Sound Team',        '2023-02-20', 6800.00, 2, NULL, 1),
('PA Speaker System',          'Electronics',         1,  'set',  'Good',        'Main Auditorium',        'Sound Team',        '2020-11-05', 25000.00, 0, NULL, 1),
('Office Laptop',              'Office Equipment',    2,  'pcs',  'Good',        'Admin Office',           'Admin',             '2023-08-01', 15000.00, 1, 'Used for CMS + finance.', 1),
('Laser Printer',              'Office Equipment',    1,  'pcs',  'Needs Repair','Admin Office',           'Admin',             '2018-04-12', 3200.00, 0, 'Paper jam issue — needs servicing.', 1),
('Projector',                  'Electronics',         1,  'pcs',  'Good',        'Main Auditorium',        'Media Team',        '2021-09-09', 9800.00, 0, NULL, 1),
('Plastic Chairs',             'Furniture',           150,'pcs',  'Good',        'Storeroom',              'Facilities',        '2020-01-01', 350.00, 20, 'Price is per chair.', 1),
('Plastic Chairs (spare)',     'Furniture',           8,  'pcs',  'Damaged',     'Storeroom',              'Facilities',        '2020-01-01', 350.00, 5, 'Awaiting disposal/repair decision.', 1),
('Folding Tables',             'Furniture',           12, 'pcs',  'Good',        'Storeroom',              'Facilities',        '2019-05-05', 900.00, 3, NULL, 1),
('Roofing Sheets (IBR)',       'Building Materials',  40, 'sheets','New',        'Building Site Store',    'Building Committee','2026-06-01', 450.00, 10, 'For the youth campus roof project.', 1),
('Cement (42.5N)',             'Building Materials',  15, 'bags', 'New',         'Building Site Store',    'Building Committee','2026-07-15', 120.00, 20, 'Below threshold — reorder soon.', 1),
('Extension Cords',            'Electronics',         5,  'pcs',  'Fair',        'Storeroom',              'Facilities',        '2021-03-03', 250.00, 2, NULL, 1);

SET FOREIGN_KEY_CHECKS = 1;
