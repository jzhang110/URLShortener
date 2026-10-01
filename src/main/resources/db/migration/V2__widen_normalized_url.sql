-- Portable DDL: runs unchanged on H2 (MODE=PostgreSQL) and PostgreSQL. SET DATA TYPE keeps NOT NULL.
-- Normalization can lengthen a URL by exactly one character: N5 turns an empty path into "/".
-- No other rule adds characters (N1 and N4 only remove, N2 and N3 preserve length), and
-- submitted URLs are capped at 2048 characters, so the normalized form needs at most 2049.
-- destination_url stays VARCHAR(2048): it is the trimmed input itself.

ALTER TABLE url_mapping ALTER COLUMN normalized_url SET DATA TYPE VARCHAR(2049);
