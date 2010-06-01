ALTER TABLE extraction ADD technician varchar(90) DEFAULT '' NOT NULL ;
ALTER TABLE pcr ADD technician varchar(90) DEFAULT '' NOT NULL ;
ALTER TABLE cyclesequencing ADD technician varchar(90) DEFAULT '' NOT NULL ;

ALTER TABLE `labbench`.`gelimages` ADD COLUMN `name` VARCHAR(45) NOT NULL DEFAULT 'Image' AFTER `notes`;

ALTER TABLE `labbench`.`workflow` ADD COLUMN `date` timestamp DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE `labbench`.`plate` MODIFY `date` date DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE `labbench`.`extraction` ADD COLUMN `concentrationStored` tinyint(1) NOT NULL default '0';
ALTER TABLE `labbench`.`extraction` ADD COLUMN `concentration` double NOT NULL default '0';

ALTER TABLE `labbench`.`workflow` ADD COLUMN `locus` varchar(45) NOT NULL default 'COI';


UPDATE databaseVersion SET version = 6;