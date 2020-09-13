CREATE TABLE account (
  account_id SERIAL PRIMARY KEY,
  name character varying,
  email_address character varying
);

CREATE TABLE google_account (
  google_account_id character varying,
  account_id SERIAL REFERENCES account(account_id)
);
