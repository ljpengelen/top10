ALTER TABLE google_account
ALTER COLUMN account_id TYPE integer;

ALTER TABLE quiz
ALTER COLUMN creator_id TYPE integer;

ALTER TABLE participant
ALTER COLUMN quiz_id TYPE integer,
ALTER COLUMN account_id TYPE integer;

ALTER TABLE list
ALTER COLUMN quiz_id TYPE integer,
ALTER COLUMN account_id TYPE integer;

ALTER TABLE video
ALTER COLUMN list_id TYPE integer;
