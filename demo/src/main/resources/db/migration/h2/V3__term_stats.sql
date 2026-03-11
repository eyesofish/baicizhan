CREATE TABLE term_stats (
  term_id            BIGINT PRIMARY KEY,
  frequency_rank     INT NOT NULL,
  difficulty_score   DECIMAL(5,2) NOT NULL DEFAULT 50.00,
  source_type        VARCHAR(32) NOT NULL DEFAULT 'import',
  updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT fk_term_stats_term FOREIGN KEY (term_id) REFERENCES terms(id)
);

CREATE INDEX idx_term_stats_freq ON term_stats(frequency_rank);
CREATE INDEX idx_term_stats_diff ON term_stats(difficulty_score);
