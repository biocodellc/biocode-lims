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
    public int id;
    public Long date;
    public String progress;
    public int limsId;

    public String extractionId;
    public int workflowId;
    public String workflowLocus;
    public String sampleId;
    public String extractionBarcode;

    public String consensus;
    public String confidenceScore;
    public Double coverage;
    public int numberOfDisagreements;
    public int numOfEdits;
    public int numberOfAmbiguities;
    public String forwardTrimParameters;
    public String reverseTrimParameters;
    public String technician;
    public String bin;
    public String assemblyNotes;
    public String assemblyParameters;
    public boolean submitted;
    public String editRecord;

    public AssembledSequence() {
    }
}
