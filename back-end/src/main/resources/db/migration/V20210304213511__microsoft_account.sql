create table microsoft_account (
  microsoft_account_id character varying,
  account_id uuid references account(account_id)
);
