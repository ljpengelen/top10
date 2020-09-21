ALTER TABLE google_account
ALTER COLUMN account_id DROP DEFAULT;

ALTER TABLE quiz
ALTER COLUMN creator_id DROP DEFAULT;

ALTER TABLE participant
ALTER COLUMN quiz_id DROP DEFAULT,
ALTER COLUMN account_id DROP DEFAULT;

ALTER TABLE list
ALTER COLUMN quiz_id DROP DEFAULT,
ALTER COLUMN account_id DROP DEFAULT;

ALTER TABLE video
ALTER COLUMN list_id DROP DEFAULT;

DROP SEQUENCE google_account_account_id_seq;
DROP SEQUENCE list_account_id_seq;
DROP SEQUENCE list_quiz_id_seq;
DROP SEQUENCE participant_account_id_seq;
DROP SEQUENCE participant_quiz_id_seq;
DROP SEQUENCE quiz_creator_id_seq;
DROP SEQUENCE video_list_id_seq;
