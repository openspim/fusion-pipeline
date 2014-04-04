package org.openspim.toolkit.adapters;

import java.awt.Component;
import java.util.concurrent.ExecutionException;

import javax.swing.JComboBox;

import ij.IJ;

import org.openspim.gui.LayoutUtils;
import org.openspim.toolkit.Processor;

public class DefaultSPIMRegistration implements Processor {

	@Override
	public String toString() {
		return "SPIM Registration Plugin";
	}
	
	@Override
	public int getCapabilities() {
		return Processor.REGISTRATION | Processor.SEGMENTATION;
	}

	private static JComboBox mode = new JComboBox(new String[] {"Difference-of-Mean (Integral image based)", "Difference-of-Gaussian"});
	
	@Override
	public Component getControlPanel() {
		return LayoutUtils.form("Detection method:", mode);
	}
	
	@Override
	public void performProcessing(final Params params) throws InterruptedException, ExecutionException {
		params.invokeOn.submit(new Runnable() {
			@Override
			public void run() {
				IJ.log("--- Begin SPIM Registration Plugin Invocation ---");
				IJ.run("Bead-based registration",
					"select_type_of_registration=Single-channel " +
					"select_type_of_detection=[" + mode.getSelectedItem().toString() + "] " +
					"subpixel_localization=[3-dimensional quadratic fit (all detections)] " +
					"transformation_model=[" + (params.stage == Processor.Stage.SEGMENTATION ? "Translation" : "Affine") + "] " +
					"bead_brightness=[Advanced ...] " +
					"channel_0_radius_1=" + params.beadRadius1 + " " +
					"channel_0_radius_2=" + params.beadRadius2 + " " +
					"channel_0_threshold=" + params.threshold + " " +
					"specify_calibration_manually " +
					"xy_resolution=" + params.xyUmPerPix + " " +
					"z_resolution=" + params.zUmPerPix + " " +
					(params.stage == Processor.Stage.REGISTRATION ? "load_segmented_beads " : "") +
					params.spec.getParameterString()
				);		
				IJ.log("--- End SPIM Registration Plugin Invocation ---");
			}
		}).get();
	}

}
