CREATE TABLE sequencing_result (
    reaction  INT(11) NULL,
    assembly  INT(10) UNSIGNED NULL,
    PRIMARY KEY (reaction, assembly),
    FOREIGN KEY(reaction) REFERENCES cyclesequencing(id) ON DELETE CASCADE,
    FOREIGN KEY(assembly) REFERENCES assembly(id) ON DELETE CASCADE
) ENGINE=INNODB;


CREATE TABLE failure_reason (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    name	varchar(80),
    description	varchar(255)
) ENGINE=INNODB;

ALTER TABLE assembly ADD COLUMN failure_reason INT NULL;
ALTER TABLE assembly ADD FOREIGN KEY(failure_reason) REFERENCES failure_reason(id) ON DELETE SET NULL;

CREATE TABLE properties (
    name     VARCHAR(255)     PRIMARY KEY,
    value    VARCHAR(255)
) ENGINE=INNODB;

UPDATE databaseversion SET version = 10;