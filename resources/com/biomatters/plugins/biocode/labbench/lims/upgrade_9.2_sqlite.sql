/**
During the 2.99.x beta the 3.0 database schema was 9.2.  So we need to update the schema version if users are using it
 */
update databaseversion set version = 11 where version = 9;
update properties set value = '11.0' where name = 'fullDatabaseVersion';