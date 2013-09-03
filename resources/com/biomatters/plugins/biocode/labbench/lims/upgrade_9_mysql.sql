ALTER TABLE cyclesequencing ADD COLUMN assembly INT(10) UNSIGNED NULL;
ALTER TABLE cyclesequencing ADD FOREIGN KEY(assembly) REFERENCES assembly(id) ON DELETE SET NULL;

CREATE TABLE properties (
    name     VARCHAR(255)     PRIMARY KEY,
    value    VARCHAR(255)
) ENGINE=INNODB;

UPDATE databaseversion SET version = 10;