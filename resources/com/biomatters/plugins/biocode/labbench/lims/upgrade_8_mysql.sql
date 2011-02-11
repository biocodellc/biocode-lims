ALTER TABLE assembly ADD `submitted` tinyint(1) NOT NULL default '0';
ALTER TABLE assembly ADD `editrecord` longtext;


UPDATE databaseversion SET version = 9;