CREATE TABLE failure_reason (
    id      INT IDENTITY PRIMARY KEY,
    name	varchar(80),
    description	varchar(255)
);

ALTER TABLE assembly ADD COLUMN failure_reason INT NULL;
ALTER TABLE assembly ADD FOREIGN KEY(failure_reason) REFERENCES failure_reason(id) ON DELETE SET NULL;

ALTER TABLE cyclesequencing ADD COLUMN assembly INTEGER NULL;
ALTER TABLE cyclesequencing ADD FOREIGN KEY(assembly) REFERENCES assembly(id) ON DELETE SET NULL;

CREATE TABLE properties (
    name     VARCHAR(255)     PRIMARY KEY,
    value    VARCHAR(255)
);

UPDATE databaseversion SET version = 10;