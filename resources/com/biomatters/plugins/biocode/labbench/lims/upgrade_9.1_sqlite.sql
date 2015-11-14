CREATE TABLE gel_quantification (
  id INTEGER PRIMARY KEY IDENTITY,
  date timestamp default CURRENT_TIMESTAMP,
  extractionId INTEGER NOT NULL,
  plate INTEGER NOT NULL,
  location INTEGER NOT NULL,
  technician VARCHAR(255),
  notes LONGVARCHAR,
  volume double, -- Is this different to extraction vol?
  gelImage LONGVARBINARY,
  gelBuffer VARCHAR(255),
  gelConc DOUBLE,
  stain VARCHAR(255),
  stainConc VARCHAR(255),
  stainMethod VARCHAR(255),
  gelLadder VARCHAR(255),
  threshold INTEGER,
  aboveThreshold INTEGER,
  FOREIGN KEY (extractionId) REFERENCES extraction(id) ON DELETE CASCADE ,
  FOREIGN KEY (plate) REFERENCES plate(id) ON DELETE CASCADE
);

UPDATE databaseversion SET version = 9;
INSERT INTO properties (name,value) VALUES ('fullDatabaseVersion', '9.2');