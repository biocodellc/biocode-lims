CREATE INDEX plate_name ON plate (name)
CREATE INDEX workflow_date ON workflow (date)
CREATE INDEX workflow_locus ON workflow (locus)
CREATE INDEX plate_type ON plate (type)
CREATE INDEX plate_date ON plate (date)
CREATE INDEX extraction_extractionBarcode ON extraction (extractionBarcode)
CREATE INDEX extraction_date ON extraction (date)
CREATE INDEX assembly_progress ON assembly (progress)
CREATE INDEX assembly_submitted ON assembly (submitted)
CREATE INDEX assembly_technician ON assembly (technician)
CREATE INDEX assembly_date ON assembly (date)
CREATE INDEX extraction_sampleId ON extraction (sampleId)

UPDATE databaseversion SET version = 9;
UPDATE properties SET value = 9.2 where name = 'fullDatabaseVersion';