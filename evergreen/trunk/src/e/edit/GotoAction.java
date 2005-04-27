package e.edit;

import java.awt.event.*;
import e.util.*;

/**
The ETextArea action to open a 'goto' dialog.
*/
public class GotoAction extends ETextAction implements MinibufferUser {
    private static final String ACTION_NAME = "Go to Line...";
    
    public ETextWindow currentTextWindow;
    public int initialCaretPosition;
    
    public GotoAction() {
        super(ACTION_NAME);
        putValue(ACCELERATOR_KEY, e.util.GuiUtilities.makeKeyStroke("L", false));
    }
    
    public void actionPerformed(ActionEvent e) {
        currentTextWindow = getFocusedTextWindow();
        if (currentTextWindow == null) {
            return;
        }
        // FIXME - selection
        initialCaretPosition = currentTextWindow.getText().getSelectionStart();
        
        Edit.showMinibuffer(this);
    }
    
    //
    // MinibufferUser interface.
    //
    
    public StringHistory getHistory() {
        return null;
    }
    
    public String getInitialValue() {
        ETextArea textArea = currentTextWindow.getText();
        // FIXME - selection
        int lineNumber = 1 + textArea.getLineOfOffset(textArea.getSelectionStart());
        return Integer.toString(lineNumber);
    }
    
    public String getPrompt() {
        return "Go To Line";
    }
    
    /** Checks whether the line number is a number, and is less than the file's line count. */
    public boolean isValid(String value) {
        try {
            int line = Integer.parseInt(value);
            return line < currentTextWindow.getText().getLineCount();
        } catch (NumberFormatException ex) {
            return false;
        }
    }
    
    public void valueChangedTo(String value) {
        try {
            int line = Integer.parseInt(value);
            if (line < currentTextWindow.getText().getLineCount()) {
                currentTextWindow.goToLine(line);
            }
        } catch (NumberFormatException ex) {
            ex = ex; // Do nothing.
        }
    }
    
    /** Returns false because we offer no actions, so the minibuffer should remain active. */
    public boolean interpretSpecialKeystroke(KeyEvent e) {
        return false;
    }
    
    public boolean wasAccepted(String value) {
        try {
            int line = Integer.parseInt(value);
            if (line < currentTextWindow.getText().getLineCount()) {
                currentTextWindow.goToLine(line);
                return true;
            }
            Edit.showAlert(ACTION_NAME, "There is no line " + line + ".");
        } catch (NumberFormatException ex) {
            Edit.showAlert(ACTION_NAME, "The text '" + value + "' isn't a line number.");
        }
        return false;
    }
    
    public void wasCanceled() {
        currentTextWindow.getText().setCaretPosition(initialCaretPosition);
    }
}
