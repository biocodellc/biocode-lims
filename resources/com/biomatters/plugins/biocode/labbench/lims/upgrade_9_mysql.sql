CREATE TABLE failure_reason (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    name	varchar(80),
    description	varchar(255)
) ENGINE=INNODB;

ALTER TABLE assembly ADD COLUMN failure_reason INT NULL;
ALTER TABLE assembly ADD FOREIGN KEY(failure_reason) REFERENCES failure_reason(id) ON DELETE SET NULL;

ALTER TABLE cyclesequencing ADD COLUMN assembly INT(10) UNSIGNED NULL;
ALTER TABLE cyclesequencing ADD FOREIGN KEY(assembly) REFERENCES assembly(id) ON DELETE SET NULL;

CREATE TABLE properties (
    name     VARCHAR(255)     PRIMARY KEY,
    value    VARCHAR(255)
) ENGINE=INNODB;

UPDATE databaseversion SET version = 10;