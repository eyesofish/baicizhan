-- Seed learning scenarios for end-to-end API testing.
-- Scenarios:
-- 1) 10-day learner with mixed performance and partial due cards.
-- 2) Cold-start learner with list items but no progress/logs.
-- 3) Hard-mode learner with many wrong answers in recent 30 days.

START TRANSACTION;

SET @now = CURRENT_TIMESTAMP(3);

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
INSERT INTO users (email, password_hash, display_name, status, created_at, updated_at)
VALUES
  ('scenario10d@seed.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5wA9M2Qf4qB0fC0fQ5c1R6j3mR9xYxW', 'Scenario 10d', 1, @now, @now),
  ('scenariocold@seed.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5wA9M2Qf4qB0fC0fQ5c1R6j3mR9xYxW', 'Scenario Cold', 1, @now, @now),
  ('scenariohard@seed.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5wA9M2Qf4qB0fC0fQ5c1R6j3mR9xYxW', 'Scenario Hard', 1, @now, @now)
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  status = 1,
  updated_at = @now;

SET @u10 = (SELECT id FROM users WHERE email = 'scenario10d@seed.local' LIMIT 1);
SET @ucold = (SELECT id FROM users WHERE email = 'scenariocold@seed.local' LIMIT 1);
SET @uhard = (SELECT id FROM users WHERE email = 'scenariohard@seed.local' LIMIT 1);

SET @lang_en = (SELECT id FROM languages WHERE iso_code = 'en' LIMIT 1);
SET @lang_zh = (SELECT id FROM languages WHERE iso_code = 'zh-Hans' LIMIT 1);

-- ---------------------------------------------------------------------------
-- Ensure one list per scenario user
-- ---------------------------------------------------------------------------
SET @list10 = (SELECT id FROM vocab_lists WHERE user_id = @u10 AND name = 'Seed List - 10d' LIMIT 1);
INSERT INTO vocab_lists (
  user_id,
  name,
  source_language_id,
  target_language_id,
  is_public,
  created_at,
  updated_at
)
SELECT @u10, 'Seed List - 10d', @lang_en, @lang_zh, 0, @now, @now
WHERE @list10 IS NULL;
SET @list10 = (SELECT id FROM vocab_lists WHERE user_id = @u10 AND name = 'Seed List - 10d' LIMIT 1);

SET @listcold = (SELECT id FROM vocab_lists WHERE user_id = @ucold AND name = 'Seed List - Cold' LIMIT 1);
INSERT INTO vocab_lists (
  user_id,
  name,
  source_language_id,
  target_language_id,
  is_public,
  created_at,
  updated_at
)
SELECT @ucold, 'Seed List - Cold', @lang_en, @lang_zh, 0, @now, @now
WHERE @listcold IS NULL;
SET @listcold = (SELECT id FROM vocab_lists WHERE user_id = @ucold AND name = 'Seed List - Cold' LIMIT 1);

SET @listhard = (SELECT id FROM vocab_lists WHERE user_id = @uhard AND name = 'Seed List - Hard' LIMIT 1);
INSERT INTO vocab_lists (
  user_id,
  name,
  source_language_id,
  target_language_id,
  is_public,
  created_at,
  updated_at
)
SELECT @uhard, 'Seed List - Hard', @lang_en, @lang_zh, 0, @now, @now
WHERE @listhard IS NULL;
SET @listhard = (SELECT id FROM vocab_lists WHERE user_id = @uhard AND name = 'Seed List - Hard' LIMIT 1);

-- Clear previous seeded runtime data for deterministic reruns.
DELETE FROM ai_jobs WHERE user_id IN (@u10, @ucold, @uhard);
DELETE FROM review_logs WHERE user_id IN (@u10, @ucold, @uhard);
DELETE FROM user_progress WHERE user_id IN (@u10, @ucold, @uhard);
DELETE FROM vocab_items WHERE list_id IN (@list10, @listcold, @listhard);

-- ---------------------------------------------------------------------------
-- Seed term bank used by all scenarios
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_seed_word_bank;
CREATE TEMPORARY TABLE tmp_seed_word_bank (
  ord INT PRIMARY KEY,
  word VARCHAR(64) NOT NULL,
  ipa VARCHAR(128) NULL,
  freq_rank INT NOT NULL,
  difficulty_score DECIMAL(5,2) NOT NULL
);

