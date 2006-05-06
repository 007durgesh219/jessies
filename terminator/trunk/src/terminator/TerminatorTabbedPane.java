package terminator;

import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import terminator.view.*;

public class TerminatorTabbedPane extends JTabbedPane {
    public TerminatorTabbedPane() {
        // We want to provide custom tool tips.
        ToolTipManager.sharedInstance().registerComponent(this);
        
        initPopUpMenu();
        
        addChangeListener(new TerminalFocuser());
        ComponentUtilities.disableFocusTraversal(this);
        
        // The tabs themselves (the components with the labels)
        // shouldn't be able to get the focus. If they can, clicking
        // on an already-selected tab takes focus away from the
        // associated terminal, which is annoying.
        setFocusable(false);
    }
    
    private void initPopUpMenu() {
        EPopupMenu tabMenu = new EPopupMenu(this);
        tabMenu.addMenuItemProvider(new MenuItemProvider() {
            public void provideMenuItems(MouseEvent e, Collection<Action> actions) {
                // If the user clicked on some part of the tabbed pane that isn't actually a tab, we're not interested.
                int tabIndex = indexAtLocation(e.getX(), e.getY());
                if (tabIndex == -1) {
                    return;
                }
                
                actions.add(new TerminatorMenuBar.NewTabAction());
                actions.add(new TerminatorMenuBar.DetachTabAction());
                actions.add(null);
                actions.add(new TerminatorMenuBar.CloseAction());
            }
        });
    }
    
    @Override
    public String getToolTipTextAt(int index) {
        String primaryModifier = GuiUtilities.isMacOs() ? "\u2318" : "Alt+";
        String control = GuiUtilities.isMacOs() ? "\u2303" : "Ctrl+";
        return "<html>Use " + primaryModifier + (index + 1) + " to switch to this tab.<br>Use " + control + "Tab to cycle through the tabs.";
    }
    
    // Just overriding getToolTipTextAt is insufficient because the default implementation of getToolTipText doesn't call it.
    @Override
    public String getToolTipText(MouseEvent event) {
        int index = indexAtLocation(event.getX(), event.getY());
        if (index != -1) {
            return getToolTipTextAt(index);
        }
        return super.getToolTipText(event);
    }
    
    
    /**
     * Ensures that when we change tab, we give focus to that terminal.
     */
    private class TerminalFocuser implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            final JTerminalPane selected = (JTerminalPane) getSelectedComponent();
            if (selected != null) {
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        selected.requestFocus();
                        selected.getTerminatorFrame().updateFrameTitle();
                    }
                });
            }
        }
    }
}
