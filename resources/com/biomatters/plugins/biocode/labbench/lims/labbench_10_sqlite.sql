-- MySQL Administrator dump 1.4
--
-- ------------------------------------------------------
-- Server version	5.0.77


--
-- Definition of table  plate
--

DROP TABLE IF EXISTS  plate ;
CREATE TABLE  plate  (
   id  INTEGER PRIMARY KEY IDENTITY,
   name  varchar(64) default 'plate' ,
   date  date default CURRENT_TIMESTAMP,
   size  INTEGER NOT NULL,
   type  varchar(45) NOT NULL,
   thermocycle INTEGER default -1
);

--
-- Dumping data for table  plate
--


--
-- Definition of table  extraction
--

DROP TABLE IF EXISTS extraction;
CREATE TABLE extraction (
  id INTEGER PRIMARY KEY IDENTITY,
  date timestamp DEFAULT CURRENT_TIMESTAMP,
  method varchar(45) NOT NULL,
  volume double NOT NULL,
  dilution double,
  concentrationStored tinyint DEFAULT 0 NOT NULL,
  concentration double,
  parent varchar(45) NOT NULL,
  sampleId varchar(45) NOT NULL,
  extractionId varchar(45) NOT NULL,
  control varchar(45) NOT NULL,
  plate INTEGER  NOT NULL,
  location INTEGER  NOT NULL,
  technician varchar(90) NOT NULL,
  notes LONGVARCHAR NOT NULL,
  extractionBarcode varchar(45) NOT NULL,
  previousPlate varchar(45) NOT NULL,
  previousWell varchar(45) NOT NULL,
  gelimage LONGVARBINARY,
  FOREIGN KEY (plate) REFERENCES plate(id)
);


--
-- Definition of table  workflow
--

DROP TABLE IF EXISTS  workflow ;
CREATE TABLE  workflow  (
   id  INTEGER PRIMARY KEY IDENTITY,
   name  varchar(45) default 'workflow',
   date date DEFAULT CURRENT_TIMESTAMP,
   extractionId  INTEGER NOT NULL,
   locus varchar(45) default 'COI' NOT NULL,
  FOREIGN KEY (extractionId) REFERENCES extraction(id)
);

--
-- Definition of table  assembly
--

DROP TABLE IF EXISTS assembly;
CREATE TABLE assembly (
  id INTEGER PRIMARY KEY IDENTITY,
  extraction_id VARCHAR(45) NOT NULL,
  workflow INTEGER NOT NULL,
  progress VARCHAR(45) NOT NULL,
  consensus LONGVARCHAR,
  params LONGVARCHAR,
  coverage FLOAT,
  disagreements INTEGER ,
  edits INTEGER ,
  reference_seq_id INTEGER ,
  confidence_scores LONGVARCHAR,
  trim_params_fwd LONGVARCHAR,
  trim_params_rev LONGVARCHAR,
  other_processing_fwd LONGVARCHAR,
  other_processing_rev LONGVARCHAR,
  date timestamp DEFAULT CURRENT_TIMESTAMP,
  submitted tinyint DEFAULT 0 NOT NULL,
  notes LONGVARCHAR,
  editrecord LONGVARCHAR,
  technician VARCHAR(255),
  bin VARCHAR(255),
  ambiguities INTEGER,
  FOREIGN KEY (workflow) REFERENCES workflow(id)
);


--
-- Definition of table  thermocycle
--

DROP TABLE IF EXISTS  thermocycle ;
CREATE TABLE  thermocycle  (
   id  INTEGER PRIMARY KEY IDENTITY,
   name  varchar(64),
   notes  LONGVARCHAR NOT NULL
);

--
-- Dumping data for table  thermocycle
--

INSERT INTO  thermocycle  ( id , name , notes ) VALUES
 (15, 'Example Thermocycle' , 'An example cycle...' );



--
-- Definition of table  cycle
--

DROP TABLE IF EXISTS cycle;
CREATE TABLE cycle (
  id INTEGER PRIMARY KEY IDENTITY,
  thermocycleId INTEGER,
  repeats INTEGER,
  FOREIGN KEY (thermocycleId) REFERENCES thermocycle(id)
);

--
-- Dumping data for table  cycle
--

