package com.biomatters.plugins.biocode.submission.bold;

/**
 * @author Matthew Cheung
 *         Created on 27/08/14 11:55 AM
 */
class TraceInfo {
    String filename;
    String forwardPcrPrimer;
    String reversePcrPrimer;
    String sequencingPrimer;
    boolean forwardNotReverse;
    String processId;
    String locus;

    TraceInfo(String filename, String forwardPcrPrimer, String reversePcrPrimer, String sequencingPrimer, boolean forwardNotReverse, String processId, String locus) {
        this.filename = filename;
        this.forwardPcrPrimer = forwardPcrPrimer;
        this.reversePcrPrimer = reversePcrPrimer;
        this.sequencingPrimer = sequencingPrimer;
        this.forwardNotReverse = forwardNotReverse;
        this.processId = processId;
        this.locus = locus;
    }
}
