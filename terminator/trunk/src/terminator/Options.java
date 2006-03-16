package terminator;

import e.forms.*;
import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;

/**
 * Reads in settings from the file system and makes them conveniently
 * available.
 * 
 * There's a grand tradition amongst Unix terminal emulators of pretending
 * to be XTerm, but that didn't work out well for us, because we're too
 * different.
 * 
 * All settings should have a default, so that users can use "--help" to see
 * every available option, and edit them in the preferences dialog.
 */
public class Options {
	private static final Options INSTANCE = new Options();
	
	private static final String TERMINATOR_SETTINGS_FILENAME = ".terminator-settings";
	
	private static final String ANTI_ALIAS = "antiAlias";
	private static final String BLOCK_CURSOR = "blockCursor";
	private static final String CURSOR_BLINK = "cursorBlink";
	private static final String FANCY_BELL = "fancyBell";
	private static final String VISUAL_BELL = "visualBell";
	private static final String FONT_NAME = "fontName";
	private static final String FONT_SIZE = "fontSize";
	private static final String INITIAL_COLUMN_COUNT = "initialColumnCount";
	private static final String INITIAL_ROW_COUNT = "initialRowCount";
	private static final String INTERNAL_BORDER = "internalBorder";
	private static final String LOGIN_SHELL = "loginShell";
	private static final String SCROLL_KEY = "scrollKey";
	private static final String SCROLL_TTY_OUTPUT = "scrollTtyOutput";
	private static final String USE_MENU_BAR = "useMenuBar";
	
	private final Pattern resourcePattern = Pattern.compile("(?:Terminator(?:\\*|\\.))?(\\S+):\\s*(.+)");
	
	// Mutable at any time.
	private HashMap<String, Object> options = new HashMap<String, Object>();
	// Immutable after initialization.
	private HashMap<String, Object> defaults = new HashMap<String, Object>();
	private HashMap<String, String> descriptions = new HashMap<String, String>();
	
	private HashMap<String, Color> rgbColors = null;
	
	public static Options getSharedInstance() {
		return INSTANCE;
	}
	
	public void showOptions(Appendable out, boolean showEvenIfDefault) throws IOException {
		String[] keys = options.keySet().toArray(new String[options.size()]);
		Arrays.sort(keys);
		for (String key : keys) {
			Object value = options.get(key);
			
			if (value.equals(defaults.get(key)) && showEvenIfDefault == false) {
				continue;
			}
			
			String description = descriptions.get(key);
			if (description != null) {
				out.append("\n# " + description + "\n");
			}
			if (value instanceof Color) {
				value = colorToString((Color) value);
			} else if (value instanceof Font) {
				value = Font.class.cast(value).getFamily();
			}
			out.append("Terminator*" + key + ": " + value + "\n");
		}
	}
	
	private static String colorToString(Color color) {
		return String.format("#%06x", color.getRGB() & 0xffffff);
	}
	
	/**
	 * Whether or not the shells we start should be login shells.
	 */
	public boolean isLoginShell() {
		return booleanResource(LOGIN_SHELL);
	}
	
	/**
	 * Whether or not pressing a key should cause the the scrollbar to go
	 * to the bottom of the scrolling region.
	 */
	public boolean isScrollKey() {
		return booleanResource(SCROLL_KEY);
	}
	
	/**
	 * Whether or not output to the terminal should cause the scrollbar to
	 * go to the bottom of the scrolling region.
	 */
	public boolean isScrollTtyOutput() {
		return booleanResource(SCROLL_TTY_OUTPUT);
	}
	
	/**
	 * Whether or not to anti-alias text.
	 */
	public boolean isAntiAliased() {
		return booleanResource(ANTI_ALIAS);
	}
	
	/**
	 * Whether to use a block cursor instead of an underline cursor.
	 */
	public boolean isBlockCursor() {
		return booleanResource(BLOCK_CURSOR);
	}
	
	/**
	 * Whether or not to use a nicer-looking but more expensive visual
	 * bell rendition. If there were a way to detect a remote X11 display --
	 * the only place where we really need to disable this -- we could
	 * remove this option, but I don't know of one.
	 */
	public boolean isFancyBell() {
		return booleanResource(FANCY_BELL);
	}
	
	/**
	 * Whether to do nothing when asked to flash.
	 */
	public boolean isVisualBell() {
		return booleanResource(VISUAL_BELL);
	}
	