INSERT INTO cycle VALUES  (7,15,1),
 (8,15,25),
 (9,15,1);


 --
 -- Definition of table  state
 --

 DROP TABLE IF EXISTS  state ;
 CREATE TABLE  state  (
    id  INTEGER PRIMARY KEY IDENTITY,
    temp  INTEGER  NOT NULL,
    length  INTEGER  NOT NULL,
    cycleId  INTEGER,
   FOREIGN KEY (cycleId) REFERENCES cycle(id)
 );

 --
 -- Dumping data for table  state
 --

 INSERT INTO  state  ( id , temp , length , cycleId ) VALUES
  (15,20,60,7),
  (16,90,30,8),
  (17,55,120,8),
  (18,75,30,8),
 (19,20,540,9);


--
-- Definition of table  cyclesequencing_cocktail
--

DROP TABLE IF EXISTS cyclesequencing_cocktail;
CREATE TABLE cyclesequencing_cocktail (
  id INTEGER PRIMARY KEY IDENTITY,
  name varchar(99) NOT NULL,
  ddh2o double NOT NULL,
  buffer double NOT NULL,
  bigDye double NOT NULL,
  notes LONGVARCHAR NOT NULL,
  bufferConc double NOT NULL,
  bigDyeConc double NOT NULL,
  templateConc double NOT NULL,
  primerConc double NOT NULL,
  primerAmount double NOT NULL,
  extraItem LONGVARCHAR NOT NULL,
  extraItemAmount double NOT NULL,
  templateAmount double NOT NULL
);

--
-- Dumping data for table  cyclesequencing_cocktail
--

INSERT INTO cyclesequencing_cocktail ( id , name , ddh2o , buffer , bigDye , notes , bufferConc , bigDyeConc , templateConc , primerConc , primerAmount , extraItem , extraItemAmount, templateAmount ) VALUES
 (0,'No Cocktail',0,0,0,' ',0,0,0,0,0,' ',0,0);


--
-- Definition of table  cyclesequencing_thermocycle
--

DROP TABLE IF EXISTS cyclesequencing_thermocycle;
CREATE TABLE cyclesequencing_thermocycle (
  id INTEGER PRIMARY KEY IDENTITY,
  cycle INTEGER NOT NULL
);

--
-- Dumping data for table  cyclesequencing_thermocycle
--

INSERT INTO cyclesequencing_thermocycle ( id , cycle ) VALUES (1,15);

--
-- Definition of table  cyclesequencing
--

DROP TABLE IF EXISTS cyclesequencing;
CREATE TABLE cyclesequencing (
  id INTEGER PRIMARY KEY IDENTITY,
  primerName varchar(64) NOT NULL,
  primerSequence varchar(999) NOT NULL,
  technician varchar(90) NOT NULL,
  notes LONGVARCHAR NOT NULL,
  date timestamp DEFAULT CURRENT_TIMESTAMP,
  workflow INTEGER ,
  thermocycle INTEGER NOT NULL,
  plate INTEGER  NOT NULL,
  location INTEGER NOT NULL,
  extractionId varchar(45) NOT NULL,
  cocktail INTEGER  NOT NULL,
  progress varchar(45) NOT NULL,
  cleanupPerformed tinyint NOT NULL,
  cleanupMethod varchar(99) NOT NULL,
  direction varchar(32) NOT NULL,
  gelimage LONGVARBINARY,
  assembly INTEGER NULL,
  --PRIMARY KEY  ( id ),
  FOREIGN KEY (plate) REFERENCES plate(id),
  FOREIGN KEY (workflow) REFERENCES workflow(id),
  FOREIGN KEY (cocktail) REFERENCES cyclesequencing_cocktail(id),
  FOREIGN KEY (assembly) REFERENCES assembly(id) ON DELETE SET NULL
);




--
-- Definition of table  databaseversion
--

DROP TABLE IF EXISTS databaseversion;
CREATE TABLE  databaseversion  (
  version INTEGER  PRIMARY KEY--,
  --PRIMARY KEY  ( version )
);

--
-- Dumping data for table  databaseversion
--

INSERT INTO databaseversion VALUES  (10);




--
-- Definition of table  gelimages
--

