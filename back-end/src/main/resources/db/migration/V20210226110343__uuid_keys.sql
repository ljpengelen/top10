alter table account add primary key (account_id);
alter table list add primary key (list_id);
alter table quiz add primary key (quiz_id);
alter table video add primary key (video_id);

alter table assignment add foreign key (list_id) references list (list_id);
alter table assignment add foreign key (account_id) references account (account_id);
alter table assignment add foreign key (assignee_id) references account (account_id);
alter table google_account add foreign key (account_id) references account (account_id);
alter table list add foreign key (account_id) references account (account_id);
alter table list add foreign key (quiz_id) references quiz (quiz_id);
alter table quiz add foreign key (creator_id) references account (account_id);

alter table list add unique (account_id, quiz_id);
alter table assignment add unique (list_id, account_id);
