ALTER TABLE extraction ADD previousPlate varchar(45) DEFAULT '' NOT NULL ;
ALTER TABLE extraction ADD previousWell varchar(45) DEFAULT '' NOT NULL;

UPDATE databaseVersion SET version = 5;