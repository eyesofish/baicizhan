CREATE TABLE users (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  email           VARCHAR(190) NOT NULL UNIQUE,
  password_hash   VARCHAR(255) NOT NULL,
  display_name    VARCHAR(64)  NOT NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE languages (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  iso_code    VARCHAR(20) NOT NULL UNIQUE,
  name        VARCHAR(64) NOT NULL,
  direction   ENUM('ltr','rtl') NOT NULL DEFAULT 'ltr',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE terms (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  language_id     INT NOT NULL,
  text            VARCHAR(255) NOT NULL,
  normalized_text VARCHAR(255) NOT NULL,
  ipa             VARCHAR(128) NULL,
  audio_url       VARCHAR(512) NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_lang_norm (language_id, normalized_text),
  KEY idx_lang_text (language_id, text),
  CONSTRAINT fk_terms_lang FOREIGN KEY (language_id) REFERENCES languages(id)
) ENGINE=InnoDB;

CREATE TABLE senses (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  term_id          BIGINT NOT NULL,
  part_of_speech   VARCHAR(32) NULL,
  definition       TEXT NULL,
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sense_term (term_id),
  CONSTRAINT fk_senses_term FOREIGN KEY (term_id) REFERENCES terms(id)
) ENGINE=InnoDB;

CREATE TABLE translations (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  sense_id           BIGINT NOT NULL,
  target_language_id INT NOT NULL,
  translated_text    VARCHAR(512) NOT NULL,
  source_type        ENUM('manual','openai','import') NOT NULL DEFAULT 'openai',
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sense_target (sense_id, target_language_id),
  CONSTRAINT fk_trans_sense FOREIGN KEY (sense_id) REFERENCES senses(id),
  CONSTRAINT fk_trans_lang FOREIGN KEY (target_language_id) REFERENCES languages(id)
) ENGINE=InnoDB;

CREATE TABLE example_sentences (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  sense_id        BIGINT NOT NULL,
  language_id     INT NOT NULL,
  sentence_text   TEXT NOT NULL,
  sentence_trans  TEXT NULL,
  source_type     ENUM('manual','openai','import') NOT NULL DEFAULT 'openai',
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sense_lang (sense_id, language_id),
  CONSTRAINT fk_es_sense FOREIGN KEY (sense_id) REFERENCES senses(id),
  CONSTRAINT fk_es_lang FOREIGN KEY (language_id) REFERENCES languages(id)
) ENGINE=InnoDB;

CREATE TABLE vocab_lists (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  name               VARCHAR(64) NOT NULL,
  source_language_id INT NOT NULL,
  target_language_id INT NOT NULL,
  is_public          TINYINT NOT NULL DEFAULT 0,
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_user (user_id),
  CONSTRAINT fk_vl_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_vl_src_lang FOREIGN KEY (source_language_id) REFERENCES languages(id),
  CONSTRAINT fk_vl_tgt_lang FOREIGN KEY (target_language_id) REFERENCES languages(id)
) ENGINE=InnoDB;

CREATE TABLE vocab_items (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  list_id     BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  sense_id    BIGINT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_list_term (list_id, term_id),
  KEY idx_list (list_id),
  CONSTRAINT fk_vi_list FOREIGN KEY (list_id) REFERENCES vocab_lists(id),
  CONSTRAINT fk_vi_term FOREIGN KEY (term_id) REFERENCES terms(id),
  CONSTRAINT fk_vi_sense FOREIGN KEY (sense_id) REFERENCES senses(id)
) ENGINE=InnoDB;

CREATE TABLE user_progress (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  term_id         BIGINT NOT NULL,
  ease_factor     DECIMAL(4,2) NOT NULL DEFAULT 2.50,
  interval_days   INT NOT NULL DEFAULT 0,
  repetition      INT NOT NULL DEFAULT 0,
  next_review_at  DATETIME(3) NOT NULL,
  last_review_at  DATETIME(3) NULL,
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_term (user_id, term_id),
  KEY idx_user_next (user_id, next_review_at),
  CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_up_term FOREIGN KEY (term_id) REFERENCES terms(id)
) ENGINE=InnoDB;

CREATE TABLE review_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  rating      TINYINT NOT NULL,
  elapsed_ms  INT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_user_time (user_id, created_at),
  KEY idx_term_time (term_id, created_at),
  CONSTRAINT fk_rl_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_rl_term FOREIGN KEY (term_id) REFERENCES terms(id)
) ENGINE=InnoDB;

CREATE TABLE ai_jobs (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT NOT NULL,
  job_type            VARCHAR(32) NOT NULL,
  status              ENUM('QUEUED','RUNNING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'QUEUED',
  term_id             BIGINT NULL,
  openai_response_id  VARCHAR(64) NULL,
  request_json        JSON NOT NULL,
  result_json         JSON NULL,
  error_message       VARCHAR(1024) NULL,
  retry_count         INT NOT NULL DEFAULT 0,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_status_created (status, created_at),
  KEY idx_term (term_id),
  CONSTRAINT fk_ai_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_ai_term FOREIGN KEY (term_id) REFERENCES terms(id)
) ENGINE=InnoDB;
