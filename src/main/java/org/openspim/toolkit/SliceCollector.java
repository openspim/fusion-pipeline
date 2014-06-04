package org.openspim.toolkit;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.openspim.gui.LayoutUtils;

public class SliceCollector implements Processor {
	@Override
	public int getCapabilities() {
		return Processor.POST_FUSION;
	}

	@Override
	public void performProcessing(Params params) throws InterruptedException, ExecutionException {
		File exemplar = params.spec.iterator().next();
		File[] files = OpenSPIMToolkit.getOutputDirectory(exemplar).listFiles(tifFilter);
		Arrays.sort(files);

		ImageStack stack = null;

		for (File f : files) {
			ImagePlus imp = IJ.openImage(f.getAbsolutePath());

			if(stack == null)
				stack = new ImageStack(imp.getWidth(), imp.getHeight());

			stack.addSlice(imp.getProcessor());
			imp.close();
		}

		ImagePlus total = new ImagePlus("collected-output", stack);
		if(!pathBox.getText().isEmpty())
			IJ.saveAsTiff(total, pathBox.getText());

		if(show.isSelected())
			total.show();
		else
			total.close();
	}

	@Override
	public String toString() {
		return "Slice Collector";
	}

	private static FilenameFilter tifFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			return name.endsWith(".tif") || name.endsWith(".tiff");
		}
	};

	private static JTextField pathBox = new JTextField("collected-output.tif", 24);
	private static JButton browseBtn = new JButton("Browse");
	private static JFileChooser chooser = new JFileChooser();
	private static JCheckBox show = new JCheckBox();

	static {
		chooser.addChoosableFileFilter(new FileNameExtensionFilter("Tagged Image File Format", "tif", "tiff"));
		chooser.setAcceptAllFileFilterUsed(true);

		browseBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if(chooser.showSaveDialog(browseBtn) != JFileChooser.APPROVE_OPTION)
					return;

				pathBox.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
	}

	@Override
	public Component getControlPanel() {
		return LayoutUtils.form(
			"Save Path:", LayoutUtils.horizPanel(pathBox, browseBtn),
			"Show Final Image:", show
		);
	}
}