	/**
	 * Whether or not to blink the cursor.
	 */
	public boolean shouldCursorBlink() {
		return booleanResource(CURSOR_BLINK);
	}
	
	/**
	 * Whether or not to use a menu bar.
	 */
	public boolean shouldUseMenuBar() {
		return booleanResource(USE_MENU_BAR);
	}
	
	public int getInternalBorder() {
		return integerResource(INTERNAL_BORDER);
	}
	
	/**
	 * How many rows a new window should have.
	 */
	public int getInitialRowCount() {
		return integerResource(INITIAL_ROW_COUNT);
	}
	
	/**
	 * How many columns a new window should have.
	 */
	public int getInitialColumnCount() {
		return integerResource(INITIAL_COLUMN_COUNT);
	}
	
	private int integerResource(String name) {
		return Integer.parseInt(stringResource(name));
	}
	
	private String stringResource(String name) {
		return options.get(name).toString();
	}
	
	private boolean booleanResource(String name) {
		return parseBoolean(stringResource(name));
	}
	
	private boolean parseBoolean(String s) {
		return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("on");
	}
	
	/**
	 * Returns a color, if explicitly configured by the user.
	 * We understand colors specified in the #rrggbb form,
	 * or those parsed from rgb.txt.
	 * 
	 * Color names supported by xterm (defaults in parentheses) include:
	 * 
	 *  background (white)
	 *  foreground (black)
	 *  cursorColor (black; we use green)
	 *  highlightColor (reverse video)
	 *  pointerColor
	 *  pointerBackgroundColor
	 * 
	 * xterm also offers complete control over all the ECMA colors.
	 */
	public Color getColor(String name) {
		return (Color) options.get(name);
	}
	
	private Color colorFromString(String description) {
		if (description.startsWith("#")) {
			return Color.decode("0x" + description.substring(1));
		} else {
			return getRgbColor(description);
		}
	}
	
	/**
	 * Returns a suitable fixed font (ignoring all X11 configuration,
	 * because we're unlikely to understand those font specifications).
	 * So we don't get into trouble with Xterm's font resource, and
	 * to work around Font.decode's weaknesses, we use two resources:
	 * "fontName" and "fontSize".
	 */
	public Font getFont() {
		return new Font(Font.class.cast(options.get(FONT_NAME)).getFamily(), Font.PLAIN, integerResource(FONT_SIZE));
	}
	
	private Options() {
		initDefaults();
		initDefaultColors();
		readOptionsFrom(TERMINATOR_SETTINGS_FILENAME);
		aliasColorBD();
	}
	
	/**
	 * Parses "-xrm <resource-string>" options from an array of
	 * command-line arguments, returning the remaining arguments as
	 * a List.
	 */
	public List<String> parseCommandLine(String[] arguments) {
		ArrayList<String> otherArguments = new ArrayList<String>();
		for (int i = 0; i < arguments.length; ++i) {
			if (arguments[i].equals("-xrm")) {
				String resourceString = arguments[++i];
				processResourceString(resourceString);
			} else {
				otherArguments.add(arguments[i]);
			}
		}
		return otherArguments;
	}
	
	private void addDefault(String name, Object value, String description) {
		defaults.put(name, value);
		options.put(name, value);
		descriptions.put(name, description);
	}
	
	private Font makePrototypeFont(String familyName) {
		return new Font(familyName, Font.PLAIN, 1);
	}
	
