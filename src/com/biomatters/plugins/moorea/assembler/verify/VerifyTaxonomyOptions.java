package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.SequenceDatabaseSuperService;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaUtilities;
import jebl.util.ProgressListener;
import org.virion.jam.util.SimpleListener;

import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyOptions extends Options {

    private final ComboBoxOption<DatabaseOptionValue> databaseOption;
    private final ComboBoxOption<ProgramOptionValue> programOption;
    private Options currentProgramOptions = null;

    public VerifyTaxonomyOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {

        GeneiousService blastSuperService = PluginUtilities.getGeneiousService("NCBI_BLAST");
        if (!(blastSuperService instanceof SequenceDatabaseSuperService)) {
            throw new DocumentOperationException("Could not find the NCBI BLAST service. Please make sure the NCBI plugin is installed and enabled.");
        }
        List<DatabaseOptionValue> databaseOptionValues = new ArrayList<DatabaseOptionValue>();
        List<ProgramOptionValue> programOptionValues = new ArrayList<ProgramOptionValue>();
        DatabaseOptionValue defaultDatabase = null;
        Map<String, Options> childOptionsToAdd = new HashMap<String, Options>();
        for (GeneiousService blastService : blastSuperService.getChildServices()) {
            if (!(blastService instanceof DatabaseService) || ((DatabaseService)blastService).getSequenceSearchPrograms(DatabaseService.SequenceSearchQueryType.NUCLEOTIDE).isEmpty()) {
                continue;
            }
            DatabaseService databaseService = (DatabaseService) blastService;
            DatabaseOptionValue databaseOptionValue = new DatabaseOptionValue(databaseService);
            Map<String, String> programs = databaseService.getSequenceSearchPrograms(DatabaseService.SequenceSearchQueryType.NUCLEOTIDE);
            boolean validProgramExists = false;
            for (Map.Entry<String, String> programEntry : programs.entrySet()) {
                String programCode = programEntry.getKey();
                if (programCode.contains("blastx")) continue;
                validProgramExists = true;
                ProgramOptionValue programOptionValue = new ProgramOptionValue(programCode, programEntry.getValue(), databaseService.getSequenceSearchOptions(programCode));
                if (!programOptionValues.contains(programOptionValue)) {
                    programOptionValues.add(programOptionValue);
                    if (programOptionValue.options != null) {
                        childOptionsToAdd.put(programCode, programOptionValue.options);
                    }
                }
            }
            if (!validProgramExists) continue;
            if (databaseService.getName().equals("nr")) defaultDatabase = databaseOptionValue;
            databaseOptionValues.add(databaseOptionValue);
        }
        databaseOption = addComboBoxOption("database", "Database:", databaseOptionValues, defaultDatabase);
        programOption = addComboBoxOption("program", "Program:", programOptionValues, programOptionValues.get(0));
        for (Map.Entry<String, Options> childOptionsEntry : childOptionsToAdd.entrySet()) {
            Options options = childOptionsEntry.getValue();
            options.setVisible(false);
            //todo don't set this, should set default or something
            options.setValue("maxHits", 5);
            options.setValue("getHitAnnos", true);
            addChildOptions(childOptionsEntry.getKey(), "", null, options);
        }
        SimpleListener programListener = new SimpleListener() {
            public void objectChanged() {
                Options newOptions = programOption.getValue().options;
                if (currentProgramOptions == newOptions) {
                    return;
                }
                if (currentProgramOptions != null) {
                    currentProgramOptions.setVisible(false);
                }
                if (newOptions != null) {
                    newOptions.setVisible(true);
                }
                currentProgramOptions = newOptions;
            }
        };
        programOption.addChangeListener(programListener);
        programListener.objectChanged();

        boolean isAlignments = SequenceAlignmentDocument.class.isAssignableFrom(documents[0].getDocumentClass());
        if (isAlignments) {
            //todo check sequence type
            Options consensusOptions = MooreaUtilities.getConsensusOptions(documents);
            if (consensusOptions == null) {
                throw new DocumentOperationException("The consensus plugin must be installed to be able to verify");
            }
            addChildOptions("consensus", "Consensus", null, consensusOptions);
        }
    }

    public List<AnnotatedPluginDocument> getQueries(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        List<AnnotatedPluginDocument> queries;
        if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocuments[0].getDocumentClass())) {
            Options consensusOptions = getChildOptions().get("consensus");
            DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
            queries = consensusOperation.performOperation(annotatedDocuments, ProgressListener.EMPTY, consensusOptions);
            for (int i = 0; i < queries.size(); i++) {
                //we don't want " consensus sequence" appended to every query doc
                queries.get(i).setName(annotatedDocuments[i].getName());
            }
        } else {
            queries = Arrays.asList(annotatedDocuments);
        }
        return queries;
    }

    public String getKeywords() {
        return "COI";
    }

    public DatabaseService getDatabase() {
        return databaseOption.getValue().database;
    }

    public String getProgram() {
        return programOption.getValue().getName();
    }

    public Options getSearchOptions() {
        return programOption.getValue().options;
    }

    private static final class DatabaseOptionValue extends OptionValue {

        final DatabaseService database;

        private DatabaseOptionValue(DatabaseService database) {
            super(database.getUniqueID(), database.getName());
            this.database = database;
        }
    }

    private static final class ProgramOptionValue extends OptionValue {

        final Options options;

        private ProgramOptionValue(String name, String label, Options options) {
            super(name, label);
            this.options = options;
        }
    }
}
