package terminator;

import e.gui.*;
import e.util.*;
import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Logs terminal output to a file in ~/.terminal-logs. Logging can be
 * temporarily suspended. If the terminal logs directory does not exist
 * or we can't open the log file for some other reason, logging is
 * automatically suspended, and can't be un-suspended.
 */
public class LogWriter {
	private static DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HHmmssZ");
	
	private String info = "(not logging)";
	private BufferedWriter stream;
	private boolean suspended;
	
	public LogWriter(String[] command) {
		try {
			String prefix = StringUtilities.join(command, " ");
			initLogging(prefix);
		} catch (Throwable th) {
			SimpleDialog.showDetails(null, "Couldn't Open Log File", th);
		} finally {
			this.suspended = (stream == null);
		}
	}
	
	private void initLogging(String prefix) throws IOException {
		prefix = java.net.URLEncoder.encode(prefix, "UTF-8");
		String timestamp = dateFormatter.format(new Date());
		String logsDirectoryName = System.getProperty("org.jessies.terminator.logDirectory");
		File logsDirectory = new File(logsDirectoryName);
		if (logsDirectory.exists()) {
			File logFile = new File(logsDirectory, prefix + '-' + timestamp + ".txt");
			this.info = logFile.toString();
			this.stream = new BufferedWriter(new FileWriter(logFile));
		} else {
			this.info = "(" + logsDirectoryName + " does not exist)";
		}
	}
	
	public void append(char ch) throws IOException {
		if (suspended) {
			return;
		}
		
		stream.write(ch);
		if (ch == '\n') {
			stream.flush();
		}
	}
	
	public void close() {
		if (stream != null) {
			try {
				suspended = true;
				stream.close();
				stream = null;
			} catch (Throwable th) {
				Log.warn("Exception occurred closing log stream \"" + info + "\".", th);
			}
		}
	}
	
	public String getInfo() {
		return info;
	}
	
	public void setSuspended(boolean newState) {
		if (stream != null) {
			try {
				stream.flush();
			} catch (Throwable th) {
				Log.warn("Exception occurred flushing log stream \"" + info + "\".", th);
			}
			suspended = newState;
		}
	}
	
	public boolean isSuspended() {
		return suspended;
	}
}
