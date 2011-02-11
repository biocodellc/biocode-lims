ALTER TABLE assembly ADD submitted tinyint DEFAULT 0 NOT NULL;
ALTER TABLE assembly ADD editrecord LONGVARCHAR;


UPDATE databaseversion SET version = 9;