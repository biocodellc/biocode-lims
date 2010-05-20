ALTER TABLE extraction ADD technician varchar(45) DEFAULT '' NOT NULL ;
ALTER TABLE pcr ADD technician varchar(45) DEFAULT '' NOT NULL ;
ALTER TABLE cyclesequencing ADD technician varchar(45) DEFAULT '' NOT NULL ;

ALTER TABLE `labbench`.`gelimages` ADD COLUMN `name` VARCHAR(45) NOT NULL DEFAULT 'Image' AFTER `notes`;

UPDATE databaseVersion SET version = 6;