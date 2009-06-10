package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.plugins.moorea.HiddenOptionsPopupButton;
import com.biomatters.plugins.moorea.AdvancedAndNormalPanelsSwappedOptions;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 14/05/2009
 * Time: 1:04:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class ThermocycleEditor extends JPanel {

    private Thermocycle thermocycle;

    public static void main(String[] args) {
        Thermocycle cycle = new Thermocycle();

        Thermocycle.Cycle cycle1 = new Thermocycle.Cycle(1);
        cycle1.addState(new Thermocycle.State(95, 120));

        Thermocycle.Cycle cycle2 = new Thermocycle.Cycle(25);
        cycle2.addState(new Thermocycle.State(95, 15));
        cycle2.addState(new Thermocycle.State(50, 15));
        cycle2.addState(new Thermocycle.State(60, 240));

        Thermocycle.Cycle cycle3 = new Thermocycle.Cycle(1);
        cycle3.addState(new Thermocycle.State(60, 601));
        cycle3.addState(new Thermocycle.State(160, 601));

        Thermocycle.Cycle cycle4 = new Thermocycle.Cycle(25);
        cycle4.addState(new Thermocycle.State(10, Integer.MAX_VALUE));

        cycle.addCycle(cycle1);
        cycle.addCycle(cycle2);
        cycle.addCycle(cycle3);
        cycle.addCycle(cycle4);

        TestGeneious.initialize();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        } catch (UnsupportedLookAndFeelException e1) {
            e1.printStackTrace();
        }

        //ThermocycleViewer viewer = new ThermocycleViewer(cycle);
        ThermocycleEditor editor = new ThermocycleEditor();

        final JFrame frame = new JFrame();

        editor.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent e) {
                frame.pack();
            }
        });

        frame.getContentPane().add(editor);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.show();

    }

    public ThermocycleEditor() {
        this(new Thermocycle());
    }

    private List<ChangeListener> listeners =  new ArrayList<ChangeListener>();

    public void addChangeListener(ChangeListener cl) {
        listeners.add(cl);
    }


    public ThermocycleEditor(Thermocycle tcycle) {
        final Options thermocycleOptions = new Options(this.getClass());

        thermocycleOptions.addStringOption("name", "Name", "Untitled");

        Options cycleOptions = new Options(this.getClass());
        HiddenOptionsPopupButton buttonOption = new HiddenOptionsPopupButton("popupButton", "Edit Cycle", "states", "Edit Cycle");

        Options stateOptions = new Options(this.getClass());
        stateOptions.beginAlignHorizontally("", false);
        stateOptions.addIntegerOption("temp", "Temparature", 20, 0, 200);
        Options.IntegerOption timeOption = stateOptions.addIntegerOption("time", "Time", 1, 1, Integer.MAX_VALUE);
        timeOption.setDisabledValue(Integer.MAX_VALUE);
        Options.BooleanOption infinityOption = stateOptions.addBooleanOption("infinity", "Infinite", false);
        infinityOption.addDependent(timeOption, false);
        stateOptions.endAlignHorizontally();

        Options stateMultipleOptions = new AdvancedAndNormalPanelsSwappedOptions(this.getClass(), "");
//        for(Options.Option o : stateOptions.getOptions()) {
//            o.setAdvanced(true);
//        }
        stateMultipleOptions.addMultipleOptions("states", stateOptions, false);
        cycleOptions.addChildOptions("states", "States", "", stateMultipleOptions);


        cycleOptions.beginAlignHorizontally("", false);
        cycleOptions.addIntegerOption("repeats", "# of cycles", 1, 1, Integer.MAX_VALUE);
        cycleOptions.addCustomOption(buttonOption);
        cycleOptions.endAlignHorizontally();


        Options.MultipleOptions multipleOptions = thermocycleOptions.addMultipleOptions("cycleOptions", cycleOptions, false);

        Options.Option<String, JScrollPane> notesOption = new Options.Option<String, JScrollPane>("notes", "", "") {
            public String getValueFromString(String value) {
                return value;
            }

            protected void setValueOnComponent(JScrollPane component, String value) {
                ((JTextArea)component.getViewport().getView()).setText(value);
            }

            protected JScrollPane createComponent() {
                final JTextArea notes = new JTextArea(5,40);
                JScrollPane notesScroller = new JScrollPane(notes);
                notesScroller.setBorder(BorderFactory.createTitledBorder("Notes"));
                notes.addKeyListener(new KeyListener(){
                    public void keyTyped(KeyEvent e) {
                        setValue(notes.getText());
                    }

                    public void keyPressed(KeyEvent e) {
                        setValue(notes.getText());
                    }

                    public void keyReleased(KeyEvent e) {
                        setValue(notes.getText());
                    }
                });
                return notesScroller;
            }
        };
        notesOption.setSpanningComponent(true);
        thermocycleOptions.addCustomOption(notesOption);



        if(tcycle != null) {
            int i = 0;
            while(i < multipleOptions.getValues().size() && i < tcycle.getCycles().size()) {
                multipleOptions.getValues().get(i).setValue("repeats", tcycle.getCycles().get(i).getRepeats());
                i++;
            }
            while (i < tcycle.getCycles().size()) {
                Thermocycle.Cycle cycle = tcycle.getCycles().get(i);
                Options options = multipleOptions.addValue(true);
                options.setValue("repeats", cycle.getRepeats());
                i++;
            }
        }

        setLayout(new BorderLayout());

        final ThermocycleViewer viewer = new ThermocycleViewer(tcycle);

        SimpleListener changeListener = new SimpleListener() {
            public void objectChanged() {
                thermocycle = getThermoCycleFromOptions(thermocycleOptions);
                viewer.setThermocycle(thermocycle);
                for (ChangeListener listener : listeners) {
                    listener.stateChanged(null);
                }
//                Runnable runnable = new Runnable() {
//                    public void run() {
//                        try {
//                            Thread.sleep(1000);
//                        } catch (InterruptedException e) {
//                            //do nothing
//                        }
//                        thermocycleOptions.getPanel().revalidate();
//                        //thermocycleOptions.getPanel().validate();
//                    }
//                };
//                new Thread(runnable).start();
                revalidate();
                invalidate();
                revalidate();
                validate();
                getParent().invalidate();
                getParent().validate();
                revalidate();
                getRootPane().invalidate();

            }
        };
        multipleOptions.addChangeListener(changeListener);
        for(Options.Option o : thermocycleOptions.getOptions()) {
            o.addChangeListener(changeListener);
        }
        thermocycle = getThermoCycleFromOptions(thermocycleOptions);

        viewer.setBorder(new LineBorder(SystemColor.control.darker()));


        add(viewer, BorderLayout.NORTH);
        add(thermocycleOptions.getPanel(), BorderLayout.CENTER);
    }

    private Thermocycle getThermoCycleFromOptions(Options thermocycleOptions) {
        Thermocycle tcycle = new Thermocycle(thermocycleOptions.getValueAsString("name"),0);
        tcycle.setNotes(thermocycleOptions.getValueAsString("notes"));

        Options.MultipleOptions cycleOptions = thermocycleOptions.getMultipleOptions("cycleOptions");

        for(Options cycleValue : cycleOptions.getValues()) {
            Thermocycle.Cycle cycle = new Thermocycle.Cycle((Integer)cycleValue.getValue("repeats"));
            Options stateOptions = cycleValue.getChildOptions().get("states");
            Options.MultipleOptions stateMultipleOptions = stateOptions.getMultipleOptions("states");

            for(Options stateValue : stateMultipleOptions.getValues()) {
                Thermocycle.State state = new Thermocycle.State((Integer)stateValue.getValue("temp"), (Integer)stateValue.getValue("time"));
                cycle.addState(state);
            }
            
            tcycle.addCycle(cycle);
        }


        return tcycle;
    }

    public Thermocycle getThermocycle() {
        return thermocycle;
    }

    public void print(Graphics g, Dimension dimensions) {
        if(thermocycle == null) {
            return;
        }
        String notes = thermocycle.getNotes();
        JTextArea textArea = new JTextArea();
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
    }


    static class ThermocycleViewer extends JPanel {
        private Thermocycle thermocycle;
        private int stateWidth;
        private int stateCount = 0;
        private int maxTemp = 0;

        public ThermocycleViewer(Thermocycle tcycle) {
            setBackground(Color.white);

            setThermocycle(tcycle);



        }

        private void setThermocycle(Thermocycle tcycle) {
            this.thermocycle = tcycle;
            stateCount = 0;
            maxTemp = 0;
            if(thermocycle != null) {
                for(Thermocycle.Cycle cycle : thermocycle.getCycles()) {
                    for(Thermocycle.State state : cycle.getStates()) {
                        stateCount++;
                        maxTemp = Math.max(state.getTemp(), maxTemp);
                    }
                }
            }
            maxTemp += 10;
            repaint();
        }

        public Dimension getPreferredSize() {
            return new Dimension(Math.max(50*stateCount, 320), Math.max(2*maxTemp, 240));
        }


        public void paintComponent(Graphics g1) {
            Graphics2D g = (Graphics2D)g1;

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(getBackground());
            g.fillRect(0,0,getWidth(),getHeight());
            if(thermocycle == null) {
                return;
            }

            stateWidth = getWidth() / Math.max(1,stateCount);

            int x = 0;
            int prevHeight = -1;
            g.setFont(new Font("serif", Font.PLAIN, 10));
            for (int i = 0; i < thermocycle.getCycles().size(); i++) {
                Thermocycle.Cycle cycle = thermocycle.getCycles().get(i);
                g.setStroke(new BasicStroke(1.0f));
                g.setColor(Color.black);

                String statesString = i < states.length ? states[i] : "" + (i + 1);

                g.drawString(statesString, x+2, getHeight()-2);
                String repeatsString = cycle.getRepeats()+"x";
                Rectangle2D repeatsStringBounds = getTextBounds(g, statesString);
                g.drawString(repeatsString, x+2, getHeight()-(int)repeatsStringBounds.getHeight()-4);


                for (Thermocycle.State state : cycle.getStates()) {
                    int height = getHeight() - (getHeight() * state.getTemp()) / maxTemp;
                    if (prevHeight < 0) {
                        prevHeight = height;
                    }
                    g.drawLine(x, prevHeight, x + 10, height);
                    g.drawLine(x + 10, height, x + stateWidth, height);


                    //draw the temparature
                    String tempString = getTempString(state.getTemp());
                    Rectangle2D tempBounds = getTextBounds(g, tempString);
                    g.drawString(tempString, x+(int)((10+stateWidth-tempBounds.getWidth())/2), height-3);

                    //draw the time
                    String timeString = getTimeString(state.getTime());
                    Rectangle2D timeBounds = getTextBounds(g, timeString);
                    g.drawString(timeString, x+(int)((10+stateWidth-timeBounds.getWidth())/2), height+(int)timeBounds.getHeight()+4);


                    prevHeight = height;
                    x += stateWidth;
                }

                if(i < thermocycle.getCycles().size()-1)  {
                    g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{4.0f, 2.0f}, 1.0f));
                    g.setColor(Color.gray);
                    g.drawLine(x, 0, x, getHeight() - (int)(2*repeatsStringBounds.getHeight() + 5));
                }
            }


        }

        private Rectangle2D getTextBounds(Graphics g, String labelText) {
            FontRenderContext frc = ((Graphics2D)g).getFontRenderContext();
            TextLayout tl = new TextLayout(labelText, g.getFont(), frc);
            return tl.getBounds();
        }

        private String getTempString(int temp) {
            return ""+temp+((char)(0xB0))+"C";
        }

        private String getTimeString(int time) {
            if(time == Integer.MAX_VALUE) {
                return ""+(char)0x221E;
            }

            int mins = time/60;
            int secs = time % 60;

            String timeString = "";

            if(mins > 0) {
                timeString += mins+"min";
            }
            if(mins > 0 && secs > 0) {
                timeString += " ";
            }
            if(secs > 0) {
                timeString += secs+"sec";
            }

            return timeString;
        }

        private static String[] states = new String[] {
                "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
        };

    }

    public static List<Thermocycle> editThermocycles(final List<Thermocycle> thermocycles, Component owner) {
        JPanel editPanel = new JPanel(new BorderLayout());
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        editPanel.add(splitPane, BorderLayout.CENTER);
        final JList thermocycleList = new JList();
        thermocycleList.setPrototypeCellValue("ACEGIKMOQSUWY13579");
        Class thermocycleClass = thermocycles.get(0).getClass();
        final List<Thermocycle> newThermocycles = new ArrayList<Thermocycle>();
        final AbstractListModel listModel = new AbstractListModel() {
            public int getSize() {
                return thermocycles.size()+newThermocycles.size();
            }

            public Object getElementAt(int index) {
                if(index < thermocycles.size())
                    return thermocycles.get(index);
                return newThermocycles.get(index-thermocycles.size());
            }
        };
        thermocycleList.setModel(listModel);
        JScrollPane scroller = new JScrollPane(thermocycleList);
        JPanel leftPanel = new JPanel(new BorderLayout());
        JButton addButton = new JButton("Add");
        leftPanel.add(addButton, BorderLayout.SOUTH);
        leftPanel.add(scroller, BorderLayout.CENTER);
        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                ThermocycleEditor editor = new ThermocycleEditor();
                if(Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "New Thermocycle", splitPane), editor).equals(Dialogs.OK)) {
                    Thermocycle newThermocycle = editor.getThermocycle();
                    newThermocycles.add(newThermocycle);
                    for(ListDataListener listener : listModel.getListDataListeners()){
                        listener.intervalAdded(new ListDataEvent(listModel, ListDataEvent.INTERVAL_ADDED, listModel.getSize(), listModel.getSize()-1));
                    }
                    thermocycleList.setSelectedValue(newThermocycle, true);
                }

            }
        });

        splitPane.setLeftComponent(leftPanel);
        thermocycleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        thermocycleList.addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                Thermocycle selectedThermocycle = (Thermocycle)thermocycleList.getSelectedValue();
                ThermocycleViewer viewer = new ThermocycleViewer(selectedThermocycle);
                JPanel tPanel = new JPanel(new BorderLayout());
                tPanel.setBackground(Color.white);
                tPanel.add(viewer, BorderLayout.NORTH);

                JTextArea notes = new JTextArea(selectedThermocycle.getNotes());
                notes.setEditable(false);
                notes.setBackground(Color.white);
                JScrollPane scroller = new JScrollPane(notes);
                scroller.setBackground(Color.white);
                scroller.setBorder(new OptionsPanel.RoundedLineBorder("Notes", false));
                scroller.setPreferredSize(new Dimension(100, Math.max(75,scroller.getPreferredSize().height)));

                tPanel.add(scroller, BorderLayout.SOUTH);

                setRightComponent(splitPane, tPanel);
            }
        });

        thermocycleList.setSelectedIndex(0);

        if(Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Edit Thermocycles", owner), editPanel).equals(Dialogs.OK)) {
            return newThermocycles;
        }
        return Collections.EMPTY_LIST;

    }

    private static void setRightComponent(JSplitPane sp, Component component) {
        int location = sp.getDividerLocation();
        sp.setRightComponent(component);
        sp.setDividerLocation(location);
    }


}
