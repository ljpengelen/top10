CREATE TABLE quiz (
  quiz_id SERIAL PRIMARY KEY,
  name character varying,
  is_active boolean NOT NULL,
  creator_id SERIAL REFERENCES account(account_id),
  deadline timestamptz
);

CREATE TABLE participant (
  quiz_id SERIAL REFERENCES quiz(quiz_id),
  account_id SERIAL REFERENCES account(account_id),
  UNIQUE(quiz_id, account_id)
);
