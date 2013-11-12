package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;

/**
* @author Matthew Cheung
* @version $Id$
*          <p/>
*          Created on 12/11/13 11:32 AM
*/
public enum InputType {
    CONTIGS("Consensus sequence", "Consensus sequence with source chromatograms"),
    ALIGNMENT_OF_CONSENSUS("Consensus sequences", "Consensus sequences with source traces"),
    CONSENSUS_SEQS("Consensus sequence", "Consensus sequence with source traces"),
    TRACES("Trace as final sequence", "Trace as both final sequence and source trace"),
    MIXED(null, null);

    private String uploadDescription;
    private String withTracesDescription;

    private InputType(String uploadDescription, String withTracesDescription) {
        this.uploadDescription = uploadDescription;
        this.withTracesDescription = withTracesDescription;
    }

    public static InputType determineInputType(AnnotatedPluginDocument[] documents) throws DocumentOperationException {
        boolean contigSelected = false;
        boolean alignmentOfConsensus = false;
        boolean tracesSelected = false;
        boolean nonTraceSequenceSelected = false;
        for (AnnotatedPluginDocument doc : documents) {
            Class<? extends PluginDocument> docClass = doc.getDocumentClass();
            if (SequenceAlignmentDocument.class.isAssignableFrom(docClass)) {
                if(((SequenceAlignmentDocument)doc.getDocument()).isContig()) {
                    contigSelected = true;
                } else {
                    alignmentOfConsensus = true;
                }
            } else if(NucleotideGraph.class.isAssignableFrom(docClass)){
                NucleotideGraph graph = (NucleotideGraph) doc.getDocument();
                if(graph.getChromatogramLength() > 0) {
                    tracesSelected = true;
                } else {
                    nonTraceSequenceSelected = true;
                }
            } else {
                nonTraceSequenceSelected = true;
            }
        }
        if(contigSelected && alignmentOfConsensus) {
            return MIXED;
        } else if(contigSelected) {
            return CONTIGS;
        } else if(alignmentOfConsensus) {
            return ALIGNMENT_OF_CONSENSUS;
        }

        // If we get here then we must have standalone sequences because the selection signature only allows one type
        if(nonTraceSequenceSelected && tracesSelected) {
            return MIXED;
        } else if(tracesSelected) {
            return TRACES;
        } else {
            return CONSENSUS_SEQS;
        }
    }

    public String getUploadDescription() {
        return uploadDescription;
    }

    public String getWithTracesDescription() {
        return withTracesDescription;
    }
}
