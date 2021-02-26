alter table quiz add column creator_uuid uuid;

update quiz set creator_uuid = uuid_generate_v5(uuid_nil(), creator_id::text);
