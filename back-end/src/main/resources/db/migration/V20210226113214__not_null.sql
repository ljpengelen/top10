alter table google_account alter account_id set not null;
alter table assignment alter list_id set not null;
alter table assignment alter account_id set not null;
alter table assignment alter assignee_id set not null;
alter table list alter account_id set not null;
alter table list alter quiz_id set not null;
alter table quiz alter creator_id set not null;
alter table video alter list_id set not null;
