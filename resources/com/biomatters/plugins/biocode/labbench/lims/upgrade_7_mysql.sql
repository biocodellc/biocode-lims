ALTER TABLE assembly ADD technician varchar(255) ;
ALTER TABLE assembly ADD bin varchar(255) ;
ALTER TABLE assembly ADD ambiguities int(10);


UPDATE databaseversion SET version = 8;