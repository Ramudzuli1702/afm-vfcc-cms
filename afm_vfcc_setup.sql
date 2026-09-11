-- ============================================================
-- AFM VFCC Church Management System - Full Database Setup
-- Run this once on any new machine to set up the system.
-- ============================================================

CREATE DATABASE IF NOT EXISTS AFM_VFCC_CMS
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE AFM_VFCC_CMS;

-- IMPORTANT: Replace 'YOUR_PASSWORD_HERE' with a strong password before running.
CREATE USER IF NOT EXISTS 'afm_app'@'localhost' IDENTIFIED BY 'YOUR_PASSWORD_HERE';
GRANT ALL PRIVILEGES ON AFM_VFCC_CMS.* TO 'afm_app'@'localhost';
FLUSH PRIVILEGES;

-- ============================================================
-- TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS sub_branches (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    is_active TINYINT(1)   DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS families (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    family_name VARCHAR(100) NOT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ministries (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_active   TINYINT(1)   DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(100)  NOT NULL,
    username        VARCHAR(50)   NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    email           VARCHAR(100),
    phone           VARCHAR(20),
    role_title      VARCHAR(50),
    photo_path      VARCHAR(255),
    is_super_admin  TINYINT(1)    DEFAULT 0,
    is_active       TINYINT(1)    DEFAULT 1,
    failed_attempts INT           DEFAULT 0,
    locked_until    DATETIME,
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS members (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    full_name             VARCHAR(100) NOT NULL,
    date_of_birth         DATE,
    gender                ENUM('Male','Female'),
    marital_status        ENUM('Single','Married','Divorced','Widowed') DEFAULT 'Single',
    is_spouse_member      TINYINT(1) DEFAULT 0,
    spouse_member_id      INT,
    employment_status     ENUM('Employed','Student','Retired','Unemployed') DEFAULT 'Employed',
    address               TEXT,
    sub_branch_id         INT,
    phone                 VARCHAR(20),
    email                 VARCHAR(100),
    baptism_date          DATE,
    next_of_kin_name      VARCHAR(100),
    next_of_kin_phone     VARCHAR(20),
    next_of_kin_member_id INT,
    family_id             INT,
    photo_path            VARCHAR(255),
    is_full_time          TINYINT(1)   DEFAULT 1,
    is_active             TINYINT(1)   DEFAULT 1,
    is_deleted            TINYINT(1)   DEFAULT 0,
    is_deceased           TINYINT(1)   DEFAULT 0,
    date_joined           DATE,
    created_at            DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sub_branch_id)         REFERENCES sub_branches(id) ON DELETE SET NULL,
    FOREIGN KEY (family_id)             REFERENCES families(id)     ON DELETE SET NULL,
    FOREIGN KEY (next_of_kin_member_id) REFERENCES members(id)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pending_members (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    sub_branch_id INT,
    ministry_id   INT,
    is_full_time  TINYINT(1)   DEFAULT 1,
    submitted_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    device_id     VARCHAR(100),
    status        ENUM('Pending','Approved','Rejected') DEFAULT 'Pending',
    reviewed_by   INT,
    reviewed_at   DATETIME,
    FOREIGN KEY (sub_branch_id) REFERENCES sub_branches(id) ON DELETE SET NULL,
    FOREIGN KEY (ministry_id)   REFERENCES ministries(id)   ON DELETE SET NULL,
    FOREIGN KEY (reviewed_by)   REFERENCES users(id)        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_ministries (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    member_id   INT  NOT NULL,
    ministry_id INT  NOT NULL,
    joined_date DATE,
    UNIQUE KEY unique_member_ministry (member_id, ministry_id),
    FOREIGN KEY (member_id)   REFERENCES members(id)    ON DELETE CASCADE,
    FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- ATTENDANCE SESSIONS
-- is_closed: 0 = open (default), 1 = completed/closed
-- ============================================================

CREATE TABLE IF NOT EXISTS attendance_sessions (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    session_name VARCHAR(150) NOT NULL,
    session_date DATE         NOT NULL,
    ministry_id  INT,
    created_by   INT,
    is_closed    TINYINT(1)   NOT NULL DEFAULT 0
                 COMMENT '1 = session completed/closed, 0 = still open',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by)  REFERENCES users(id)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for fast filtering of open vs closed sessions
CREATE INDEX idx_att_sess_closed ON attendance_sessions (is_closed);

CREATE TABLE IF NOT EXISTS attendance_records (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    session_id  INT NOT NULL,
    member_id   INT NOT NULL,
    is_present  TINYINT(1)   DEFAULT 0,
    synced_from VARCHAR(100),
    synced_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_attendance (session_id, member_id),
    FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id)  REFERENCES members(id)             ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS guests (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    full_name        VARCHAR(100) NOT NULL,
    phone            VARCHAR(20),
    gender           ENUM('Male','Female','Other'),
    sub_branch_id    INT,
    invited_by       VARCHAR(100),
    wants_membership TINYINT(1)   DEFAULT 0,
    prayer_request   TEXT,
    session_id       INT,
    synced_from      VARCHAR(100),
    visit_date       DATE         DEFAULT (CURRENT_DATE),
    status           ENUM('Guest','Converted','Dismissed') DEFAULT 'Guest',
    created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sub_branch_id) REFERENCES sub_branches(id)       ON DELETE SET NULL,
    FOREIGN KEY (session_id)    REFERENCES attendance_sessions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_tokens (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(100),
    token       VARCHAR(255) NOT NULL UNIQUE,
    is_active   TINYINT(1)   DEFAULT 1,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS events (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    event_date  DATE         NOT NULL,
    event_time  TIME,
    location    VARCHAR(150),
    description TEXT,
    category    VARCHAR(50),
    created_by  INT,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS website_events (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    description    LONGTEXT,
    image_filename VARCHAR(255),
    event_date     DATE,
    published_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_by     INT,
    poster_path    VARCHAR(500),
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS website_blogs (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    author       VARCHAR(100),
    content      LONGTEXT     NOT NULL,
    published_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_by   INT,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- COMMUNICATIONS & ANNOUNCEMENTS
-- ============================================================

CREATE TABLE IF NOT EXISTS communications (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    subject         VARCHAR(200),
    message_body    LONGTEXT     NOT NULL,
    channel         ENUM('Email','SMS') NOT NULL,
    recipient_type  ENUM('Individual','Ministry','Sub-branch','All') NOT NULL,
    recipient_id    INT,
    sent_by         INT,
    sent_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    recipient_count INT          DEFAULT 0,
    recipient_group VARCHAR(100),
    FOREIGN KEY (sent_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS announcements (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(255) NOT NULL DEFAULT 'Church Announcement',
    announcement_text LONGTEXT     NOT NULL,
    channels          VARCHAR(100),
    attachment_path   VARCHAR(500),
    attachment_name   VARCHAR(255),
    whatsapp_post_id  VARCHAR(200),
    facebook_post_id  VARCHAR(200),
    status            VARCHAR(100),
    posted_by         INT,
    posted_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (posted_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- BROADCAST / YOUTUBE MODULE
-- ============================================================

CREATE TABLE IF NOT EXISTS broadcast_uploads (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200)  NOT NULL,
    description      TEXT,
    facebook_url     VARCHAR(500),
    youtube_video_id VARCHAR(50),
    youtube_url      VARCHAR(300),
    local_file_path  VARCHAR(500),
    privacy          VARCHAR(20)   DEFAULT 'public',
    status           ENUM('Downloaded','Uploaded','Failed') DEFAULT 'Downloaded',
    uploaded_by      INT,
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- BOARD MODULE
-- ============================================================

CREATE TABLE IF NOT EXISTS board_members (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    member_id  INT  NOT NULL,
    role       VARCHAR(100) NOT NULL DEFAULT '',
    role_title VARCHAR(100),
    start_date DATE,
    end_date   DATE,
    is_active  TINYINT(1)   DEFAULT 1,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS board_meetings (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(150) NOT NULL,
    meeting_date     DATE         NOT NULL,
    location         VARCHAR(150),
    agenda           TEXT,
    minutes_text     LONGTEXT,
    status           ENUM('Upcoming','Completed') DEFAULT 'Upcoming',
    agenda_doc_path  VARCHAR(500),
    minutes_doc_path VARCHAR(500),
    created_by       INT,
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS board_meeting_attendees (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    meeting_id INT         NOT NULL,
    member_id  INT         NOT NULL,
    attended   TINYINT(1)  DEFAULT 1,
    apology    TINYINT(1)  DEFAULT 0,
    FOREIGN KEY (meeting_id) REFERENCES board_meetings(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id)  REFERENCES members(id)        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS board_meeting_files (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    meeting_id  INT          NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(255) NOT NULL,
    uploaded_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (meeting_id) REFERENCES board_meetings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- WELFARE MODULE
-- ============================================================

CREATE TABLE IF NOT EXISTS welfare_workers (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    member_id   INT NOT NULL UNIQUE,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS welfare_cases (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    member_id          INT  NOT NULL,
    reason             TEXT NOT NULL,
    assigned_worker_id INT,
    report             LONGTEXT,
    status             ENUM('Pending','In Progress','Completed') DEFAULT 'Pending',
    opened_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at       DATETIME,
    FOREIGN KEY (member_id)          REFERENCES members(id)         ON DELETE CASCADE,
    FOREIGN KEY (assigned_worker_id) REFERENCES welfare_workers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deceased_members (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    member_id     INT NOT NULL UNIQUE,
    date_of_death DATE,
    obituary      TEXT,
    recorded_by   INT,
    recorded_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id)   REFERENCES members(id) ON DELETE CASCADE,
    FOREIGN KEY (recorded_by) REFERENCES users(id)   ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_log (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT,
    action       TEXT     NOT NULL,
    performed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_settings (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- DEFAULT DATA
-- ============================================================

INSERT IGNORE INTO ministries (id, name, is_active) VALUES
    (1, "Women's Ministry", 1),
    (2, "Men's Ministry",   1),
    (3, 'Youth',             1),
    (4, 'Sunday School',     1);

INSERT IGNORE INTO system_settings (setting_key, setting_value) VALUES
    ('smtp_host',                 'smtp.gmail.com'),
    ('smtp_port',                 '587'),
    ('smtp_user',                 ''),
    ('smtp_password',             ''),
    ('bulksms_key',               ''),
    ('bulksms_secret',            ''),
    ('bulksms_sender',            'AFMVFCC'),
    ('backup_location',           ''),
    ('website_folder',            ''),
    ('broadcast_download_folder', ''),
    ('whatsapp_token',            ''),
    ('whatsapp_phone_id',         ''),
    ('whatsapp_recipient',        ''),
    ('facebook_page_token',       ''),
    ('facebook_page_id',          '');

-- Default superadmin  |  Username: superadmin  |  Password: password
-- CHANGE THIS PASSWORD IMMEDIATELY AFTER FIRST LOGIN!
INSERT IGNORE INTO users (id, full_name, username, password_hash, role_title, is_super_admin, is_active)
VALUES (1, 'Super Admin', 'superadmin',
    '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'System Administrator', 1, 1);

-- ============================================================
-- MIGRATION: run this block against EXISTING databases that
-- were created before is_closed was added.
-- Safe to run multiple times — ALTER will error silently if
-- the column already exists (use a stored procedure if you
-- want true idempotency).
-- ============================================================

ALTER TABLE attendance_sessions
    ADD COLUMN IF NOT EXISTS is_closed TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1 = session completed/closed, 0 = still open';

-- Note: CREATE INDEX IF NOT EXISTS requires MySQL 8.0.29+.
-- On older MySQL use the stored procedure block below instead.
CREATE INDEX IF NOT EXISTS idx_att_sess_closed ON attendance_sessions (is_closed);

-- For MySQL < 8.0.29 use this instead of the two statements above:
-- DROP PROCEDURE IF EXISTS add_att_sess_closed;
-- DELIMITER $$
-- CREATE PROCEDURE add_att_sess_closed()
-- BEGIN
--   IF NOT EXISTS (
--     SELECT 1 FROM information_schema.COLUMNS
--     WHERE TABLE_SCHEMA = DATABASE()
--       AND TABLE_NAME   = 'attendance_sessions'
--       AND COLUMN_NAME  = 'is_closed'
--   ) THEN
--     ALTER TABLE attendance_sessions
--       ADD COLUMN is_closed TINYINT(1) NOT NULL DEFAULT 0
--         COMMENT '1 = session completed/closed, 0 = still open';
--   END IF;
--   IF NOT EXISTS (
--     SELECT 1 FROM information_schema.STATISTICS
--     WHERE TABLE_SCHEMA = DATABASE()
--       AND TABLE_NAME   = 'attendance_sessions'
--       AND INDEX_NAME   = 'idx_att_sess_closed'
--   ) THEN
--     CREATE INDEX idx_att_sess_closed ON attendance_sessions (is_closed);
--   END IF;
-- END$$
-- DELIMITER ;
-- CALL add_att_sess_closed();
-- DROP PROCEDURE IF EXISTS add_att_sess_closed;

-- ============================================================
SELECT 'AFM VFCC CMS database setup complete.' AS Status;
SELECT 'Default login  →  Username: superadmin  |  Password: password' AS Instructions;
SELECT 'IMPORTANT: Change the superadmin password after first login!' AS Warning;
-- ============================================================
