package terminator;

import com.apple.eawt.*;
import e.gui.*;
import e.util.*;
import java.awt.EventQueue;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import terminator.view.*;

public class Terminator {
	private static final Terminator INSTANCE = new Terminator();
	
	private List<String> arguments;
	private Frames frames = new Frames();
	
	public static Terminator getSharedInstance() {
		return INSTANCE;
	}
	
	private Terminator() {
		Log.setApplicationName("Terminator");
		initAboutBox();
		initMacOsEventHandlers();
	}
	
	private void initMacOsEventHandlers() {
		if (GuiUtilities.isMacOs() == false) {
			return;
		}
		
		Application.getApplication().setEnabledPreferencesMenu(true);
		Application.getApplication().addApplicationListener(new ApplicationAdapter() {
			public void handleReOpenApplication(ApplicationEvent e) {
				if (frames.isEmpty()) {
					openFrame(JTerminalPane.newShell());
				}
				e.setHandled(true);
			}
			
			public void handlePreferences(ApplicationEvent e) {
				Options.getSharedInstance().showPreferencesDialog();
				e.setHandled(true);
			}
			
			public void handleQuit(ApplicationEvent e) {
				boolean quit = true;
				
				int terminalCount = 0;
				for (TerminatorFrame frame : frames) {
					terminalCount += frame.getTerminalPaneCount();
				}
				if (terminalCount > 0) {
					// FIXME: we shouldn't count windows whose terminals' PtyProcesses have finished.
					// FIXME: can we find out, like Terminal, what processes are at risk and name them?
					quit = SimpleDialog.askQuestion(frames.get(0), "Terminator", "<html><b>Quit Terminator?</b><p>You have " + StringUtilities.pluralize(terminalCount, "terminal", "terminals") + " which may contain running processes. Do you want to quit and risk terminating these processes?", "Quit");
				}
				if (quit) {
					e.setHandled(true);
				}
			}
		});
	}
	
	private void initAboutBox() {
		AboutBox aboutBox = AboutBox.getSharedInstance();
		aboutBox.setApplicationName("Terminator");
		aboutBox.addCopyright("Copyright (C) 2004-2006 Free Software Foundation, Inc.");
		aboutBox.addCopyright("All Rights Reserved.");
	}
	
	private void startTerminatorServer() {
		String display = System.getenv("DISPLAY");
		if (display == null) {
			display = "";
		}
		new InAppServer("Terminator", "~/.terminal-logs/.terminator-server-port" + display, TerminatorServer.class, new TerminatorServer());
	}
	
	// Returns whether we started the UI.
	public boolean parseCommandLine(final String[] argumentArray, PrintWriter out, PrintWriter err) throws IOException {
		arguments = Options.getSharedInstance().parseCommandLine(argumentArray);
		if (arguments.contains("-h") || arguments.contains("-help") || arguments.contains("--help")) {
			showUsage(out);
		} else {
			initUi();
			return true;
		}
		return false;
	}

	private void parseOriginalCommandLine(final String[] argumentArray, PrintWriter out, PrintWriter err) throws IOException {
		if (parseCommandLine(argumentArray, out, err)) {
			startTerminatorServer();
		}
	}
	
	public void frameClosed(TerminatorFrame frame) {
		frames.remove(frame);
	}
	
	public void openFrame(JTerminalPane terminalPane) {
		TerminatorFrame frame = new TerminatorFrame(Collections.singletonList(terminalPane));
		frames.add(frame);
	}
	
	/**
	 * Sets up the user interface on the AWT event thread.
	 */
	private void initUi() {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				TerminatorFrame frame = new TerminatorFrame(getInitialTerminals());
				frames.add(frame);
			}
		});
	}
	
	/**
	 * Invoked by the preferences dialog whenever an option is changed.
	 */
	public void repaintUi() {
		for (int i = 0; i < frames.size(); ++i) {
			frames.get(i).repaint();
		}
	}
	
	public JFrame getSuitableParentFrameForForms() {
		return frames.getFrame();
	}
	
	private List<JTerminalPane> getInitialTerminals() {
		ArrayList<JTerminalPane> result = new ArrayList<JTerminalPane>();
		String name = null;
		String workingDirectory = null;
		for (int i = 0; i < arguments.size(); ++i) {
			String word = arguments.get(i);
			if (word.equals("-n")) {
				name = arguments.get(++i);
				continue;
			}
			if (word.equals("--working-directory")) {
				workingDirectory = arguments.get(++i);
				continue;
			}
			
			// We can't hope to imitate the shell's parsing of a string, so pass it unmolested to the shell.
			String command = word;
			result.add(JTerminalPane.newCommandWithName(command, name, workingDirectory));
			name = null;
		}
		
		if (result.isEmpty()) {
			result.add(JTerminalPane.newShellWithName(name, workingDirectory));
		}
		return result;
	}

	public void showUsage(PrintWriter out) {
		out.println("Usage: terminator [--help | --version] [-xrm <resource-string>]... [[-n <name>] [--working-directory <directory>] [<command>]]...");
		out.println();
		out.println("Current resource settings:");
		Options.getSharedInstance().showOptions(out);
		out.println();
		out.println("Terminator will read your .Xdefaults and .Xresources files, and use");
		out.println("resources of class Rxvt, Terminator or XTerm.");
	}
	
	public static void main(final String[] arguments) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					GuiUtilities.initLookAndFeel();
					PrintWriter outWriter = new PrintWriter(System.out);
					PrintWriter errWriter = new PrintWriter(System.err);
					Terminator.getSharedInstance().parseOriginalCommandLine(arguments, outWriter, errWriter);
					outWriter.flush();
					errWriter.flush();
				} catch (Throwable th) {
					Log.warn("Couldn't start Terminator.", th);
					System.exit(1);
				}
			}
		});
	}
}
