package terminator;

import e.forms.*;
import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import terminator.terminal.*;
import terminator.view.*;

public class InfoDialog {
    private static final InfoDialog INSTANCE = new InfoDialog();
    
    private ETextField title;
    private ETextField dimensions;
    private ETextField processes;
    private ETextField logFilename;
    private ETextField ptyFilename;
    private JCheckBox suspendLogging;
    private JTerminalPane terminal;
    
    private InfoDialog() {
        this.title = new TitleField();
        this.dimensions = new UneditableField();
        this.processes = new UneditableField();
        this.logFilename = new UneditableField();
        this.ptyFilename = new UneditableField();
        this.suspendLogging = makeSuspendLoggingCheckBox();
    }
    
    private static class UneditableField extends ETextField {
        public UneditableField() {
            // Text fields with setEditable(false) don't look very different on any platform but Win32.
            // Win32 is the only platform that clearly distinguishes between all the combinations of editable and enabled.
            // It's sadly unclear that those responsible for the other platforms even understand the distinction.
            // Although Cocoa makes a overly-subtle visual distinction, Apple's Java doesn't reproduce it.
            // As a work-around, we use a trick various Mac OS programs use: make the uneditable text fields look like labels.
            // You lose the visual clue that you can select and copy the text, but that's less important than obscuring the visual clue on editable fields that they're editable.
            // FIXME: at the moment, we're far too wide when there are lots of processes with access to the terminal.
            // FIXME: a PTextArea would retain the selection behavior but add wrapping, but we need to change FormPanel first because GridBagLayout won't let us do the right thing when (say) the "dimensions" and "processes" need one line-height each, but "log filename" needs two line-heights.
            // FIXME: because of the way the GTK+ LAF renders text fields, this looks awful. setBorder effectively just removes the padding, and setOpaque has no effect. See getName for a partial work-around.
            setBorder(null);
            setOpaque(false);
            setEditable(false);
        }
        
        public String getName() {
            // The GTK+ LAF insists on rendering a border unless we're a tree cell editor. So pretend to be a tree cell editor.
            // This still doesn't look quite right, but it's the best I know how to do, and it's significantly better than doing nothing.
            return GuiUtilities.isGtk() ? "Tree.cellEditor" : super.getName();
        }
    }
    
    private class TitleField extends ETextField {
        @Override
        public void textChanged() {
            terminal.setName(title.getText());
        }
    }
    
    private JCheckBox makeSuspendLoggingCheckBox() {
        final JCheckBox checkBox = new JCheckBox("Suspend Logging");
        checkBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                terminal.getLogWriter().setSuspended(suspendLogging.isSelected());
                // The LogWriter might not be able to comply, so ensure that
                // the UI reflects the actual state.
                checkBox.setSelected(terminal.getLogWriter().isSuspended());
            }
        });
        return checkBox;
    }
    
    public static InfoDialog getSharedInstance() {
        return INSTANCE;
    }
    
    public void showInfoDialogFor(final JTerminalPane terminal) {
        updateFieldValuesFor(terminal);
        
        JFrame frame = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, terminal);
        FormBuilder form = new FormBuilder(frame, "Info");
        FormPanel formPanel = form.getFormPanel();
        formPanel.addRow("Title:", title);
        formPanel.addRow("Dimensions:", dimensions);
        formPanel.addRow("Pseudo-Terminal:", ptyFilename);
        formPanel.addRow("Processes:", processes);
        formPanel.addRow("Log Filename:", logFilename);
        if (GuiUtilities.isMacOs() || GuiUtilities.isWindows()) {
            JButton showInFinderButton = new JButton(GuiUtilities.isMacOs() ? "Show in Finder" : "Show in Explorer");
            showInFinderButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    terminal.getLogWriter().flush();
                    GuiUtilities.selectFileInFileViewer(logFilename.getText());
                }
            });
            formPanel.addRow("", showInFinderButton);
        }
        formPanel.addRow("", suspendLogging);
        form.getFormDialog().setRememberBounds(false);
        form.showNonModal();
    }
    
    private void updateFieldValuesFor(JTerminalPane terminal) {
        this.terminal = terminal;
        
        title.setText(terminal.getName());
        
        Dimension size = terminal.getTextPane().getVisibleSizeInCharacters();
        dimensions.setText(size.width + " x " + size.height);
        
        PtyProcess ptyProcess = terminal.getControl().getPtyProcess();
        if (ptyProcess != null) {
            ptyFilename.setText(ptyProcess.getPtyName());
            processes.setText(ptyProcess.listProcessesUsingTty());
        } else {
            ptyFilename.setText("(no pseudo-terminal allocated)");
            processes.setText("");
        }
        
        LogWriter logWriter = terminal.getLogWriter();
        logFilename.setText(logWriter.getInfo());
        suspendLogging.setSelected(logWriter.isSuspended());
    }
}
