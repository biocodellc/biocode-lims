CREATE TABLE gel_quantification (
  id int(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  extractionId INT(10) UNSIGNED NOT NULL,
  plate int(10) UNSIGNED NOT NULL,
  location int(10) UNSIGNED NOT NULL,
  technician VARCHAR(255),
  notes LONGTEXT,
  volume DOUBLE,
  gelImage longblob,
  gelBuffer VARCHAR(255),
  gelConc DOUBLE,
  stain VARCHAR(255),
  stainConc VARCHAR(255),
  stainMethod VARCHAR(255),
  gelLadder VARCHAR(255),
  threshold INTEGER,
  aboveThreshold INTEGER,
  PRIMARY KEY (id),
  FOREIGN KEY (extractionId) REFERENCES extraction(id) ON DELETE CASCADE ,
  FOREIGN KEY (plate) REFERENCES plate(id) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

UPDATE databaseversion SET version = 9;
INSERT INTO properties (name,value) VALUES ('fullDatabaseVersion', '9.2');