
package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Content {

    @SerializedName("family")
    @Expose
    private String family;
    @SerializedName("dayIdentified")
    @Expose
    private String dayIdentified;
    @SerializedName("countryOrOcean")
    @Expose
    private String countryOrOcean;
    @SerializedName("verbatimDepth")
    @Expose
    private String verbatimDepth;
    @SerializedName("associatedMedia")
    @Expose
    private String associatedMedia;
    @SerializedName("collectionMethod")
    @Expose
    private String collectionMethod;
    @SerializedName("kingdom")
    @Expose
    private String kingdom;
    @SerializedName("phylum")
    @Expose
    private String phylum;
    @SerializedName("waterBody")
    @Expose
    private String waterBody;
    @SerializedName("chainOfCustody")
    @Expose
    private String chainOfCustody;
    @SerializedName("tissueRackBarcode")
    @Expose
    private String tissueRackBarcode;
    @SerializedName("taxonRemarks")
    @Expose
    private String taxonRemarks;
    @SerializedName("subfamily")
    @Expose
    private String subfamily;
    @SerializedName("catalogNumber")
    @Expose
    private String catalogNumber;
    @SerializedName("island")
    @Expose
    private String island;
    @SerializedName("tissueBarcode")
    @Expose
    private String tissueBarcode;
    @SerializedName("yearCollected,monthCollected,dayCollected")
    @Expose
    private String yearCollectedMonthCollectedDayCollected;
    @SerializedName("tissueRack")
    @Expose
    private String tissueRack;
    @SerializedName("dayCollected")
    @Expose
    private String dayCollected;
    @SerializedName("extractionPlateID")
    @Expose
    private String extractionPlateID;
    @SerializedName("voucherID")
    @Expose
    private String voucherID;
    @SerializedName("georeferenceProtocol")
    @Expose
    private String georeferenceProtocol;
    @SerializedName("extractionWell")
    @Expose
    private String extractionWell;
    @SerializedName("monthCollected")
    @Expose
    private String monthCollected;
    @SerializedName("processingLab")
    @Expose
    private String processingLab;
    @SerializedName("tissuePosition")
    @Expose
    private String tissuePosition;
    @SerializedName("locality")
    @Expose
    private String locality;
    @SerializedName("municipality")
    @Expose
    private String municipality;
    @SerializedName("monthIdentified")
    @Expose
    private String monthIdentified;
    @SerializedName("sex")
    @Expose
    private String sex;
    @SerializedName("eventID")
    @Expose
    private String eventID;
    @SerializedName("basisOfID")
    @Expose
    private String basisOfID;
    @SerializedName("subspecies")
    @Expose
    private String subspecies;
    @SerializedName("decimalLongitude")
    @Expose
    private String decimalLongitude;
    @SerializedName("order")
    @Expose
    private String order;
    @SerializedName("county")
    @Expose
    private String county;
    @SerializedName("identificationConfidence")
    @Expose
    private String identificationConfidence;
    @SerializedName("extractionPlateBarcode")
    @Expose
    private String extractionPlateBarcode;
    @SerializedName("commonName")
    @Expose
    private String commonName;
    @SerializedName("tissueType")
    @Expose
    private String tissueType;
    @SerializedName("identifiedBy")
    @Expose
    private String identifiedBy;
    @SerializedName("institutionCode")
    @Expose
    private String institutionCode;
    @SerializedName("habitat")
    @Expose
    private String habitat;
    @SerializedName("associatedTaxa")
    @Expose
    private String associatedTaxa;
    @SerializedName("timeCollected")
    @Expose
    private String timeCollected;
    @SerializedName("fieldNumber")
    @Expose
    private String fieldNumber;
    @SerializedName("species")
    @Expose
    private String species;
    @SerializedName("yearIdentified")
    @Expose
    private String yearIdentified;
    @SerializedName("collectionCode")
    @Expose
    private String collectionCode;
    @SerializedName("collectedBy")
    @Expose
    private String collectedBy;
    @SerializedName("decimalLatitude")
    @Expose
    private String decimalLatitude;
    @SerializedName("lifeStage")
    @Expose
    private String lifeStage;
    @SerializedName("verbatimElevation")
    @Expose
    private String verbatimElevation;
    @SerializedName("sampleRemarks")
    @Expose
    private String sampleRemarks;
    @SerializedName("genus")
    @Expose
    private String genus;
    @SerializedName("eventRemarks")
    @Expose
    private String eventRemarks;
    @SerializedName("sequencingLab")
    @Expose
    private String sequencingLab;
    @SerializedName("coordinatePrecision")
    @Expose
    private String coordinatePrecision;
    @SerializedName("typeStatus")
    @Expose
    private String typeStatus;
    @SerializedName("permitInformation")
    @Expose
    private String permitInformation;
    @SerializedName("decimalLatitude,decimalLongitude")
    @Expose
    private String decimalLatitudeDecimalLongitude;
    @SerializedName("yearCollected")
    @Expose
    private String yearCollected;
    @SerializedName("scientificName")
    @Expose
    private String scientificName;
    @SerializedName("establishmentMeans")
    @Expose
    private String establishmentMeans;
    @SerializedName("preservative")
    @Expose
    private String preservative;
    @SerializedName("locationRemarks")
    @Expose
    private String locationRemarks;
    @SerializedName("extractionBarcode")
    @Expose
    private String extractionBarcode;
    @SerializedName("stateProvince")
    @Expose
    private String stateProvince;
    @SerializedName("bcid")
    @Expose
    private String bcid;

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getDayIdentified() {
        return dayIdentified;
    }

    public void setDayIdentified(String dayIdentified) {
        this.dayIdentified = dayIdentified;
    }

    public String getCountryOrOcean() {
        return countryOrOcean;
    }

    public void setCountryOrOcean(String countryOrOcean) {
        this.countryOrOcean = countryOrOcean;
    }

    public String getVerbatimDepth() {
        return verbatimDepth;
    }

    public void setVerbatimDepth(String verbatimDepth) {
        this.verbatimDepth = verbatimDepth;
    }

    public String getAssociatedMedia() {
        return associatedMedia;
    }

    public void setAssociatedMedia(String associatedMedia) {
        this.associatedMedia = associatedMedia;
    }

    public String getCollectionMethod() {
        return collectionMethod;
    }

    public void setCollectionMethod(String collectionMethod) {
        this.collectionMethod = collectionMethod;
    }

    public String getKingdom() {
        return kingdom;
    }

    public void setKingdom(String kingdom) {
        this.kingdom = kingdom;
    }

    public String getPhylum() {
        return phylum;
    }

    public void setPhylum(String phylum) {
        this.phylum = phylum;
    }

    public String getWaterBody() {
        return waterBody;
    }

    public void setWaterBody(String waterBody) {
        this.waterBody = waterBody;
    }

    public String getChainOfCustody() {
        return chainOfCustody;
    }

    public void setChainOfCustody(String chainOfCustody) {
        this.chainOfCustody = chainOfCustody;
    }

    public String getTissueRackBarcode() {
        return tissueRackBarcode;
    }

    public void setTissueRackBarcode(String tissueRackBarcode) {
        this.tissueRackBarcode = tissueRackBarcode;
    }

    public String getTaxonRemarks() {
        return taxonRemarks;
    }

    public void setTaxonRemarks(String taxonRemarks) {
        this.taxonRemarks = taxonRemarks;
    }

    public String getSubfamily() {
        return subfamily;
    }

    public void setSubfamily(String subfamily) {
        this.subfamily = subfamily;
    }

    public String getCatalogNumber() {
        return catalogNumber;
    }

    public void setCatalogNumber(String catalogNumber) {
        this.catalogNumber = catalogNumber;
    }

    public String getIsland() {
        return island;
    }

    public void setIsland(String island) {
        this.island = island;
    }

    public String getTissueBarcode() {
        return tissueBarcode;
    }

    public void setTissueBarcode(String tissueBarcode) {
        this.tissueBarcode = tissueBarcode;
    }

    public String getYearCollectedMonthCollectedDayCollected() {
        return yearCollectedMonthCollectedDayCollected;
    }

    public void setYearCollectedMonthCollectedDayCollected(String yearCollectedMonthCollectedDayCollected) {
        this.yearCollectedMonthCollectedDayCollected = yearCollectedMonthCollectedDayCollected;
    }

    public String getTissueRack() {
        return tissueRack;
    }

    public void setTissueRack(String tissueRack) {
        this.tissueRack = tissueRack;
    }

    public String getDayCollected() {
        return dayCollected;
    }

    public void setDayCollected(String dayCollected) {
        this.dayCollected = dayCollected;
    }

    public String getExtractionPlateID() {
        return extractionPlateID;
    }

    public void setExtractionPlateID(String extractionPlateID) {
        this.extractionPlateID = extractionPlateID;
    }

    public String getVoucherID() {
        return voucherID;
    }

    public void setVoucherID(String voucherID) {
        this.voucherID = voucherID;
    }

    public String getGeoreferenceProtocol() {
        return georeferenceProtocol;
    }

    public void setGeoreferenceProtocol(String georeferenceProtocol) {
        this.georeferenceProtocol = georeferenceProtocol;
    }

    public String getExtractionWell() {
        return extractionWell;
    }

    public void setExtractionWell(String extractionWell) {
        this.extractionWell = extractionWell;
    }

    public String getMonthCollected() {
        return monthCollected;
    }

    public void setMonthCollected(String monthCollected) {
        this.monthCollected = monthCollected;
    }

    public String getProcessingLab() {
        return processingLab;
    }

    public void setProcessingLab(String processingLab) {
        this.processingLab = processingLab;
    }

    public String getTissuePosition() {
        return tissuePosition;
    }

    public void setTissuePosition(String tissuePosition) {
        this.tissuePosition = tissuePosition;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getMunicipality() {
        return municipality;
    }

    public void setMunicipality(String municipality) {
        this.municipality = municipality;
    }

    public String getMonthIdentified() {
        return monthIdentified;
    }

    public void setMonthIdentified(String monthIdentified) {
        this.monthIdentified = monthIdentified;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getEventID() {
        return eventID;
    }

    public void setEventID(String eventID) {
        this.eventID = eventID;
    }

    public String getBasisOfID() {
        return basisOfID;
    }

    public void setBasisOfID(String basisOfID) {
        this.basisOfID = basisOfID;
    }

    public String getSubspecies() {
        return subspecies;
    }

    public void setSubspecies(String subspecies) {
        this.subspecies = subspecies;
    }

    public String getDecimalLongitude() {
        return decimalLongitude;
    }

    public void setDecimalLongitude(String decimalLongitude) {
        this.decimalLongitude = decimalLongitude;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public String getIdentificationConfidence() {
        return identificationConfidence;
    }

    public void setIdentificationConfidence(String identificationConfidence) {
        this.identificationConfidence = identificationConfidence;
    }

    public String getExtractionPlateBarcode() {
        return extractionPlateBarcode;
    }

    public void setExtractionPlateBarcode(String extractionPlateBarcode) {
        this.extractionPlateBarcode = extractionPlateBarcode;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getTissueType() {
        return tissueType;
    }

    public void setTissueType(String tissueType) {
        this.tissueType = tissueType;
    }

    public String getIdentifiedBy() {
        return identifiedBy;
    }

    public void setIdentifiedBy(String identifiedBy) {
        this.identifiedBy = identifiedBy;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    public String getHabitat() {
        return habitat;
    }

    public void setHabitat(String habitat) {
        this.habitat = habitat;
    }

    public String getAssociatedTaxa() {
        return associatedTaxa;
    }

    public void setAssociatedTaxa(String associatedTaxa) {
        this.associatedTaxa = associatedTaxa;
    }

    public String getTimeCollected() {
        return timeCollected;
    }

    public void setTimeCollected(String timeCollected) {
        this.timeCollected = timeCollected;
    }

    public String getFieldNumber() {
        return fieldNumber;
    }

    public void setFieldNumber(String fieldNumber) {
        this.fieldNumber = fieldNumber;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getYearIdentified() {
        return yearIdentified;
    }

    public void setYearIdentified(String yearIdentified) {
        this.yearIdentified = yearIdentified;
    }

    public String getCollectionCode() {
        return collectionCode;
    }

    public void setCollectionCode(String collectionCode) {
        this.collectionCode = collectionCode;
    }

    public String getCollectedBy() {
        return collectedBy;
    }

    public void setCollectedBy(String collectedBy) {
        this.collectedBy = collectedBy;
    }

    public String getDecimalLatitude() {
        return decimalLatitude;
    }

    public void setDecimalLatitude(String decimalLatitude) {
        this.decimalLatitude = decimalLatitude;
    }

    public String getLifeStage() {
        return lifeStage;
    }

    public void setLifeStage(String lifeStage) {
        this.lifeStage = lifeStage;
    }

    public String getVerbatimElevation() {
        return verbatimElevation;
    }

    public void setVerbatimElevation(String verbatimElevation) {
        this.verbatimElevation = verbatimElevation;
    }

    public String getSampleRemarks() {
        return sampleRemarks;
    }

    public void setSampleRemarks(String sampleRemarks) {
        this.sampleRemarks = sampleRemarks;
    }

    public String getGenus() {
        return genus;
    }

    public void setGenus(String genus) {
        this.genus = genus;
    }

    public String getEventRemarks() {
        return eventRemarks;
    }

    public void setEventRemarks(String eventRemarks) {
        this.eventRemarks = eventRemarks;
    }

    public String getSequencingLab() {
        return sequencingLab;
    }

    public void setSequencingLab(String sequencingLab) {
        this.sequencingLab = sequencingLab;
    }

    public String getCoordinatePrecision() {
        return coordinatePrecision;
    }

    public void setCoordinatePrecision(String coordinatePrecision) {
        this.coordinatePrecision = coordinatePrecision;
    }

    public String getTypeStatus() {
        return typeStatus;
    }

    public void setTypeStatus(String typeStatus) {
        this.typeStatus = typeStatus;
    }

    public String getPermitInformation() {
        return permitInformation;
    }

    public void setPermitInformation(String permitInformation) {
        this.permitInformation = permitInformation;
    }

    public String getDecimalLatitudeDecimalLongitude() {
        return decimalLatitudeDecimalLongitude;
    }

    public void setDecimalLatitudeDecimalLongitude(String decimalLatitudeDecimalLongitude) {
        this.decimalLatitudeDecimalLongitude = decimalLatitudeDecimalLongitude;
    }

    public String getYearCollected() {
        return yearCollected;
    }

    public void setYearCollected(String yearCollected) {
        this.yearCollected = yearCollected;
    }

    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(String scientificName) {
        this.scientificName = scientificName;
    }

    public String getEstablishmentMeans() {
        return establishmentMeans;
    }

    public void setEstablishmentMeans(String establishmentMeans) {
        this.establishmentMeans = establishmentMeans;
    }

    public String getPreservative() {
        return preservative;
    }

    public void setPreservative(String preservative) {
        this.preservative = preservative;
    }

    public String getLocationRemarks() {
        return locationRemarks;
    }

    public void setLocationRemarks(String locationRemarks) {
        this.locationRemarks = locationRemarks;
    }

    public String getExtractionBarcode() {
        return extractionBarcode;
    }

    public void setExtractionBarcode(String extractionBarcode) {
        this.extractionBarcode = extractionBarcode;
    }

    public String getStateProvince() {
        return stateProvince;
    }

    public void setStateProvince(String stateProvince) {
        this.stateProvince = stateProvince;
    }

    public String getBcid() {
        return bcid;
    }

    public void setBcid(String bcid) {
        this.bcid = bcid;
    }

}
