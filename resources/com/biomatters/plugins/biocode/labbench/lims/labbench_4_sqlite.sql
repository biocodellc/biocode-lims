-- MySQL Administrator dump 1.4
-- MODIFIED FOR SQLITE
-- ------------------------------------------------------
-- Server version	5.0.77

--
-- Definition of table `assembly`
--

DROP TABLE IF EXISTS `assembly`;
CREATE TABLE `assembly` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT ,
  `extraction_id` varchar(45) NOT NULL,
  `workflow` INTEGER  NOT NULL,
  `progress` varchar(45) NOT NULL,
  `consensus` longtext,
  `params` longtext,
  `coverage` float default NULL,
  `disagreements` INTEGER  default NULL,
  `edits` INTEGER  default NULL,
  `reference_seq_id` INTEGER  default NULL,
  `confidence_scores` longtext,
  `trim_params_fwd` longtext,
  `trim_params_rev` longtext,
  `other_processing_fwd` longtext,
  `other_processing_rev` longtext,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `notes` longtext
);


--
-- Definition of table `cycle`
--

DROP TABLE IF EXISTS `cycle`;
CREATE TABLE `cycle` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT  ,
  `thermocycleId` INTEGER default NULL,
  `repeats` INTEGER default NULL
) ;

--
-- Dumping data for table `cycle`
--

INSERT INTO `cycle` VALUES  (7,15,1);
INSERT INTO `cycle` VALUES   (8,15,25);
INSERT INTO `cycle` VALUES   (9,15,1);


--
-- Definition of table `cyclesequencing`
--

DROP TABLE IF EXISTS `cyclesequencing`;
CREATE TABLE `cyclesequencing` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT  ,
  `primerName` varchar(64) NOT NULL,
  `primerSequence` varchar(999) NOT NULL,
  `notes` longtext NOT NULL,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `workflow` INTEGER  default NULL,
  `thermocycle` INTEGER NOT NULL,
  `plate` INTEGER  NOT NULL,
  `location` INTEGER NOT NULL,
  `extractionId` varchar(45) NOT NULL,
  `cocktail` INTEGER  NOT NULL,
  `progress` varchar(45) NOT NULL,
  `cleanupPerformed` tinyint(1) NOT NULL default '0',
  `cleanupMethod` varchar(99) NOT NULL,
  `direction` varchar(32) NOT NULL
);



--
-- Definition of table `cyclesequencing_cocktail`
--

DROP TABLE IF EXISTS `cyclesequencing_cocktail`;
CREATE TABLE `cyclesequencing_cocktail` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT ,
  `name` varchar(99) NOT NULL,
  `ddh2o` double NOT NULL,
  `buffer` double NOT NULL,
  `bigDye` double NOT NULL,
  `notes` longtext NOT NULL,
  `bufferConc` double NOT NULL,
  `bigDyeConc` double NOT NULL,
  `templateConc` double NOT NULL,
  `primerConc` double NOT NULL,
  `primerAmount` double NOT NULL,
  `extraItem` mediumtext NOT NULL,
  `extraItemAmount` double NOT NULL,
  `templateAmount` double NOT NULL
) ;

--
-- Dumping data for table `cyclesequencing_cocktail`
--

INSERT INTO `cyclesequencing_cocktail` (`id`,`name`,`ddh2o`,`buffer`,`bigDye`,`notes`,`bufferConc`,`bigDyeConc`,`templateConc`,`primerConc`,`primerAmount`,`extraItem`,`extraItemAmount`, `templateAmount`) VALUES 
 (0,'No Cocktail',0,0,0,' ',0,0,0,0,0,' ',0, 0);


--
-- Definition of table `cyclesequencing_thermocycle`
--

DROP TABLE IF EXISTS `cyclesequencing_thermocycle`;
CREATE TABLE `cyclesequencing_thermocycle` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT  ,
  `cycle` INTEGER NOT NULL
) ;

--
-- Dumping data for table `cyclesequencing_thermocycle`
--

INSERT INTO `cyclesequencing_thermocycle` (`id`,`cycle`) VALUES 
 (1,15);


--
-- Definition of table `databaseversion`
--

DROP TABLE IF EXISTS `databaseversion`;
CREATE TABLE `databaseversion` (
  `version` INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT
);

--
-- Dumping data for table `databaseversion`
--


INSERT INTO `databaseversion` VALUES  (4);


--
-- Definition of table `extraction`
--

DROP TABLE IF EXISTS `extraction`;
CREATE TABLE `extraction` (
  `id` INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT ,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `method` varchar(45) NOT NULL,
  `volume` double NOT NULL,
  `dilution` double default NULL,
  `parent` varchar(45) NOT NULL,
  `sampleId` varchar(45) NOT NULL,
  `extractionId` varchar(45) NOT NULL,
  `plate` INTEGER  NOT NULL,
  `location` INTEGER  NOT NULL,
  `notes` longtext NOT NULL,
  `extractionBarcode` varchar(45) NOT NULL
);




--
-- Definition of table `gelimages`
--

DROP TABLE IF EXISTS `gelimages`;
CREATE TABLE `gelimages` (
  `id` INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT ,
  `plate` INTEGER NOT NULL default '0',
  `imageData` longblob,
  `notes` longtext NOT NULL
);

--
-- Dumping data for table `gelimages`
--



--
-- Definition of table `pcr`
--

