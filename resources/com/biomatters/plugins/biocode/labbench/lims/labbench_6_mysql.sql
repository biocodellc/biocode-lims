-- MySQL Administrator dump 1.4
--
-- ------------------------------------------------------
-- Server version	5.0.45-community-nt


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;


--
-- Create schema labbench
--

CREATE DATABASE IF NOT EXISTS labbench;
USE labbench;

--
-- Definition of table `assembly`
--

DROP TABLE IF EXISTS `assembly`;
CREATE TABLE `assembly` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `extraction_id` varchar(45) NOT NULL,
  `workflow` int(10) unsigned NOT NULL,
  `progress` varchar(45) NOT NULL,
  `consensus` longtext,
  `params` longtext,
  `coverage` float,
  `disagreements` int(10) unsigned,
  `edits` longtext,
  `reference_seq_id` int(10) unsigned,
  `confidence_scores` longtext,
  `trim_params_fwd` longtext,
  `trim_params_rev` longtext,
  `other_processing_fwd` longtext,
  `other_processing_rev` longtext,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `notes` longtext,
  PRIMARY KEY  (`id`),
  KEY `FK_assembly_1` (`workflow`),
  CONSTRAINT `FK_assembly_1` FOREIGN KEY (`workflow`) REFERENCES `workflow` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dumping data for table `assembly`
--

/*!40000 ALTER TABLE `assembly` DISABLE KEYS */;
/*!40000 ALTER TABLE `assembly` ENABLE KEYS */;


--
-- Definition of table `cycle`
--

