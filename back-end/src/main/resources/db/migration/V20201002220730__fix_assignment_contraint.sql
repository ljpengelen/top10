ALTER TABLE assignment
DROP CONSTRAINT assignment_list_id_account_id_assignee_id_key,
ADD UNIQUE(list_id, account_id);
