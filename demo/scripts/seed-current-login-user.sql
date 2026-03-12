-- Copy scenario10d seed data into the current active (non-seed) user.
-- Default target selection: latest updated active user excluding seed scenario accounts.
-- Optional override: set @target_email to a concrete email before running.

START TRANSACTION;

SET @now = CURRENT_TIMESTAMP(3);
SET @target_email = NULL;

SET @source_email = 'scenario10d@seed.local';
SET @source_user_id = (
  SELECT id
  FROM users
  WHERE email = @source_email
  LIMIT 1
);

SET @target_user_id = (
  SELECT id
  FROM users
  WHERE status = 1
    AND (
      (@target_email IS NOT NULL AND email = @target_email)
      OR (
        @target_email IS NULL
        AND email NOT IN ('scenario10d@seed.local', 'scenariocold@seed.local', 'scenariohard@seed.local')
      )
    )
  ORDER BY updated_at DESC, id DESC
  LIMIT 1
);

SET @lang_en = (SELECT id FROM languages WHERE iso_code = 'en' LIMIT 1);
SET @lang_zh = (SELECT id FROM languages WHERE iso_code = 'zh-Hans' LIMIT 1);

-- Source list from scenario10d seed user.
SET @source_list_id = (
  SELECT id
  FROM vocab_lists
  WHERE user_id = @source_user_id
    AND name = 'Seed List - 10d'
  LIMIT 1
);

-- Ensure target test list exists.
SET @target_list_name = 'Seed List - Current User (10d)';
SET @target_list_id = (
  SELECT id
  FROM vocab_lists
  WHERE user_id = @target_user_id
    AND name = @target_list_name
  LIMIT 1
);

INSERT INTO vocab_lists (
  user_id,
  name,
  source_language_id,
  target_language_id,
  is_public,
  created_at,
  updated_at
)
SELECT
  @target_user_id,
  @target_list_name,
  @lang_en,
  @lang_zh,
  0,
  @now,
  @now
WHERE @target_user_id IS NOT NULL
  AND @target_user_id <> 0
  AND @target_list_id IS NULL;

SET @target_list_id = (
  SELECT id
  FROM vocab_lists
  WHERE user_id = @target_user_id
    AND name = @target_list_name
  LIMIT 1
);

-- Deterministic list contents: reset target seeded list items each run.
DELETE FROM vocab_items
WHERE list_id = @target_list_id;

INSERT INTO vocab_items (list_id, term_id, sense_id, created_at)
SELECT
  @target_list_id,
  vi.term_id,
  vi.sense_id,
  vi.created_at
FROM vocab_items vi
WHERE vi.list_id = @source_list_id
ORDER BY vi.id;

-- Upsert user progress to match scenario10d values for seeded terms.
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
  @target_user_id,
  up.term_id,
  up.ease_factor,
  up.interval_days,
  up.repetition,
  up.next_review_at,
  up.last_review_at,
  @now
FROM user_progress up
JOIN vocab_items vi
  ON vi.term_id = up.term_id
 AND vi.list_id = @source_list_id
WHERE up.user_id = @source_user_id
ON DUPLICATE KEY UPDATE
  ease_factor = VALUES(ease_factor),
  interval_days = VALUES(interval_days),
  repetition = VALUES(repetition),
  next_review_at = VALUES(next_review_at),
  last_review_at = VALUES(last_review_at),
  updated_at = VALUES(updated_at);

-- Insert scenario logs if missing (idempotent copy).
INSERT INTO review_logs (user_id, term_id, rating, elapsed_ms, created_at)
SELECT
  @target_user_id,
  rl.term_id,
  rl.rating,
  rl.elapsed_ms,
  rl.created_at
FROM review_logs rl
JOIN vocab_items vi
  ON vi.term_id = rl.term_id
 AND vi.list_id = @source_list_id
WHERE rl.user_id = @source_user_id
  AND NOT EXISTS (
    SELECT 1
    FROM review_logs x
    WHERE x.user_id = @target_user_id
      AND x.term_id = rl.term_id
      AND x.rating = rl.rating
      AND COALESCE(x.elapsed_ms, -1) = COALESCE(rl.elapsed_ms, -1)
      AND x.created_at = rl.created_at
  );

-- Touch target list update time.
UPDATE vocab_lists
SET updated_at = @now
WHERE id = @target_list_id;

COMMIT;

-- Verification summary for the chosen target user.
SELECT
  u.id,
  u.email,
  @target_list_id AS seeded_list_id,
  (SELECT COUNT(*) FROM vocab_items vi WHERE vi.list_id = @target_list_id) AS seeded_vocab_items,
  (
    SELECT COUNT(*)
    FROM user_progress up
    JOIN vocab_items vi ON vi.term_id = up.term_id
    WHERE up.user_id = u.id
      AND vi.list_id = @target_list_id
  ) AS progress_on_seed_terms,
  (
    SELECT COUNT(*)
    FROM user_progress up
    JOIN vocab_items vi ON vi.term_id = up.term_id
    WHERE up.user_id = u.id
      AND vi.list_id = @target_list_id
      AND up.next_review_at <= @now
  ) AS due_now_on_seed_terms,
  (
    SELECT COUNT(*)
    FROM review_logs rl
    JOIN vocab_items vi ON vi.term_id = rl.term_id
    WHERE rl.user_id = u.id
      AND vi.list_id = @target_list_id
  ) AS review_logs_on_seed_terms,
  (
    SELECT COUNT(*)
    FROM review_logs rl
    JOIN vocab_items vi ON vi.term_id = rl.term_id
    WHERE rl.user_id = u.id
      AND vi.list_id = @target_list_id
      AND rl.rating <= 2
      AND rl.created_at >= DATE_SUB(@now, INTERVAL 30 DAY)
  ) AS wrong_30d_on_seed_terms
FROM users u
WHERE u.id = @target_user_id;

SELECT
  @source_user_id AS source_user_id,
  @source_list_id AS source_list_id,
  @target_user_id AS target_user_id,
  @target_list_id AS target_list_id;
