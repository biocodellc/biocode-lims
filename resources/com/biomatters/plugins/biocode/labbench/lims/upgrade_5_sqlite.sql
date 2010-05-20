ALTER TABLE extraction ADD technician varchar(45) DEFAULT '' NOT NULL ;
ALTER TABLE pcr ADD technician varchar(45) DEFAULT '' NOT NULL ;
ALTER TABLE cyclesequencing ADD technician varchar(45) DEFAULT '' NOT NULL ;

ALTER TABLE gelimages ADD name VARCHAR(45) DEFAULT 'Image' NOT NULL ;

UPDATE databaseVersion SET version = 6;