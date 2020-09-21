CREATE TABLE list (
  list_id SERIAL PRIMARY KEY,
  account_id SERIAL REFERENCES account(account_id),
  quiz_id SERIAL REFERENCES quiz(quiz_id),
  has_draft_status boolean NOT NULL,
  UNIQUE(account_id, quiz_id)
);

CREATE TABLE video (
  video_id SERIAL PRIMARY KEY,
  list_id SERIAL REFERENCES list(list_id),
  url character varying
);