	/**
	 * Sets the defaults for non-color options.
	 */
	private void initDefaults() {
		addDefault(ANTI_ALIAS, Boolean.FALSE, "Anti-alias text?");
		addDefault(BLOCK_CURSOR, Boolean.FALSE, "Use block cursor?");
		addDefault(CURSOR_BLINK, Boolean.TRUE, "Blink cursor?");
		addDefault(FANCY_BELL, Boolean.TRUE, "High-quality rendering of the visual bell?");
		addDefault(VISUAL_BELL, Boolean.TRUE, "Visual bell (as opposed to no bell)?");
		addDefault(FONT_NAME, makePrototypeFont(GuiUtilities.getMonospacedFontName()), "Font family");
		addDefault(FONT_SIZE, Integer.valueOf(12), "Font size (points)");
		addDefault(INITIAL_COLUMN_COUNT, Integer.valueOf(80), "New terminal width");
		addDefault(INITIAL_ROW_COUNT, Integer.valueOf(24), "New terminal height");
		addDefault(INTERNAL_BORDER, Integer.valueOf(2), "Border (pixels)");
		addDefault(LOGIN_SHELL, Boolean.TRUE, "Start child process with a '-l' argument?");
		addDefault(SCROLL_KEY, Boolean.TRUE, "Scroll to bottom on key press?");
		addDefault(SCROLL_TTY_OUTPUT, Boolean.FALSE, "Scroll to bottom on output?");
		
		if (GuiUtilities.isMacOs() || GuiUtilities.isWindows() || GuiUtilities.isGtk()) {
			// GNOME, Mac, and Win32 users are accustomed to every window having a menu bar.
			options.put(USE_MENU_BAR, Boolean.TRUE);
			defaults.put(USE_MENU_BAR, Boolean.TRUE);
		} else {
			// FIXME: I'm still psyching myself up for the inevitable battle of removing this option.
			addDefault(USE_MENU_BAR, Boolean.TRUE, "Use a menu bar?");
		}
	}
	
