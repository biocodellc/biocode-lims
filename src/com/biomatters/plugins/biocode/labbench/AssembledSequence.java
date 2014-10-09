package com.biomatters.plugins.biocode.labbench;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 7/04/14 10:20 AM
 */
@XmlRootElement
public class AssembledSequence {
    public Integer id;
    public Long date;
    public String progress;
    public Integer limsId;

    public String extractionId;
    public Integer workflowId;
    public String workflowName;
    public String workflowLocus;
    public String sampleId;
    public String extractionBarcode;

    public String forwardPlate;
    public String reversePlate;

    public String consensus;
    public String confidenceScore;
    public Double coverage;
    public Integer numberOfDisagreements;
    public Integer numOfEdits;
    public Integer numberOfAmbiguities;
    public String forwardTrimParameters;
    public String reverseTrimParameters;
    public String technician;
    public String bin;
    public String assemblyNotes;
    public String assemblyParameters;
    public Boolean submitted;
    public String editRecord;

    public String forwardPrimerName;
    public String forwardPrimerSequence;

    public String reversePrimerName;
    public String reversePrimerSequence;

    public AssembledSequence() {
    }
}
