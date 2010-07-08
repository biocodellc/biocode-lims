ALTER TABLE extraction ADD gelimage longblob DEFAULT NULL ;
ALTER TABLE pcr ADD gelimage longblob DEFAULT NULL ;
ALTER TABLE cyclesequencing ADD gelimage longblob DEFAULT NULL ;


UPDATE databaseVersion SET version = 7;