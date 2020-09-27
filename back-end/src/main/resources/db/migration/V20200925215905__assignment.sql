CREATE TABLE assignment (
  assignment_id SERIAL PRIMARY KEY,
  list_id integer REFERENCES list(list_id),
  account_id integer REFERENCES account(account_id),
  assignee_id integer REFERENCES account(account_id),
  UNIQUE(list_id, account_id, assignee_id)
);