DROP TABLE IF EXISTS gelimages;
CREATE TABLE gelimages (
  id INTEGER PRIMARY KEY IDENTITY,
  name VARCHAR(45) NOT NULL,
  plate INTEGER NOT NULL,
  imageData LONGVARBINARY,
  notes LONGVARCHAR NOT NULL,
  FOREIGN KEY (plate) REFERENCES plate(id)
);

--
-- Dumping data for table  gelimages
--



--
-- Definition of table  pcr_cocktail
--

DROP TABLE IF EXISTS  pcr_cocktail ;
CREATE TABLE  pcr_cocktail  (
   id  INTEGER  PRIMARY KEY IDENTITY,
   name  varchar(99) NOT NULL,
   ddH20  double NOT NULL ,
   buffer  double NOT NULL ,
   mg  double NOT NULL ,
   bsa  double NOT NULL ,
   dNTP  double NOT NULL ,
   taq  double NOT NULL ,
   notes  LONGVARCHAR NOT NULL,
   bufferConc  double NOT NULL ,
   mgConc  double NOT NULL ,
   dNTPConc  double NOT NULL ,
   taqConc  double NOT NULL ,
   templateConc  double NOT NULL ,
   bsaConc  double NOT NULL,
   fwPrAmount  double NOT NULL,
   fwPrConc  double NOT NULL,
   revPrAmount  double NOT NULL,
   revPrConc  double NOT NULL,
   extraItem  LONGVARCHAR NOT NULL,
   extraItemAmount  double NOT NULL,
   templateAmount  double NOT NULL
);

--
-- Dumping data for table  pcr_cocktail
--

INSERT INTO  pcr_cocktail  ( id , name , ddH20 , buffer , mg , bsa , dNTP , taq , notes , bufferConc , mgConc , dNTPConc , taqConc , templateConc , bsaConc , fwPrAmount , fwPrConc , revPrAmount , revPrConc , extraItem , extraItemAmount, templateAmount ) VALUES
 (0, 'No Cocktail' ,0,0,0,0,0,0, '' ,0,0,0,0,0,0,0,0,0,0, '' ,0, 0);


--
-- Definition of table  pcr_thermocycle
--

DROP TABLE IF EXISTS  pcr_thermocycle ;
CREATE TABLE  pcr_thermocycle  (
   id  INTEGER PRIMARY KEY IDENTITY,
   cycle INTEGER
);

--
-- Dumping data for table  pcr_thermocycle
--

INSERT INTO  pcr_thermocycle  ( id , cycle ) VALUES
 (2,15);



--
-- Definition of table  pcr
--

DROP TABLE IF EXISTS pcr;
CREATE TABLE  pcr  (
  id INTEGER PRIMARY KEY IDENTITY,
  prName varchar(64),
  prSequence varchar(999),
  date timestamp default CURRENT_TIMESTAMP,
  workflow INTEGER ,
  plate INTEGER NOT NULL,
  location INTEGER NOT NULL,
  cocktail INTEGER NOT NULL,
  progress varchar(45) NOT NULL,
  extractionId varchar(45) NOT NULL,
  thermocycle INTEGER default  -1 ,
  cleanupPerformed tinyint default  0 ,
  cleanupMethod varchar(45) NOT NULL,
  technician varchar(90) NOT NULL,
  notes LONGVARCHAR NOT NULL,
  revPrName varchar(64) NOT NULL,
  revPrSequence varchar(999) NOT NULL,
  gelimage LONGVARBINARY,
  FOREIGN KEY (workflow) REFERENCES workflow(id),
  FOREIGN KEY (cocktail) REFERENCES pcr_cocktail(id),
  FOREIGN KEY (plate) REFERENCES plate(id)
);

--
-- Dumping data for table  pcr
--





--
-- Definition of table  traces
--

DROP TABLE IF EXISTS  traces ;
CREATE TABLE  traces  (
   id  INTEGER PRIMARY KEY IDENTITY,
   reaction  INTEGER NOT NULL,
   name  varchar(96) NOT NULL,
   data  LONGVARBINARY NOT NULL,
  FOREIGN KEY (reaction) REFERENCES cyclesequencing(id) ON DELETE CASCADE
);

--
-- Dumping data for table  traces
--


--
-- Definition of table `properties`
--
CREATE TABLE properties (
    name     VARCHAR(255)     PRIMARY KEY,
    value    VARCHAR(255)
);




