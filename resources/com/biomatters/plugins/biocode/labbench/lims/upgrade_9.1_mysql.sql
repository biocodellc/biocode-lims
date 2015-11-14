CREATE TABLE gel_quantification (
  id int(10) unsigned NOT NULL auto_increment,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  extractionId INT(10) UNSIGNED NOT NULL,
  plate int(10) unsigned NOT NULL,
  location int(10) unsigned NOT NULL,
  technician VARCHAR(255),
  notes longtext,
  volume double, -- Is this different to extraction vol?
  gelImage longblob,
  gelBuffer VARCHAR(255),
  gelConc VARCHAR(255),  -- Check how we do conc in other cases.  Is it a varchar?
  stain VARCHAR(255),
  stainConc VARCHAR(255),  -- Check how we do conc in other cases.  Is it a varchar?
  stainMethod VARCHAR(255),
  gelLadder VARCHAR(255),  -- Should we make ladder it's own thign?  Then threshold would be a drop down?  SI only asked for open field though....
  threshold VARCHAR(255),  -- Bulk edit?  Or no?
  percent_above_threshold DOUBLE,  -- Needs to be available via Bulk Edit
  PRIMARY KEY (id),
  FOREIGN KEY (extractionId) REFERENCES extraction(id) ON DELETE CASCADE ,
  FOREIGN KEY (plate) REFERENCES plate(id) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

UPDATE databaseversion SET version = 9;
INSERT INTO properties (name,value) VALUES ('fullDatabaseVersion', '9.2');