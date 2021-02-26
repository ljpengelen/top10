alter table account drop constraint account_pkey cascade;
alter table list drop constraint list_pkey cascade;
alter table quiz drop constraint quiz_pkey cascade;
alter table video drop constraint video_pkey cascade;

alter table account
    drop column account_id,
    drop column external_id;
alter table assignment
    drop column account_id,
    drop column list_id,
    drop column assignee_id;
alter table google_account drop column account_id;
alter table list
    drop column list_id,
    drop column account_id,
    drop column quiz_id,
    drop column if exists external_id;
alter table quiz
    drop column quiz_id,
    drop column external_id,
    drop column creator_id;
alter table video
    drop column video_id,
    drop column list_id,
    drop column if exists external_id;

alter table account rename column account_uuid to account_id;
alter table assignment rename column account_uuid to account_id;
alter table assignment rename column list_uuid to list_id;
alter table assignment rename column assignee_uuid to assignee_id;
alter table google_account rename column account_uuid to account_id;
alter table list rename column list_uuid to list_id;
alter table list rename column account_uuid to account_id;
alter table list rename column quiz_uuid to quiz_id;
alter table quiz rename column creator_uuid to creator_id;
alter table quiz rename column quiz_uuid to quiz_id;
alter table video rename column video_uuid to video_id;
alter table video rename column list_uuid to list_id;
