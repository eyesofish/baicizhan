CREATE TABLE users (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  email           VARCHAR(190) NOT NULL UNIQUE,
  password_hash   VARCHAR(255) NOT NULL,
  display_name    VARCHAR(64)  NOT NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP()
);

CREATE TABLE languages (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  iso_code    VARCHAR(20) NOT NULL UNIQUE,
  name        VARCHAR(64) NOT NULL,
  direction   VARCHAR(16) NOT NULL DEFAULT 'ltr',
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP()
);

CREATE TABLE terms (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  language_id     INT NOT NULL,
  text            VARCHAR(255) NOT NULL,
  normalized_text VARCHAR(255) NOT NULL,
  ipa             VARCHAR(128) NULL,
  audio_url       VARCHAR(512) NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT uk_lang_norm UNIQUE (language_id, normalized_text),
  CONSTRAINT fk_terms_lang FOREIGN KEY (language_id) REFERENCES languages(id)
);
CREATE INDEX idx_lang_text ON terms(language_id, text);

CREATE TABLE senses (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  term_id          BIGINT NOT NULL,
  part_of_speech   VARCHAR(32) NULL,
  definition       CLOB NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT fk_senses_term FOREIGN KEY (term_id) REFERENCES terms(id)
);
CREATE INDEX idx_sense_term ON senses(term_id);

CREATE TABLE translations (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  sense_id           BIGINT NOT NULL,
  target_language_id INT NOT NULL,
  translated_text    VARCHAR(512) NOT NULL,
  source_type        VARCHAR(32) NOT NULL DEFAULT 'openai',
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT fk_trans_sense FOREIGN KEY (sense_id) REFERENCES senses(id),
  CONSTRAINT fk_trans_lang FOREIGN KEY (target_language_id) REFERENCES languages(id)
);
CREATE INDEX idx_sense_target ON translations(sense_id, target_language_id);

CREATE TABLE example_sentences (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  sense_id        BIGINT NOT NULL,
  language_id     INT NOT NULL,
  sentence_text   CLOB NOT NULL,
  sentence_trans  CLOB NULL,
  source_type     VARCHAR(32) NOT NULL DEFAULT 'openai',
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT fk_es_sense FOREIGN KEY (sense_id) REFERENCES senses(id),
  CONSTRAINT fk_es_lang FOREIGN KEY (language_id) REFERENCES languages(id)
);
CREATE INDEX idx_sense_lang ON example_sentences(sense_id, language_id);

CREATE TABLE vocab_lists (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  name               VARCHAR(64) NOT NULL,
  source_language_id INT NOT NULL,
  target_language_id INT NOT NULL,
  is_public          TINYINT NOT NULL DEFAULT 0,
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT fk_vl_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_vl_src_lang FOREIGN KEY (source_language_id) REFERENCES languages(id),
  CONSTRAINT fk_vl_tgt_lang FOREIGN KEY (target_language_id) REFERENCES languages(id)
);
CREATE INDEX idx_user ON vocab_lists(user_id);

CREATE TABLE vocab_items (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  list_id     BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  sense_id    BIGINT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT uk_list_term UNIQUE (list_id, term_id),
  CONSTRAINT fk_vi_list FOREIGN KEY (list_id) REFERENCES vocab_lists(id),
  CONSTRAINT fk_vi_term FOREIGN KEY (term_id) REFERENCES terms(id),
  CONSTRAINT fk_vi_sense FOREIGN KEY (sense_id) REFERENCES senses(id)
);
CREATE INDEX idx_list ON vocab_items(list_id);

CREATE TABLE user_progress (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  term_id         BIGINT NOT NULL,
  ease_factor     DECIMAL(4,2) NOT NULL DEFAULT 2.50,
  interval_days   INT NOT NULL DEFAULT 0,
  repetition      INT NOT NULL DEFAULT 0,
  next_review_at  TIMESTAMP NOT NULL,
  last_review_at  TIMESTAMP NULL,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT uk_user_term UNIQUE (user_id, term_id),
  CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_up_term FOREIGN KEY (term_id) REFERENCES terms(id)
);
CREATE INDEX idx_user_next ON user_progress(user_id, next_review_at);

CREATE TABLE review_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  rating      TINYINT NOT NULL,
  elapsed_ms  INT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT fk_rl_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_rl_term FOREIGN KEY (term_id) REFERENCES terms(id)
);
CREATE INDEX idx_user_time ON review_logs(user_id, created_at);
CREATE INDEX idx_term_time ON review_logs(term_id, created_at);

CREATE TABLE ai_jobs (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT NOT NULL,
  job_type            VARCHAR(32) NOT NULL,
  status              VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
  term_id             BIGINT NULL,
  openai_response_id  VARCHAR(64) NULL,
  request_json        CLOB NOT NULL,
  result_json         CLOB NULL,
  error_message       VARCHAR(1024) NULL,
  retry_count         INT NOT NULL DEFAULT 0,
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  CONSTRAINT fk_ai_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_ai_term FOREIGN KEY (term_id) REFERENCES terms(id)
);
CREATE INDEX idx_status_created ON ai_jobs(status, created_at);
CREATE INDEX idx_term ON ai_jobs(term_id);
