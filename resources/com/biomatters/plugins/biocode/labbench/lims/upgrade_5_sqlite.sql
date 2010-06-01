ALTER TABLE extraction ADD technician varchar(90) DEFAULT '' NOT NULL ;
ALTER TABLE pcr ADD technician varchar(90) DEFAULT '' NOT NULL ;
ALTER TABLE cyclesequencing ADD technician varchar(90) DEFAULT '' NOT NULL ;

ALTER TABLE gelimages ADD name VARCHAR(45) DEFAULT 'Image' NOT NULL ;

ALTER TABLE workflow ADD COLUMN date timestamp DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE extraction ADD COLUMN concentrationStored tinyint DEFAULT 0 NOT NULL;
ALTER TABLE extraction ADD COLUMN concentration double DEFAULT 0 NOT NULL;

ALTER TABLE workflow ADD COLUMN locus varchar(45) default 'COI' NOT NULL;

UPDATE databaseVersion SET version = 6;