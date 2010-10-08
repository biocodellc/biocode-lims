ALTER TABLE assembly ADD technician VARCHAR(255) ;
ALTER TABLE assembly ADD bin VARCHAR(255) ;
ALTER TABLE assembly ADD ambiguities  INTEGER;


UPDATE databaseversion SET version = 8;