ALTER TABLE extraction ADD gelimage longblob DEFAULT NULL ;
ALTER TABLE pcr ADD gelimage longblob DEFAULT NULL ;
ALTER TABLE cyclesequencing ADD gelimage longblob DEFAULT NULL;

ALTER table extraction ADD control varchar(45) DEFAULT '' NOT NULL;


UPDATE databaseversion SET version = 7;