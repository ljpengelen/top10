ALTER TABLE account
ADD COLUMN first_login_at timestamptz,
ADD COLUMN last_login_at timestamptz,
ADD COLUMN number_of_logins integer DEFAULT 0;