INSERT INTO tmp_seed_word_bank (ord, word, ipa, freq_rank, difficulty_score) VALUES
  (1,  'resilient', '/rɪˈzɪliənt/', 120, 66.00),
  (2,  'diligent', '/ˈdɪlɪdʒənt/', 220, 63.00),
  (3,  'obscure', '/əbˈskjʊr/', 320, 70.00),
  (4,  'vivid', '/ˈvɪvɪd/', 420, 55.00),
  (5,  'frugal', '/ˈfruːɡəl/', 520, 58.00),
  (6,  'tedious', '/ˈtiːdiəs/', 620, 67.00),
  (7,  'keen', '/kiːn/', 720, 50.00),
  (8,  'sturdy', '/ˈstɜːrdi/', 820, 57.00),
  (9,  'glance', '/ɡlæns/', 920, 54.00),
  (10, 'notion', '/ˈnoʊʃən/', 1020, 60.00),
  (11, 'absorb', '/əbˈzɔːrb/', 1120, 61.00),
  (12, 'retain', '/rɪˈteɪn/', 1220, 62.00),
  (13, 'collide', '/kəˈlaɪd/', 1320, 68.00),
  (14, 'drift', '/drɪft/', 1420, 56.00),
  (15, 'humble', '/ˈhʌmbəl/', 1520, 53.00),
  (16, 'brisk', '/brɪsk/', 1620, 52.00),
  (17, 'harsh', '/hɑːrʃ/', 1720, 59.00),
  (18, 'thrive', '/θraɪv/', 1820, 64.00),
  (19, 'scarce', '/skers/', 1920, 65.00),
  (20, 'ample', '/ˈæmpəl/', 2020, 51.00),
  (21, 'swift', '/swɪft/', 2120, 49.00),
  (22, 'subtle', '/ˈsʌtəl/', 2220, 69.00),
  (23, 'rigid', '/ˈrɪdʒɪd/', 2320, 58.00),
  (24, 'vast', '/væst/', 2420, 47.00);

INSERT INTO terms (language_id, text, normalized_text, ipa, audio_url, created_at, updated_at)
SELECT
  @lang_en,
  wb.word,
  LOWER(wb.word),
  wb.ipa,
  NULL,
  @now,
  @now
FROM tmp_seed_word_bank wb
LEFT JOIN terms t
  ON t.language_id = @lang_en
 AND t.normalized_text = LOWER(wb.word)
WHERE t.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_terms;
CREATE TEMPORARY TABLE tmp_seed_terms AS
SELECT
  wb.ord,
  wb.word,
  wb.freq_rank,
  wb.difficulty_score,
  t.id AS term_id
FROM tmp_seed_word_bank wb
JOIN terms t
  ON t.language_id = @lang_en
 AND t.normalized_text = LOWER(wb.word);

INSERT INTO term_stats (term_id, frequency_rank, difficulty_score, source_type, updated_at)
SELECT term_id, freq_rank, difficulty_score, 'seed', @now
FROM tmp_seed_terms
ON DUPLICATE KEY UPDATE
  frequency_rank = VALUES(frequency_rank),
  difficulty_score = VALUES(difficulty_score),
  source_type = 'seed',
  updated_at = @now;

-- Ensure each seeded term has at least one sense.
INSERT INTO senses (term_id, part_of_speech, definition, created_at)
SELECT
  st.term_id,
  CASE
    WHEN MOD(st.ord, 3) = 0 THEN 'verb'
    WHEN MOD(st.ord, 2) = 0 THEN 'adjective'
    ELSE 'noun'
  END,
  CONCAT('Seed definition for ', st.word),
  @now
FROM tmp_seed_terms st
LEFT JOIN senses s ON s.term_id = st.term_id
WHERE s.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_primary_sense;
CREATE TEMPORARY TABLE tmp_seed_primary_sense AS
SELECT s.term_id, MIN(s.id) AS sense_id
FROM senses s
JOIN tmp_seed_terms st ON st.term_id = s.term_id
GROUP BY s.term_id;

-- Ensure at least one zh-Hans translation exists for seeded terms.
INSERT INTO translations (sense_id, target_language_id, translated_text, source_type, created_at)
SELECT
  ps.sense_id,
  @lang_zh,
  CONCAT('示例-', st.word),
  'manual',
  @now
FROM tmp_seed_primary_sense ps
JOIN tmp_seed_terms st ON st.term_id = ps.term_id
LEFT JOIN translations tr
  ON tr.sense_id = ps.sense_id
 AND tr.target_language_id = @lang_zh
