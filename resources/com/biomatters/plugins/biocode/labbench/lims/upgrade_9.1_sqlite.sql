CREATE INDEX plate_name ON plate (name);
CREATE INDEX workflow_date ON workflow (date);
CREATE INDEX workflow_locus ON workflow (locus);
CREATE INDEX plate_type ON plate (type);
CREATE INDEX plate_date ON plate (date);
CREATE INDEX extraction_extractionBarcode ON extraction (extractionBarcode);
CREATE INDEX extraction_date ON extraction (date);
CREATE INDEX assembly_progress ON assembly (progress);
CREATE INDEX assembly_submitted ON assembly (submitted);
CREATE INDEX assembly_technician ON assembly (technician);
CREATE INDEX assembly_date ON assembly (date);
CREATE INDEX extraction_sampleId ON extraction (sampleId);

CREATE TABLE gel_quantification (
  id INTEGER PRIMARY KEY IDENTITY,
  date timestamp default CURRENT_TIMESTAMP,
  extractionId INTEGER NOT NULL,
  plate INTEGER NOT NULL,
  location INTEGER NOT NULL,
  technician VARCHAR(255),
  notes LONGVARCHAR,
  volume double,
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

UPDATE databaseversion SET version = 11;
INSERT INTO properties (name,value) VALUES ('fullDatabaseVersion', '11.0');