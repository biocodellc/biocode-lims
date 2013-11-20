CREATE TABLE sequencing_result (
    reaction  INTEGER,
    assembly  INTEGER,
    PRIMARY KEY (reaction, assembly),
    FOREIGN KEY(reaction) REFERENCES cyclesequencing(id) ON DELETE CASCADE,
    FOREIGN KEY(assembly) REFERENCES assembly(id) ON DELETE CASCADE
);

CREATE TABLE failure_reason (
    id      INT IDENTITY PRIMARY KEY,
    name	varchar(80),
    description	varchar(255)
);

ALTER TABLE assembly ADD COLUMN failure_reason INT NULL;
ALTER TABLE assembly ADD COLUMN failure_notes LONGVARCHAR;
ALTER TABLE assembly ADD FOREIGN KEY(failure_reason) REFERENCES failure_reason(id) ON DELETE SET NULL;

CREATE TABLE properties (
    name     VARCHAR(255)     PRIMARY KEY,
    value    VARCHAR(255)
);

UPDATE databaseversion SET version = 9;
INSERT INTO properties (name,value) VALUES ('fullDatabaseVersion', '9.1');