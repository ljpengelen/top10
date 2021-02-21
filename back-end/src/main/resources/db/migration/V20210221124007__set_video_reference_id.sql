UPDATE video SET reference_id = substring(url, '[^/]+$') WHERE reference_id IS NULL;
