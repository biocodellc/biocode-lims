package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.GTable;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a revision of the final end product of sequencing.  Can either be {@link Status#PASS} or {@link Status#FAIL}.
 * If it is a pass, then it will include the ID of the consensus sequence in the database.
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 13/09/13 11:24 AM
 */
public class SequencingResult implements XMLSerializable {

    enum Status {
        PASS("passed", "<html><font color=\"green\"><strong>PASS</strong></font></html>"),
        FAIL("failed", "<html><font color=\"red\"><strong>FAIL</strong></font></html>");

        String databaseString;
        String htmlString;

        Status(String databaseString, String htmlString) {
            this.databaseString = databaseString;
            this.htmlString = htmlString;
        }

        String asHtml() {
            return htmlString;
        }

        static Status fromString(String databaseString) {
            for (Status status : Status.values()) {
                if(status.databaseString.equals(databaseString)) {
                    return status;
                }
            }
            return null;
        }
    }
    private Status status;

    private Date date;
    private String notes;
    private FailureReason reason;
    private String reasonDetails;
    private Integer seqId;

    private SequencingResult(Status status, Date date, String notes, FailureReason reason, String reasonDetails, int seqId) {
        this.status = status;
        this.date = date;
        this.notes = notes;
        this.reason = reason;
        this.reasonDetails = reasonDetails;
        this.seqId = seqId;
    }

    public Status getStatus() {
        return status;
    }

    public Date getDate() {
        return date;
    }

    public String getNotes() {
        return notes;
    }

    public FailureReason getReason() {
        return reason;
    }

    public String getReasonDetails() {
        return reasonDetails;
    }

    /**
     * @return The id of the sequence in the database or null
     */
    public Integer getSequenceId() {
        return seqId;
    }

    public static SequencingResult fromResultSet(ResultSet resultSet) throws SQLException {
        String statusString = resultSet.getString("assembly.progress");
        if(statusString == null) {
            return null;
        }

        FailureReason reason;
        int reasonId = resultSet.getInt("failure_reason");
        if(resultSet.wasNull()) {
            reason = null;
        } else {
            reason = FailureReason.getReasonFromIdString(""+reasonId);
        }

        return new SequencingResult(Status.fromString(statusString), resultSet.getDate("assembly.date"),
                resultSet.getString("assembly.notes"), reason, resultSet.getString("failure_notes"), resultSet.getInt("assembly.id"));
    }

    private static final String STATUS = "status";
    private static final String DATE = "date";
    private static final String NOTES = "notes";
    private static final String REASON = "failureReason";
    private static final String DETAILS = "failureDetails";
    private static final String ID = "sequenceID";

    @Override
    public Element toXML() {
        Element root = new Element(XMLSerializable.ROOT_ELEMENT_NAME);

        root.addContent(new Element(STATUS).setText(status.databaseString));
        root.addContent(new Element(DATE).setText(Long.toString(date.getTime())));
        if(notes != null) {
            root.addContent(new Element(NOTES).setText(notes));
        }
        if(reason != null) {
            root.addContent(new Element(REASON).setText(Integer.toString(reason.getId())));
        }
        if(reasonDetails != null) {
            root.addContent(new Element(DETAILS).setText(reasonDetails));
        }
        root.addContent(new Element(ID).setText(Integer.toString(seqId)));

        return root;
    }

    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        String statusText = element.getChildText(STATUS);
        status = Status.fromString(statusText);

        String dateText = element.getChildText(DATE);
        if(dateText != null) {
            Long time = Long.valueOf(dateText);
            if(time != null) {
                date = new Date(time);
            }
        }

        notes = element.getChildText(NOTES);
        String reasonId = element.getChildText(REASON);
        reason = FailureReason.getReasonFromIdString(reasonId);
        reasonDetails = element.getChildText(DETAILS);
        seqId = Integer.valueOf(element.getChildText(ID));
    }

    public SequencingResult(Element element) throws XMLSerializationException {
        fromXML(element);
    }

    public static void display(String title, final List<SequencingResult> sequencingResults) {
        TableModel model = new TableModel() {
            Object[] columns = new Object[]{"Status", "Date", "Notes", "Failure Reason", "Failure Details", ""};

            @Override
            public int getRowCount() {
                return sequencingResults.size();
            }

            @Override
            public int getColumnCount() {
                return 6;
            }

            @Override
            public String getColumnName(int columnIndex) {
                if(columnIndex >= 0 && columnIndex < columns.length) {
                    return columns[columnIndex].toString();
                } else {
                    return null;
                }
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 5: return SequencingResult.class;
                    default: return String.class;
                }
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                if(rowIndex < 0 || rowIndex >= sequencingResults.size()) {
                    return null;
                }
                SequencingResult result = sequencingResults.get(rowIndex);
                switch (columnIndex) {
                    case 0: return result.getStatus().asHtml();
                    case 1: return DATE_FORMAT.format(result.getDate());
                    case 2: return result.getNotes();
                    case 3:
                        FailureReason reason = result.getReason();
                        return reason == null ? null : reason.getName();
                    case 4: return result.getReasonDetails();
                    case 5: return result;
                    default:
                        return null;
                }
            }

            @Override
            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            }

            @Override
            public void addTableModelListener(TableModelListener l) {
            }

            @Override
            public void removeTableModelListener(TableModelListener l) {
            }
        };

        GPanel resultsPanel = new GPanel(new BorderLayout());
        resultsPanel.setPreferredSize(new Dimension(800, 300));

        final GTable table = new GTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setRowHeight(60);
        table.setDefaultRenderer(SequencingResult.class, new SequencingResultRendererEditor(table.getDefaultRenderer(SequencingResult.class)));
        table.setDefaultRenderer(String.class, new SequencingResultRendererEditor(table.getDefaultRenderer(String.class)));

        int minWidth = 55;
        Enumeration<TableColumn> columns = table.getColumnModel().getColumns();
        while(columns.hasMoreElements()) {
            columns.nextElement().setMinWidth(minWidth);
        }

        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        for(int i : new int[]{2,4}) {
            table.getColumnModel().getColumn(i).setPreferredWidth(200);
        }
        table.getColumnModel().getColumn(5).setPreferredWidth(50);
        table.getColumnModel().getColumn(5).setMaxWidth(50);

        JScrollPane scrollPane = new JScrollPane(table);
        resultsPanel.add(scrollPane, BorderLayout.CENTER);

        table.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object value = table.getValueAt(table.getSelectedRow(), table.getSelectedColumn());
                if(value instanceof SequencingResult) {
                    final SequencingResult result = (SequencingResult) value;
                    if(result.getStatus() == Status.PASS) {
                        displayTableOfResults(result, table);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e) {}

            @Override
            public void mouseEntered(MouseEvent e) {}

            @Override
            public void mouseExited(MouseEvent e) {}
        });

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_ONLY, title);
        dialogOptions.setMaxWidth(1600);
        dialogOptions.setMaxHeight(600);
        Dialogs.showDialog(dialogOptions, resultsPanel);
    }

    private static void displayTableOfResults(final SequencingResult result, final GTable table) {
        final AtomicReference<NucleotideSequenceDocument> doc = new AtomicReference<NucleotideSequenceDocument>();
        final AtomicReference<Exception> error = new AtomicReference<Exception>();
        Runnable downloadSeqs = new Runnable() {
            @Override
            public void run() {
                try {
                    List<AnnotatedPluginDocument> matching = BiocodeService.getInstance().getMatchingAssemblyDocumentsForIds(
                            null, null, Collections.singletonList(result.getSequenceId()), null, false, true, false);
                    assert(matching.size() <= 1);
                    for (AnnotatedPluginDocument annotatedPluginDocument : matching) {
                        doc.set((NucleotideSequenceDocument) annotatedPluginDocument.getDocumentOrNull());
                    }
                } catch (DatabaseServiceException e) {
                    error.set(e);
                }
            }
        };
        @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) final Exception exception = error.get();
        Runnable showEditor = new Runnable() {
            public void run() {
                if(exception != null) {
                    Dialogs.showMessageDialog("Failed to download sequences: " + exception.getMessage());
                } else {
                    SequencingResultEditor editor = new SequencingResultEditor(Collections.singletonList(doc.get()), "");
                    editor.showDialog(table);
                }
            }
        };
        BiocodeService.block("Downloading Sequences...", table, downloadSeqs, showEditor);
    }

    private static class SequencingResultRendererEditor extends AbstractCellEditor implements TableCellRenderer, TableCellEditor{

        private TableCellRenderer defaultRenderer;

        private SequencingResultRendererEditor(TableCellRenderer defaultRenderer) {
            this.defaultRenderer = defaultRenderer;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            return null;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component defaultComp = defaultRenderer.getTableCellRendererComponent(table, value, false, false, row, column);
            Component component;
            if(value instanceof SequencingResult) {
                Component nullComp = defaultRenderer.getTableCellRendererComponent(table, null, false, hasFocus, row, column);
                if(((SequencingResult)value).getStatus() == Status.PASS) {
                    JPanel panel = new JPanel(new BorderLayout());
                    Box verticalBox = Box.createVerticalBox();
                    panel.add(verticalBox, BorderLayout.CENTER);
                    verticalBox.add(Box.createVerticalGlue());
                    JButton button = new JButton("View");
                    verticalBox.add(button);
                    verticalBox.add(Box.createVerticalGlue());
                    component = panel;
                } else {
                    component = nullComp;
                }
            } else if(value instanceof String && (column == 2 || column == 4)) {
                JTextArea textArea = new JTextArea((String) value, 4, 40);
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);
                component = textArea;
            } else {
                return defaultComp;
            }
            component.setForeground(defaultComp.getForeground());
            component.setBackground(defaultComp.getBackground());
            return component;
        }
    }

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy");
}