	public void showPreferencesDialog() {
		FormBuilder form = new FormBuilder(null, "Preferences");
		FormPanel formPanel = form.getFormPanel();
		
		String[] keys = options.keySet().toArray(new String[options.size()]);
		Arrays.sort(keys);
		ArrayList<ColorPreference> colorPreferences = new ArrayList<ColorPreference>();
		for (String key : keys) {
			String description = descriptions.get(key);
			if (description != null) {
				Object value = options.get(key);
				if (value instanceof Boolean) {
					formPanel.addRow("", new BooleanPreferenceAction(key).makeUi());
				} else if (value instanceof Integer) {
					formPanel.addRow(description + ":", new IntegerPreferenceAction(key).makeUi());
				} else if (value instanceof Font) {
					formPanel.addRow(description + ":", new FontPreferenceAction(key).makeUi());
				} else if (value instanceof Color) {
					if (description.startsWith("Color ")) {
						// Ignore the numbered colors. No-one should be modifying those.
					} else {
						colorPreferences.add(new ColorPreference(description, key));
					}
				} else {
					// FIXME: we should probably handle String.
					// FIXME: the Final Solution should use a HashMap<Class, XPreference>.
					// FIXME: the preference classes should be based on a base class extracted from ColorPreference, rather than the older Action scheme.
					continue;
				}
			}
		}
		
		for (ColorPreference colorPreference : colorPreferences) {
			formPanel.addRow(colorPreference.getDescription() + ":", colorPreference.makeUi());
		}
		
		form.getFormDialog().setAcceptRunnable(new Runnable() {
			public void run() {
				PrintWriter out = null;
				try {
					out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(getHomeFile(TERMINATOR_SETTINGS_FILENAME)), "UTF-8"));
					showOptions(out, false);
				} catch (IOException ex) {
					SimpleDialog.showDetails(null, "Couldn't save preferences.", ex);
				} finally {
					FileUtilities.close(out);
				}
			}
		});
		form.showNonModal();
	}
	
	private class BooleanPreferenceAction extends AbstractAction {
		private String key;
		
		public BooleanPreferenceAction(String key) {
			this.key = key;
			putValue(NAME, descriptions.get(key));
		}
		
		public void actionPerformed(ActionEvent e) {
			JCheckBox checkBox = JCheckBox.class.cast(e.getSource());
			options.put(key, checkBox.isSelected());
			Terminator.getSharedInstance().repaintUi();
		}
		
		public JComponent makeUi() {
			JCheckBox checkBox = new JCheckBox(this);
			checkBox.setSelected(Boolean.class.cast(options.get(key)).booleanValue());
			return checkBox;
		}
	}
	
	private class FontPreferenceAction extends AbstractAction {
		private String key;
		
		public FontPreferenceAction(String key) {
			this.key = key;
			putValue(NAME, descriptions.get(key));
		}
		
		public void actionPerformed(ActionEvent e) {
			JComboBox comboBox = JComboBox.class.cast(e.getSource());
			options.put(key, makePrototypeFont(comboBox.getSelectedItem().toString()));
			Terminator.getSharedInstance().repaintUi();
		}
		
		public JComponent makeUi() {
			JComboBox comboBox = new JComboBox();
			// FIXME: filter out unsuitable fonts. "Zapf Dingbats", for example.
			// FIXME: pull fixed fonts to the top of the list?
			for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
				comboBox.addItem(name);
			}
			comboBox.setSelectedItem(Font.class.cast(options.get(key)).getFamily());
			comboBox.addActionListener(this);
			// FIXME: add a custom renderer so you can see the fonts.
			return comboBox;
		}
	}
	
	private class ColorPreference {
		private String description;
		private String key;
		
		public ColorPreference(String description, String key) {
			this.description = description;
			this.key = key;
		}
		
		public String getDescription() {
			return description;
		}
		
		public JComponent makeUi() {
			final ColorSwatchIcon icon = new ColorSwatchIcon(getColor(key), new Dimension(60, 20));
			final JButton button = new JButton(icon);
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Color newColor = JColorChooser.showDialog(button, "Colors", getColor(key));
					if (newColor != null) {
						options.put(key, newColor);
						icon.setColor(newColor);
						Terminator.getSharedInstance().repaintUi();
					}
				}
			});
			button.putClientProperty("JButton.buttonType", "toolbar");
			return button;
		}
	}
	
	private class IntegerPreferenceAction extends AbstractAction {
		private String key;
		
		public IntegerPreferenceAction(String key) {
			this.key = key;
			putValue(NAME, descriptions.get(key));
		}
		
		public void actionPerformed(ActionEvent e) {
			JTextField textField = JTextField.class.cast(e.getSource());
			boolean okay = false;
			try {
				String text = textField.getText();
				int newValue = Integer.parseInt(text);
				// FIXME: really, an integer preference should have an explicit range.
				if (newValue > 0) {
					options.put(key, newValue);
					Terminator.getSharedInstance().repaintUi();
					okay = true;
				}
			} catch (NumberFormatException ex) {
			}
			textField.setForeground(okay ? UIManager.getColor("TextField.foreground") : Color.RED);
		}
		
		public JComponent makeUi() {
			final ETextField textField = new ETextField(options.get(key).toString()) {
				@Override
				public void textChanged() {
					actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
				}
			};
			return textField;
		}
	}
	
	/**
	 * color0 to color7 are the normal colors (black, red3, green3,
	 * yellow3, blue3, magenta3, cyan3, and gray90).
	 *
	 * color8 to color15 are the bold colors (gray30, red, green, yellow,
	 * blue, magenta, cyan, and white).
	 */
	private void initDefaultColors() {
		addDefault("color0", colorFromString("#000000"), "Color 0: black");
		addDefault("color1", colorFromString("#cd0000"), "Color 1: red3");
		addDefault("color2", colorFromString("#00cd00"), "Color 2: green3");
		addDefault("color3", colorFromString("#cdcd00"), "Color 3: yellow3");
		addDefault("color4", colorFromString("#0000cd"), "Color 4: blue3");
		addDefault("color5", colorFromString("#cd00cd"), "Color 5: magenta3");
		addDefault("color6", colorFromString("#00cdcd"), "Color 6: cyan3");
		addDefault("color7", colorFromString("#e5e5e5"), "Color 7: grey90");
		
		// If we supported 16 colors, they would be these bright versions of the colors above:
		// Color 8: gray30
		// Color 9: red
		// Color 10: green
		// Color 11: yellow
		// Color 12: blue
		// Color 13: magenta
		// Color 14: cyan
		// Color 15: white
		// Both the xterm-color nor rxvt-color terminfo entries just claim 8 colors.
		// There are xterm-16color and rxvt-16color variants, but I've not seen them used, and don't know of anything that would take advantage of the extra colors (which would require a significantly more complicated terminfo, and support for extra sequences).
		
		// Defaults reminiscent of SGI's xwsh(1).
		addDefault("background", colorFromString("#000045"), "Background"); // dark blue
		addDefault("colorBD", colorFromString("#ffffff"), "Bold color"); // white
		addDefault("cursorColor", colorFromString("#00ff00"), "Cursor color"); // green
		addDefault("foreground", colorFromString("#e7e7e7"), "Foreground"); // off-white
		addDefault("selectionColor", colorFromString("#1c2bff"), "Selection color"); // light blue
	}
	
	/**
	 * Tries to get a good bold foreground color. If the user has set their
	 * own, or they haven't set their own foreground, we don't need to do
	 * anything. Otherwise, we look through the normal (low intensity)
	 * colors to see if we can find a match. If we do, take the appropriate
	 * bold (high intensity) color for the bold foreground color.
	 */
	private void aliasColorBD() {
		if (getColor("colorBD") != null || getColor("foreground") == null) {
			return;
		}
		Color foreground = getColor("foreground");
		for (int i = 0; i < 8; ++i) {
			Color color = getColor("color" + i);
			if (foreground.equals(color)) {
				options.put("colorBD", options.get("color" + (i + 8)));
				return;
			}
		}
	}
	
	/**
	 * Returns the name of the first "rgb.txt" file it finds.
	 */
	private String findRgbDotTxt() {
		String[] possibleRgbDotTxtLocations = {
			"/usr/X11R6/lib/X11/rgb.txt", // Linux, Mac OS with X11 installed.
			"/usr/share/emacs/21.2/etc/rgb.txt", // Mac OS without X11 installed.
		};
		for (String possibleLocation : possibleRgbDotTxtLocations) {
			if (FileUtilities.exists(possibleLocation)) {
				return possibleLocation;
			}
		}
		return null;
	}
	
	private void readRGBFile(String rgbDotTxtFilename) {
		rgbColors = new HashMap<String, Color>();
		String[] lines = StringUtilities.readLinesFromFile(rgbDotTxtFilename);
		for (String line : lines) {
			if (line.length() == 0 || line.startsWith("!") || line.startsWith("#")) {
				// X11's "rgb.txt" uses !-commenting, but Emacs' copy uses #-commenting, and contains an empty line.
				continue;
			}
			int r = channelAt(line, 0);
			int g = channelAt(line, 4);
			int b = channelAt(line, 8);
			line = line.substring(12).trim();
			rgbColors.put(line.toLowerCase(), new Color(r, g, b));
		}
	}
	
	private Color getRgbColor(String description) {
		// FIXME: with Sun's JVM, com.sun.java.swing.plaf.gtk.XColors.lookupColor returns a Color for each color named in "rgb.txt". We should try that (via reflection) first, and only then resort to reading "rgb.txt".
		if (rgbColors == null) {
			final String filename = findRgbDotTxt();
			if (filename == null) {
				return null;
			}
			try {
				readRGBFile(filename);
			} catch (Exception ex) {
				Log.warn("Problem reading colors from \"" + filename + "\"", ex);
			}
		}
		return rgbColors.get(description.toLowerCase());
	}
	
	private int channelAt(String line, int offset) {
		return Integer.parseInt(line.substring(offset, offset + 3).trim());
	}
	
	private File getHomeFile(String filename) {
		return new File(System.getProperty("user.home"), filename);
	}
	
	private void readOptionsFrom(String filename) {
		File file = getHomeFile(filename);
		if (file.exists() == false) {
			return;
		}
		try {
			readOptionsFrom(file);
		} catch (Exception ex) {
			Log.warn("Problem reading options from \"" + filename + "\"", ex);
		}
	}
	
	private void readOptionsFrom(File file) {
		String[] lines = StringUtilities.readLinesFromFile(file.toString());
		for (String line : lines) {
			line = line.trim();
			if (line.length() == 0 || line.startsWith("!")) {
				continue;
			}
			processResourceString(line);
		}
	}
	
	private void processResourceString(String resourceString) {
		Matcher matcher = resourcePattern.matcher(resourceString);
		if (matcher.find()) {
			String key = matcher.group(1);
			String value = matcher.group(2);
			Object currentValue = options.get(key);
			if (currentValue == null) {
				throw new RuntimeException("Attempt to set unknown resource \"" + key + "\"");
			}
			Class currentClass = currentValue.getClass();
			if (currentClass == Boolean.class) {
				options.put(key, Boolean.valueOf(value));
			} else if (currentClass == Font.class) {
				options.put(key, makePrototypeFont(value));
			} else if (currentClass == Integer.class) {
				options.put(key, Integer.valueOf(value));
			} else if (currentClass == Color.class) {
				options.put(key, colorFromString(value));
			} else {
				throw new RuntimeException("Resource \"" + key + "\" had default value " + currentValue + " of class " + currentClass);
			}
		}
	}
}