DROP TABLE IF EXISTS `pcr`;
CREATE TABLE `pcr` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT  ,
  `prName` varchar(64) default NULL,
  `prSequence` varchar(999) default NULL,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `workflow` INTEGER  default NULL,
  `plate` INTEGER  NOT NULL,
  `location` INTEGER NOT NULL,
  `cocktail` INTEGER  NOT NULL,
  `progress` varchar(45) NOT NULL,
  `extractionId` varchar(45) NOT NULL,
  `thermocycle` INTEGER NOT NULL default '-1',
  `cleanupPerformed` tinyint(1) NOT NULL default '0',
  `cleanupMethod` varchar(45) NOT NULL,
  `notes` longtext NOT NULL,
  `revPrName` varchar(64) NOT NULL,
  `revPrSequence` varchar(999) NOT NULL
  
);

--
-- Dumping data for table `pcr`
--


--
-- Definition of table `pcr_cocktail`
--

DROP TABLE IF EXISTS `pcr_cocktail`;
CREATE TABLE `pcr_cocktail` (
  `id` INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT ,
  `name` varchar(99) NOT NULL,
  `ddH20` double NOT NULL default '0',
  `buffer` double NOT NULL default '0',
  `mg` double NOT NULL default '0',
  `bsa` double NOT NULL default '0',
  `dNTP` double NOT NULL default '0',
  `taq` double NOT NULL default '0',
  `notes` longtext NOT NULL,
  `bufferConc` double NOT NULL default '0',
  `mgConc` double NOT NULL default '0',
  `dNTPConc` double NOT NULL default '0',
  `taqConc` double NOT NULL default '0',
  `templateConc` double NOT NULL default '0',
  `bsaConc` double NOT NULL default '0',
  `fwPrAmount` double NOT NULL,
  `fwPrConc` double NOT NULL,
  `revPrAmount` double NOT NULL,
  `revPrConc` double NOT NULL,
  `extraItem` mediumtext NOT NULL,
  `extraItemAmount` double NOT NULL,
  `templateAmount` double NOT NULL
) ;

--
-- Dumping data for table `pcr_cocktail`
--


INSERT INTO `pcr_cocktail` (`id`,`name`,`ddH20`,`buffer`,`mg`,`bsa`,`dNTP`,`taq`,`notes`,`bufferConc`,`mgConc`,`dNTPConc`,`taqConc`,`templateConc`,`bsaConc`,`fwPrAmount`,`fwPrConc`,`revPrAmount`,`revPrConc`,`extraItem`,`extraItemAmount`, `templateAmount`) VALUES 
 (0,'No Cocktail',0,0,0,0,0,0,' ',0,0,0,0,0,0,0,0,0,0,' ',0,0);



--
-- Definition of table `pcr_thermocycle`
--

DROP TABLE IF EXISTS `pcr_thermocycle`;
CREATE TABLE `pcr_thermocycle` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT  ,
  `cycle` INTEGER default NULL
);

--
-- Dumping data for table `pcr_thermocycle`
--


INSERT INTO `pcr_thermocycle` (`id`,`cycle`) VALUES 
 (2,15);



--
-- Definition of table `plate`
--

DROP TABLE IF EXISTS `plate`;
CREATE TABLE `plate` (
  `id` INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT ,
  `name` varchar(64) NOT NULL default 'plate',
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `size` INTEGER NOT NULL,
  `type` varchar(45) NOT NULL,
  `thermocycle` INTEGER NOT NULL default '-1'
);

--
-- Dumping data for table `plate`
--



--
-- Definition of table `state`
--

DROP TABLE IF EXISTS `state`;
CREATE TABLE `state` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT  ,
  `temp` INTEGER  NOT NULL,
  `length` INTEGER  NOT NULL,
  `cycleId` INTEGER default NULL
);

--
-- Dumping data for table `state`
--


INSERT INTO `state` (`id`,`temp`,`length`,`cycleId`) VALUES  (15,20,60,7);
INSERT INTO `state` (`id`,`temp`,`length`,`cycleId`) VALUES  (16,90,30,8);
INSERT INTO `state` (`id`,`temp`,`length`,`cycleId`) VALUES  (17,55,120,8);
INSERT INTO `state` (`id`,`temp`,`length`,`cycleId`) VALUES  (18,75,30,8);
INSERT INTO `state` (`id`,`temp`,`length`,`cycleId`) VALUES  (19,20,540,9);



--
-- Definition of table `thermocycle`
--

DROP TABLE IF EXISTS `thermocycle`;
CREATE TABLE `thermocycle` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT  ,
  `name` varchar(64) default NULL,
  `notes` longtext NOT NULL
) ;

--
-- Dumping data for table `thermocycle`
--

INSERT INTO `thermocycle` (`id`,`name`,`notes`) VALUES 
 (15,'Example Thermocycle','An example cycle...');


--
-- Definition of table `traces`
--

DROP TABLE IF EXISTS `traces`;
CREATE TABLE `traces` (
  `id` INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT ,
  `reaction` INTEGER NOT NULL,
  `name` varchar(96) NOT NULL,
  `data` longblob NOT NULL
) ;

--
-- Dumping data for table `traces`
--



--
-- Definition of table `workflow`
--

DROP TABLE IF EXISTS `workflow`;
CREATE TABLE `workflow` (
  `id` INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT ,
  `name` varchar(45) NOT NULL default 'workflow',
  `extractionId` INTEGER  NOT NULL
) ;
