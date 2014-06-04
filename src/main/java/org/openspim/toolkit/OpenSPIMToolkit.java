package org.openspim.toolkit;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.openspim.gui.LayoutUtils;

import ij.IJ;
import ij.Macro;
import ij.plugin.PlugIn;
import ij.gui.GenericDialog;

public class OpenSPIMToolkit implements PlugIn {
	private static final String PATH_BOX_NAME = "pathBox";

	@Override
	public void run(String arg0) {
		IJ.showMessage("The SPIM registration toolkit is not meant to be run by its primary class. :(");
	}

	public static ActionListener smartPathBrowser = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent ae) {
			if(ae.getSource() == null || !(ae.getSource() instanceof Component))
				throw new UnsupportedOperationException("smartPathBrowser bound to a non-component.");

			Component src = (Component) ae.getSource();

			if(src.getParent() == null)
				throw new UnsupportedOperationException("smartPathBrowser bound to a top-level container?");

			Component[] cs = src.getParent().getComponents();
			JTextField pathBox = null;

			for(Component child : cs)
				if((child instanceof JTextField) && child.getName().equals(PATH_BOX_NAME))
					pathBox = (JTextField) child;

			if(pathBox == null)
				throw new UnsupportedOperationException("smartPathBrowser's source's name matches no hash codes in container.");

			JFileChooser chooser = new JFileChooser(pathBox.getText());
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if(chooser.showDialog(src, "Select") == JFileChooser.APPROVE_OPTION)
				pathBox.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	};

	public static JPanel directoryField(String current) {
		JButton browse = new JButton("Browse");
		JTextField pathBox = new JTextField(current, 32);
		pathBox.setName(PATH_BOX_NAME);
		browse.addActionListener(smartPathBrowser);

		return LayoutUtils.horizPanel(pathBox, browse);
	}
	
	public static String readDirectoryField(Component panel) {
		if(panel == null || !(panel instanceof JPanel))
			throw new IllegalArgumentException("readDirectoryField from what now?");

		for(Component child : ((JPanel) panel).getComponents())
			if((child instanceof JTextField) && child.getName().equals(PATH_BOX_NAME))
				return ((JTextField) child).getText();

		throw new IllegalArgumentException("readDirectoryField on container without a path box?");
	}

	public static enum RegField {
		PATH ("spim_data_directory", "Path:", ""),
		PATTERN ("pattern_of_spim", "Pattern:", "spim_TL{tt}_Angle{a}.tiff"),
		TIMEPOINTS ("timepoints_to_process", "Timepoints:", "1"),
		ANGLES ("angles_to_process", "Angles:", "0-5:1"),
		// Advanced... registration fields
		RADIUS1 ("channel_0_radius_1", "Bead Radius 1:", 1),
		RADIUS2 ("channel_0_radius_2", "Bead Radius 2:", 2),
		THRESHOLD ("channel_0_threshold", "Bead Threshold:", 0.01),
		PIXEL_SIZE_XY ("xy_resolution", "Pixel X/Y size (um):", 0.652),
		PIXEL_SIZE_Z ("z_resolution", "Pixel Z size (um):", 3);

		private String name, label;
		private Object value;

		private RegField(String name, String label, Object value) {
			this.name = name;
			this.label = label;
			this.value = value;
		}

		public String getLabel() {
			return label;
		}

		public String getName() {
			return name;
		}

		public Object getValue() {
			return value;
		}

		public String getStringValue() {
			return (value != null ? value.toString() : "(null)");
		}

		public double getDoubleValue() {
			return (value != null && value instanceof Number) ? ((Number) value).doubleValue() : Double.NaN;
		}

		public int getIntValue() {
			return (value != null && value instanceof Integer) ? (Integer) value : 0;
		}
		
		public String getMacroParameter() {
			if(value == null)
				return "";

			if(value instanceof Integer)
				return String.format("%s=%d", name, ((Integer) value).intValue());
			else if(value instanceof Double)
				return String.format("%s=%.6f", name, ((Double) value).doubleValue());
			else
				return String.format("%s=[%s]", name, value);
		}

		public void setValue(Object value) {
			this.value = value;
		}
		
		public static void readMacroOptions() {
			if(Macro.getOptions() == null)
				return;

			for(RegField val : values())
				val.setValue(Macro.getValue(Macro.getOptions(), val.getName(), val.getValue().toString()));
		}

		public static String getMacroOptions(RegField... vals) {
			StringBuilder sb = new StringBuilder();

			for(RegField val : vals)
				sb.append(val.getMacroParameter()).append(' ');

			return sb.substring(0, sb.length() - 1);
		}

		public static String getMacroOptions() {
			return getMacroOptions(values());
		}
	}

	public static Map<String, Component> createSpecifierFields() {
		Map<String, Component> ret = new LinkedHashMap<String, Component>();
		
		ret.put(RegField.PATH.getLabel(), directoryField(RegField.PATH.getValue().toString()));
		ret.put(RegField.PATTERN.getLabel(), new JTextField(RegField.PATTERN.getStringValue()));
		ret.put(RegField.TIMEPOINTS.getLabel(), new JTextField(RegField.TIMEPOINTS.getStringValue()));
		ret.put(RegField.ANGLES.getLabel(), new JTextField(RegField.ANGLES.getStringValue()));

		return ret;
	}

	public static SpimDataSpecifier readSpecifierFields(Map<String, Component> form) {
		RegField.PATH.setValue(readDirectoryField(form.get(RegField.PATH.getLabel())));
		RegField.PATTERN.setValue(((JTextField) form.get(RegField.PATTERN.getLabel())).getText());
		RegField.TIMEPOINTS.setValue(((JTextField) form.get(RegField.TIMEPOINTS.getLabel())).getText());
		RegField.ANGLES.setValue(((JTextField) form.get(RegField.ANGLES.getLabel())).getText());

		return getLastSpecifier();
	}

	public static Map<String, Component> createManualRegFields() {
		Map<String, Component> ret = new LinkedHashMap<String, Component>();

		ret.put(RegField.RADIUS1.getLabel(), new JSpinner(new SpinnerNumberModel(RegField.RADIUS1.getIntValue(), 1, 100, 1)));
		ret.put(RegField.RADIUS2.getLabel(), new JSpinner(new SpinnerNumberModel(RegField.RADIUS2.getIntValue(), 1, 100, 1)));
		ret.put(RegField.THRESHOLD.getLabel(), new JSpinner(new SpinnerNumberModel(RegField.THRESHOLD.getDoubleValue(), 1e-4, 1, 1e-3)));
		ret.put(RegField.PIXEL_SIZE_XY.getLabel(), new JSpinner(new SpinnerNumberModel(RegField.PIXEL_SIZE_XY.getDoubleValue(), 1e-4, 1000, 1e-2)));
		ret.put(RegField.PIXEL_SIZE_Z.getLabel(), new JSpinner(new SpinnerNumberModel(RegField.PIXEL_SIZE_Z.getDoubleValue(), 1e-4, 1000, 1e-2)));

		return ret;
	}

	public static void readManualRegFields(Map<String, Component> form) {
		RegField.RADIUS1.setValue(((JSpinner) form.get(RegField.RADIUS1.getLabel())).getValue());
		RegField.RADIUS2.setValue(((JSpinner) form.get(RegField.RADIUS2.getLabel())).getValue());
		RegField.THRESHOLD.setValue(((JSpinner) form.get(RegField.THRESHOLD.getLabel())).getValue());
		RegField.PIXEL_SIZE_XY.setValue(((JSpinner) form.get(RegField.PIXEL_SIZE_XY.getLabel())).getValue());
		RegField.PIXEL_SIZE_Z.setValue(((JSpinner) form.get(RegField.PIXEL_SIZE_Z.getLabel())).getValue());
	}

	public static void createSpecifierFields(GenericDialog gd) {
		gd.addStringField("Path", RegField.PATH.getStringValue());
		gd.addStringField("Pattern", RegField.PATTERN.getStringValue());
		gd.addStringField("Timepoints", RegField.TIMEPOINTS.getStringValue());
		gd.addStringField("Angles", RegField.ANGLES.getStringValue());
	}

	public static SpimDataSpecifier readSpecifierFields(GenericDialog gd) {
		RegField.PATH.setValue(gd.getNextString());
		RegField.PATTERN.setValue(gd.getNextString());
		RegField.TIMEPOINTS.setValue(gd.getNextString());
		RegField.ANGLES.setValue(gd.getNextString());

		return getLastSpecifier();
	}

	public static SpimDataSpecifier getLastSpecifier() {
		return new SpimDataSpecifier(new File(RegField.PATH.getStringValue()), RegField.PATTERN.getStringValue(),
				RegField.TIMEPOINTS.getStringValue(), RegField.ANGLES.getStringValue());
	}

	public static void createManualRegFields(GenericDialog gd) {
		gd.addNumericField("Radius1", RegField.RADIUS1.getIntValue(), 0, 4, "px");
		gd.addNumericField("Radius2", RegField.RADIUS2.getIntValue(), 0, 4, "px");
		gd.addNumericField("Threshold", RegField.THRESHOLD.getDoubleValue(), 6, 12, "");
		gd.addNumericField("VoxelSizeXY", RegField.PIXEL_SIZE_XY.getDoubleValue(), 6, 12, "um/pix");
		gd.addNumericField("VoxelSizeZ", RegField.PIXEL_SIZE_Z.getDoubleValue(), 6, 12, "um/pix");
	}

	public static void readManualRegFields(GenericDialog gd) {
		RegField.RADIUS1.setValue((int) gd.getNextNumber());
		RegField.RADIUS2.setValue((int) gd.getNextNumber());
		RegField.THRESHOLD.setValue(gd.getNextNumber());
		RegField.PIXEL_SIZE_XY.setValue(gd.getNextNumber());
		RegField.PIXEL_SIZE_Z.setValue(gd.getNextNumber());
	}

	public static String getManualRegParameterString() {
		return String.format("bead_brightness=[Advanced ...] %s %s %s specify_calibration_manually %s %s",
				RegField.RADIUS1.getMacroParameter(), RegField.RADIUS2.getMacroParameter(), RegField.THRESHOLD.getMacroParameter(),
				RegField.PIXEL_SIZE_XY.getMacroParameter(), RegField.PIXEL_SIZE_Z.getMacroParameter());
	}

	public static String getInteractiveParameterString() {
		return String.format("bead_brightness=[Interactive ...] %s %s %s specify_calibration_manually %s %s",
				RegField.RADIUS1.getMacroParameter(), RegField.RADIUS2.getMacroParameter(), RegField.THRESHOLD.getMacroParameter(),
				RegField.PIXEL_SIZE_XY.getMacroParameter(), RegField.PIXEL_SIZE_Z.getMacroParameter());
	}

	/**
	 * Gets the parameters for a simple single-channel registration. Currently one-timepoint only.
	 * 
	 * @param model one of 'Affine', 'Translation', or 'Rigid'.
	 * @return a string to be concatenated with the remaining parameters for a registration
	 */
	public static String get1ChannelDoMSimple(String model) {
		return "select_type_of_registration=Single-channel select_type_of_detection=[Difference-of-Mean (Integral image based)] subpixel_localization=[3-dimensional quadratic fit (all detections)] transformation_model="
				+ model;
	}

	public static List<Vector3D> loadBeadsSimple(File viewpath) {
		File beads = new File(getRegistrationDirectory(viewpath), viewpath.getName() + ".beads.txt");

		if (!beads.exists())
			return null;

		List<String> lines;

		try {
			lines = readAllLines(beads);
		} catch (IOException e) {
			return null;
		}

		Iterator<String> iter = lines.iterator();
		iter.next();

		List<Vector3D> beadList = new LinkedList<Vector3D>();

		while (iter.hasNext()) {
			Scanner line = new Scanner(iter.next());

			/* int beadId = */line.nextInt();
			/* int viewId = */line.nextInt();
			beadList.add(new Vector3D(line.nextDouble(), line.nextDouble(), line.nextDouble()));
			// and we don't care about the rest of the line.
			
			line.close();
		}

		return beadList;
	}

	public static File getRegistrationDirectory(File viewpath) {
		return new File(viewpath.getParent(), "registration");
	}
	
	public static File getOutputDirectory(File viewpath) {
		return new File(viewpath.getParent(), "output");
	}

	public static List<String> readAllLines(File path) throws IOException {
		List<String> out = new LinkedList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(path));

		while (reader.ready())
			out.add(reader.readLine());

		reader.close();

		return out;
	}

	public static void writeAllLines(File path, Iterable<String> lines) throws IOException {
		PrintWriter writer = new PrintWriter(new FileWriter(path));

		for (String line : lines)
			writer.println(line);

		writer.close();
	}
};