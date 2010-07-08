ALTER TABLE extraction ADD COLUMN gelimage LONGVARBINARY ;
ALTER TABLE pcr ADD COLUMN gelimage LONGVARBINARY ;
ALTER TABLE cyclesequencing ADD COLUMN gelimage LONGVARBINARY ;


UPDATE databaseVersion SET version = 7;
