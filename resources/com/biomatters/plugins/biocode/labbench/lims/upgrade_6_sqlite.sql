ALTER TABLE extraction ADD COLUMN gelimage LONGVARBINARY ;
ALTER TABLE pcr ADD COLUMN gelimage LONGVARBINARY ;
ALTER TABLE cyclesequencing ADD COLUMN gelimage LONGVARBINARY ;

ALTER table extraction ADD control varchar(45) DEFAULT '' NOT NULL;

UPDATE databaseVersion SET version = 7;