WHERE tr.id IS NULL;

-- ---------------------------------------------------------------------------
-- Scenario maps
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_map_10d;
CREATE TEMPORARY TABLE tmp_map_10d (
  ord INT PRIMARY KEY,
  has_progress TINYINT(1) NOT NULL,
  due TINYINT(1) NOT NULL
);

INSERT INTO tmp_map_10d (ord, has_progress, due) VALUES
  (1,1,1),(2,1,1),(3,1,1),(4,1,1),(5,1,1),
  (6,1,0),(7,1,0),(8,1,0),(9,1,0),(10,1,0),
  (11,1,1),(12,1,1),(13,1,0),(14,1,0),
  (15,0,0),(16,0,0),(17,0,0),(18,0,0),(19,0,0),(20,0,0);

DROP TEMPORARY TABLE IF EXISTS tmp_map_cold;
CREATE TEMPORARY TABLE tmp_map_cold (ord INT PRIMARY KEY);
INSERT INTO tmp_map_cold (ord) VALUES
  (3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14);

DROP TEMPORARY TABLE IF EXISTS tmp_map_hard;
CREATE TEMPORARY TABLE tmp_map_hard (
  ord INT PRIMARY KEY,
  has_progress TINYINT(1) NOT NULL,
  due TINYINT(1) NOT NULL
);

INSERT INTO tmp_map_hard (ord, has_progress, due) VALUES
  (5,1,1),(6,1,1),(7,1,1),(8,1,1),(9,1,1),
  (10,1,1),(11,1,1),(12,1,1),(13,1,1),(14,1,1),
  (15,1,1),(16,1,1),(17,1,0),(18,1,0),(19,1,0),(20,1,0);

-- ---------------------------------------------------------------------------
-- Vocab items
-- ---------------------------------------------------------------------------
INSERT INTO vocab_items (list_id, term_id, sense_id, created_at)
SELECT
  @list10,
  st.term_id,
  ps.sense_id,
  DATE_SUB(@now, INTERVAL (21 - st.ord) DAY)
FROM tmp_seed_terms st
JOIN tmp_map_10d m ON m.ord = st.ord
LEFT JOIN tmp_seed_primary_sense ps ON ps.term_id = st.term_id
ORDER BY st.ord;

INSERT INTO vocab_items (list_id, term_id, sense_id, created_at)
SELECT
  @listcold,
  st.term_id,
  ps.sense_id,
  DATE_SUB(@now, INTERVAL (30 - st.ord) DAY)
FROM tmp_seed_terms st
JOIN tmp_map_cold m ON m.ord = st.ord
LEFT JOIN tmp_seed_primary_sense ps ON ps.term_id = st.term_id
ORDER BY st.ord;

INSERT INTO vocab_items (list_id, term_id, sense_id, created_at)
SELECT
  @listhard,
  st.term_id,
  ps.sense_id,
  DATE_SUB(@now, INTERVAL (18 - st.ord) DAY)
FROM tmp_seed_terms st
JOIN tmp_map_hard m ON m.ord = st.ord
LEFT JOIN tmp_seed_primary_sense ps ON ps.term_id = st.term_id
ORDER BY st.ord;

-- ---------------------------------------------------------------------------
-- User progress
-- ---------------------------------------------------------------------------
INSERT INTO user_progress (
  user_id,
  term_id,
  ease_factor,
  interval_days,
  repetition,
  next_review_at,
  last_review_at,
  updated_at
)
SELECT
  @u10,
  st.term_id,
  CAST(ROUND(2.10 + MOD(st.ord, 6) * 0.08, 2) AS DECIMAL(4,2)),
  CASE WHEN m.due = 1 THEN 2 + MOD(st.ord, 3) ELSE 4 + MOD(st.ord, 5) END,
  CASE WHEN m.due = 1 THEN 2 + MOD(st.ord, 3) ELSE 4 + MOD(st.ord, 4) END,
  CASE
    WHEN m.due = 1 THEN DATE_SUB(@now, INTERVAL (1 + MOD(st.ord, 4)) DAY)
    ELSE DATE_ADD(@now, INTERVAL (1 + MOD(st.ord, 5)) DAY)
  END,
  DATE_SUB(@now, INTERVAL (1 + MOD(st.ord, 6)) DAY),
  @now
