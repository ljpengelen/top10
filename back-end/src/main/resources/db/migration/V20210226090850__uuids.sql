create extension if not exists "pgcrypto";
create extension if not exists "uuid-ossp";

alter table account add column account_uuid uuid;
alter table assignment
    add column list_uuid uuid,
    add column account_uuid uuid,
    add column assignee_uuid uuid;
alter table google_account add column account_uuid uuid;
alter table list
    add column list_uuid uuid,
    add column account_uuid uuid,
    add column quiz_uuid uuid;
alter table quiz add column quiz_uuid uuid;
alter table video
    add column video_uuid uuid,
    add column list_uuid uuid;

update quiz set quiz_uuid = external_id::uuid;

update account set account_uuid = uuid_generate_v5(uuid_nil(), account_id::text);
update assignment
    set list_uuid = uuid_generate_v5(uuid_nil(), list_id::text),
    account_uuid = uuid_generate_v5(uuid_nil(), account_id::text),
    assignee_uuid = uuid_generate_v5(uuid_nil(), assignee_id::text);
update google_account set account_uuid = uuid_generate_v5(uuid_nil(), account_id::text);
update list l
    set list_uuid = uuid_generate_v5(uuid_nil(), list_id::text),
    account_uuid = uuid_generate_v5(uuid_nil(), account_id::text),
    quiz_uuid = (select quiz_uuid from quiz q where q.quiz_id = l.quiz_id);
update video
    set list_uuid = uuid_generate_v5(uuid_nil(), list_id::text),
    video_uuid = uuid_generate_v5(uuid_nil(), video_id::text);