DROP TABLE IF EXISTS `cycle`;
CREATE TABLE `cycle` (
  `id` int(11) NOT NULL auto_increment,
  `thermocycleId` int(11) default NULL,
  `repeats` int(11) default NULL,
  PRIMARY KEY  (`id`),
  KEY `thermocycleId` (`thermocycleId`),
  CONSTRAINT `thermocycleId` FOREIGN KEY (`thermocycleId`) REFERENCES `thermocycle` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;

--
-- Dumping data for table `cycle`
--

/*!40000 ALTER TABLE `cycle` DISABLE KEYS */;
INSERT INTO `cycle` (`id`,`thermocycleId`,`repeats`) VALUES
 (7,15,1),
 (8,15,25),
 (9,15,1);
/*!40000 ALTER TABLE `cycle` ENABLE KEYS */;


--
-- Definition of table `cyclesequencing`
--

DROP TABLE IF EXISTS `cyclesequencing`;
CREATE TABLE `cyclesequencing` (
  `id` int(11) NOT NULL auto_increment,
  `primerName` varchar(64) NOT NULL,
  `primerSequence` varchar(999) NOT NULL,
  `notes` longtext NOT NULL,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `workflow` int(10) unsigned default NULL,
  `thermocycle` int(11) NOT NULL,
  `plate` int(10) unsigned NOT NULL,
  `location` int(11) NOT NULL,
  `extractionId` varchar(45) NOT NULL,
  `technician` varchar(90) NOT NULL,
  `cocktail` int(10) unsigned default NULL,
  `progress` varchar(45) NOT NULL,
  `cleanupPerformed` tinyint(1) NOT NULL default '0',
  `cleanupMethod` varchar(99) NOT NULL,
  `direction` varchar(32) NOT NULL,
  PRIMARY KEY  (`id`),
  KEY `cycle_thermocycle` (`thermocycle`),
  KEY `FK_cyclesequencing_plate` (`plate`),
  KEY `FK_cyclesequencing_workflow` (`workflow`),
  KEY `FK_cyclesequencing_cocktail` (`cocktail`),
  CONSTRAINT `FK_cyclesequencing_cocktail` FOREIGN KEY (`cocktail`) REFERENCES `cyclesequencing_cocktail` (`id`),
  CONSTRAINT `FK_cyclesequencing_plate` FOREIGN KEY (`plate`) REFERENCES `plate` (`id`),
  CONSTRAINT `FK_cyclesequencing_workflow` FOREIGN KEY (`workflow`) REFERENCES `workflow` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;

--
-- Dumping data for table `cyclesequencing`
--

/*!40000 ALTER TABLE `cyclesequencing` DISABLE KEYS */;
/*!40000 ALTER TABLE `cyclesequencing` ENABLE KEYS */;


--
-- Definition of table `cyclesequencing_cocktail`
--

DROP TABLE IF EXISTS `cyclesequencing_cocktail`;
CREATE TABLE `cyclesequencing_cocktail` (
  `id` int(10) unsigned NOT NULL auto_increment,
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
  `templateAmount` double NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `cyclesequencing_cocktail`
--

/*!40000 ALTER TABLE `cyclesequencing_cocktail` DISABLE KEYS */;
INSERT INTO `cyclesequencing_cocktail` (`id`,`name`,`ddh2o`,`buffer`,`bigDye`,`notes`,`bufferConc`,`bigDyeConc`,`templateConc`,`primerConc`,`primerAmount`,`extraItem`,`extraItemAmount`) VALUES
 (0,'No Cocktail',0,0,0,' ',0,0,0,0,0,' ',0);
/*!40000 ALTER TABLE `cyclesequencing_cocktail` ENABLE KEYS */;


--
-- Definition of table `cyclesequencing_thermocycle`
--

DROP TABLE IF EXISTS `cyclesequencing_thermocycle`;
CREATE TABLE `cyclesequencing_thermocycle` (
  `id` int(11) NOT NULL auto_increment,
  `cycle` int(11) NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `cyclesequencing_thermocycle`
--

/*!40000 ALTER TABLE `cyclesequencing_thermocycle` DISABLE KEYS */;
INSERT INTO `cyclesequencing_thermocycle` (`id`,`cycle`) VALUES
 (1,15);
/*!40000 ALTER TABLE `cyclesequencing_thermocycle` ENABLE KEYS */;


--
-- Definition of table `databaseversion`
--

DROP TABLE IF EXISTS `databaseversion`;
CREATE TABLE `databaseversion` (
  `version` int(10) unsigned NOT NULL auto_increment,
  PRIMARY KEY  (`version`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `databaseversion`
--

/*!40000 ALTER TABLE `databaseversion` DISABLE KEYS */;
INSERT INTO `databaseversion` (`version`) VALUES
 (6);
/*!40000 ALTER TABLE `databaseversion` ENABLE KEYS */;


--
-- Definition of table `extraction`
--

DROP TABLE IF EXISTS `extraction`;
CREATE TABLE `extraction` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP,
  `method` varchar(45) NOT NULL,
  `volume` double NOT NULL,
  `dilution` double default NULL,
  `concentrationStored` tinyint(1) NOT NULL default '0',
  `concentration` double NOT NULL default '0',
  `parent` varchar(45) NOT NULL,
  `technician` varchar(90) NOT NULL,
  `sampleId` varchar(45) NOT NULL,
  `extractionId` varchar(45) NOT NULL,
  `plate` int(10) unsigned NOT NULL,
  `location` int(10) unsigned NOT NULL,
  `notes` longtext NOT NULL,
  `extractionBarcode` varchar(45) NOT NULL,
  `previousPlate` varchar(45) NOT NULL,
  `previousWell` varchar(45) NOT NULL,
  PRIMARY KEY  (`id`),
  UNIQUE KEY `ind_extraction_3` (`extractionId`),
  KEY `FK_extraction_plate` (`plate`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Dumping data for table `extraction`
--

/*!40000 ALTER TABLE `extraction` DISABLE KEYS */;
/*!40000 ALTER TABLE `extraction` ENABLE KEYS */;


--
-- Definition of table `gelimages`
--

DROP TABLE IF EXISTS `gelimages`;
CREATE TABLE `gelimages` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `name` VARCHAR(45) DEFAULT 'Image' NOT NULL,
  `plate` int(11) NOT NULL default '0',
  `imageData` longblob,
  `notes` longtext NOT NULL,
  PRIMARY KEY  (`id`),
  KEY `pcrImage` (`plate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `gelimages`
--

/*!40000 ALTER TABLE `gelimages` DISABLE KEYS */;
/*!40000 ALTER TABLE `gelimages` ENABLE KEYS */;


--
-- Definition of table `pcr`
--

DROP TABLE IF EXISTS `pcr`;
CREATE TABLE `pcr` (
  `id` int(11) NOT NULL auto_increment,
  `prName` varchar(64) default NULL,
  `prSequence` varchar(999) default NULL,
  `date` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `workflow` int(10) unsigned default NULL,
  `plate` int(10) unsigned NOT NULL,
  `location` int(11) NOT NULL,
  `cocktail` int(10) unsigned NOT NULL,
  `progress` varchar(45) NOT NULL,
  `extractionId` varchar(45) NOT NULL,
  `technician` varchar(90) NOT NULL,
  `thermocycle` int(11) NOT NULL default '-1',
  `cleanupPerformed` tinyint(1) NOT NULL default '0',
  `cleanupMethod` varchar(45) NOT NULL,
  `notes` longtext NOT NULL,
  `revPrName` varchar(64) NOT NULL,
  `revPrSequence` varchar(999) NOT NULL,
  PRIMARY KEY  (`id`),
  KEY `ind_pcr_plate` (`plate`),
  KEY `FK_pcr_workflow` (`workflow`),
  KEY `FK_pcr_cocktail` (`cocktail`),
  CONSTRAINT `FK_pcr_cocktail` FOREIGN KEY (`cocktail`) REFERENCES `pcr_cocktail` (`id`),
  CONSTRAINT `FK_pcr_plate` FOREIGN KEY (`plate`) REFERENCES `plate` (`id`),
  CONSTRAINT `FK_pcr_workflow` FOREIGN KEY (`workflow`) REFERENCES `workflow` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;

--
-- Dumping data for table `pcr`
--

/*!40000 ALTER TABLE `pcr` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcr` ENABLE KEYS */;


--
-- Definition of table `pcr_cocktail`
--

DROP TABLE IF EXISTS `pcr_cocktail`;
CREATE TABLE `pcr_cocktail` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `name` varchar(99) NOT NULL,
  `ddH20` double NOT NULL,
  `buffer` double NOT NULL,
  `mg` double NOT NULL,
  `bsa` double NOT NULL,
  `dNTP` double NOT NULL,
  `taq` double NOT NULL,
  `notes` longtext NOT NULL,
  `bufferConc` double NOT NULL,
  `mgConc` double NOT NULL,
  `dNTPConc` double NOT NULL,
  `taqConc` double NOT NULL,
  `templateConc` double NOT NULL,
  `bsaConc` double NOT NULL,
  `fwPrAmount` double NOT NULL,
  `fwPrConc` double NOT NULL,
  `revPrAmount` double NOT NULL,
  `revPrConc` double NOT NULL,
  `extraItem` mediumtext NOT NULL,
  `extraItemAmount` double NOT NULL,
  `templateAmount` double NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `pcr_cocktail`
--

/*!40000 ALTER TABLE `pcr_cocktail` DISABLE KEYS */;
INSERT INTO `pcr_cocktail` (`id`,`name`,`ddH20`,`buffer`,`mg`,`bsa`,`dNTP`,`taq`,`notes`,`bufferConc`,`mgConc`,`dNTPConc`,`taqConc`,`templateConc`,`bsaConc`,`fwPrAmount`,`fwPrConc`,`revPrAmount`,`revPrConc`,`extraItem`,`extraItemAmount`) VALUES
 (0,'No Cocktail',0,0,0,0,0,0,' ',0,0,0,0,0,0,0,0,0,0,' ',0);
/*!40000 ALTER TABLE `pcr_cocktail` ENABLE KEYS */;


--
-- Definition of table `pcr_thermocycle`
--

DROP TABLE IF EXISTS `pcr_thermocycle`;
CREATE TABLE `pcr_thermocycle` (
  `id` int(11) NOT NULL auto_increment,
  `cycle` int(11) default NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `pcr_thermocycle`
--

/*!40000 ALTER TABLE `pcr_thermocycle` DISABLE KEYS */;
INSERT INTO `pcr_thermocycle` (`id`,`cycle`) VALUES
 (2,15);
/*!40000 ALTER TABLE `pcr_thermocycle` ENABLE KEYS */;


--
-- Definition of table `plate`
--

DROP TABLE IF EXISTS `plate`;
CREATE TABLE `plate` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `name` varchar(64) NOT NULL default 'plate',
  `date` date NOT NULL,
  `size` int(11) NOT NULL,
  `type` varchar(45) NOT NULL,
  `thermocycle` int(11) NOT NULL default '-1',
  PRIMARY KEY  USING BTREE (`id`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `plate`
--

/*!40000 ALTER TABLE `plate` DISABLE KEYS */;
/*!40000 ALTER TABLE `plate` ENABLE KEYS */;


--
-- Definition of table `state`
--

DROP TABLE IF EXISTS `state`;
CREATE TABLE `state` (
  `id` int(11) NOT NULL auto_increment,
  `temp` int(11) unsigned NOT NULL,
  `length` int(11) unsigned NOT NULL,
  `cycleId` int(11) default NULL,
  PRIMARY KEY  (`id`),
  KEY `cycleId` (`cycleId`),
  CONSTRAINT `cycleId` FOREIGN KEY (`cycleId`) REFERENCES `cycle` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;

--
-- Dumping data for table `state`
--

/*!40000 ALTER TABLE `state` DISABLE KEYS */;
INSERT INTO `state` (`id`,`temp`,`length`,`cycleId`) VALUES
 (15,20,60,7),
 (16,90,30,8),
 (17,55,120,8),
 (18,75,30,8),
 (19,20,540,9);
/*!40000 ALTER TABLE `state` ENABLE KEYS */;


--
-- Definition of table `thermocycle`
--

DROP TABLE IF EXISTS `thermocycle`;
CREATE TABLE `thermocycle` (
  `id` int(11) NOT NULL auto_increment,
  `name` varchar(64) default NULL,
  `notes` longtext NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `thermocycle`
--

/*!40000 ALTER TABLE `thermocycle` DISABLE KEYS */;
INSERT INTO `thermocycle` (`id`,`name`,`notes`) VALUES
 (15,'Example Thermocycle','An example cycle...');
/*!40000 ALTER TABLE `thermocycle` ENABLE KEYS */;


--
-- Definition of table `workflow`
--

DROP TABLE IF EXISTS `workflow`;
CREATE TABLE `workflow` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `name` varchar(45) NOT NULL default 'workflow',
  `date` date NOT NULL,
  `extractionId` int(10) unsigned NOT NULL,
  `locus` varchar(45) NOT NULL default 'COI',
  PRIMARY KEY  USING BTREE (`id`),
  UNIQUE KEY `ind_workflow_3` (`name`),
  KEY `FK_workflow_extraction` (`extractionId`),
  CONSTRAINT `FK_workflow_extraction` FOREIGN KEY (`extractionId`) REFERENCES `extraction` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `workflow`
--

/*!40000 ALTER TABLE `workflow` DISABLE KEYS */;
/*!40000 ALTER TABLE `workflow` ENABLE KEYS */;

DROP TABLE IF EXISTS `traces`;
CREATE TABLE `traces` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `reaction` int(11) NOT NULL,
  `name` varchar(96) NOT NULL,
  `data` longblob NOT NULL,
  PRIMARY KEY  (`id`),
  KEY `FK_traces_1` (`reaction`),
  CONSTRAINT `FK_traces_1` FOREIGN KEY (`reaction`) REFERENCES `cyclesequencing` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Dumping data for table `traces`
--

/*!40000 ALTER TABLE `traces` DISABLE KEYS */;
/*!40000 ALTER TABLE `traces` ENABLE KEYS */;




/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