FROM tmp_seed_terms st
JOIN tmp_map_10d m ON m.ord = st.ord
WHERE m.has_progress = 1;

INSERT INTO user_progress (
  user_id,
  term_id,
  ease_factor,
  interval_days,
  repetition,
  next_review_at,
  last_review_at,
  updated_at
)
SELECT
  @uhard,
  st.term_id,
  CAST(ROUND(1.35 + MOD(st.ord, 4) * 0.06, 2) AS DECIMAL(4,2)),
  1 + MOD(st.ord, 3),
  MOD(st.ord, 3),
  CASE
    WHEN m.due = 1 THEN DATE_SUB(@now, INTERVAL (1 + MOD(st.ord, 5)) DAY)
    ELSE DATE_ADD(@now, INTERVAL 1 DAY)
  END,
  DATE_SUB(@now, INTERVAL (1 + MOD(st.ord, 4)) DAY),
  @now
FROM tmp_seed_terms st
JOIN tmp_map_hard m ON m.ord = st.ord
WHERE m.has_progress = 1;

-- ---------------------------------------------------------------------------
-- Review logs: 10-day learner (mixed ratings) + hard learner (mostly wrong)
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_days_10;
CREATE TEMPORARY TABLE tmp_days_10 (d INT PRIMARY KEY);
INSERT INTO tmp_days_10 (d) VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

INSERT INTO review_logs (user_id, term_id, rating, elapsed_ms, created_at)
SELECT
  @u10,
  st.term_id,
  CASE
    WHEN MOD(st.ord + d.d, 8) = 0 THEN 1
    WHEN MOD(st.ord + d.d, 5) = 0 THEN 2
    WHEN MOD(st.ord + d.d, 3) = 0 THEN 3
    WHEN MOD(st.ord + d.d, 2) = 0 THEN 4
    ELSE 5
  END AS rating,
  900 + MOD(st.ord * 79 + d.d * 53, 2600) AS elapsed_ms,
  DATE_ADD(
    DATE_SUB(@now, INTERVAL (10 - d.d) DAY),
    INTERVAL MOD(st.ord * 7 + d.d * 3, 120) MINUTE
  ) AS created_at
FROM tmp_seed_terms st
JOIN tmp_map_10d m ON m.ord = st.ord
JOIN tmp_days_10 d
WHERE m.has_progress = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_days_15;
CREATE TEMPORARY TABLE tmp_days_15 (d INT PRIMARY KEY);
INSERT INTO tmp_days_15 (d) VALUES
  (0),(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14);

INSERT INTO review_logs (user_id, term_id, rating, elapsed_ms, created_at)
SELECT
  @uhard,
  st.term_id,
  CASE
    WHEN MOD(st.ord + d.d, 6) = 0 THEN 3
    WHEN MOD(st.ord + d.d, 2) = 0 THEN 1
    ELSE 2
  END AS rating,
  1200 + MOD(st.ord * 61 + d.d * 41, 1800) AS elapsed_ms,
  DATE_ADD(
    DATE_SUB(@now, INTERVAL (15 - d.d) DAY),
    INTERVAL MOD(st.ord * 5 + d.d * 2, 180) MINUTE
  ) AS created_at
FROM tmp_seed_terms st
JOIN tmp_map_hard m ON m.ord = st.ord
JOIN tmp_days_15 d
WHERE m.has_progress = 1;

COMMIT;

-- ---------------------------------------------------------------------------
-- Quick verification summary
-- ---------------------------------------------------------------------------
SELECT
  u.email,
  (SELECT COUNT(*) FROM vocab_items vi JOIN vocab_lists vl ON vl.id = vi.list_id WHERE vl.user_id = u.id) AS vocab_items,
  (SELECT COUNT(*) FROM user_progress up WHERE up.user_id = u.id) AS progress_items,
  (SELECT COUNT(*) FROM user_progress up WHERE up.user_id = u.id AND up.next_review_at <= @now) AS due_now,
  (SELECT COUNT(*) FROM review_logs rl WHERE rl.user_id = u.id) AS review_logs,
  (SELECT COUNT(*) FROM review_logs rl WHERE rl.user_id = u.id AND rl.rating <= 2 AND rl.created_at >= DATE_SUB(@now, INTERVAL 30 DAY)) AS wrong_30d
FROM users u
WHERE u.email IN ('scenario10d@seed.local', 'scenariocold@seed.local', 'scenariohard@seed.local')
ORDER BY u.email;

