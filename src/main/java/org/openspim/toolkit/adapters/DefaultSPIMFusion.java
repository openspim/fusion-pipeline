package org.openspim.toolkit.adapters;

import ij.IJ;

import java.awt.Component;
import java.util.concurrent.ExecutionException;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.openspim.gui.LayoutUtils;
import org.openspim.toolkit.Processor;

public class DefaultSPIMFusion implements Processor {
	@Override
	public void performProcessing(final Params params) throws InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		/*
		 * IJ.run("Multi-view fusion", "select_channel=Single-channel registration=[Individual registration of channel 0]
		 * fusion_method=[Fuse into a single image] process_views_in_paralell=All blending downsample_output=4
		 * crop_output_image_offset_x=0 crop_output_image_offset_y=0 crop_output_image_offset_z=0
		 * crop_output_image_size_x=0 crop_output_image_size_y=0 crop_output_image_size_z=0 content_based_weights_(fast
		 * fused_image_output=[Save 2d-slices, all in one directory]");
		 */
		params.invokeOn.submit(new Runnable() {
			@Override
			public void run() {
				IJ.log("--- Begin SPIM Fusion Plugin Invocation ---");
				IJ.run("Multi-view fusion",
					"select_channel=Single-channel " +
					"registration=[Individual registration of channel 0] " +
					"fusion_method=[Fuse into a single image] " +
					"process_views_in_parallel=" + (multithread.isSelected() ? "All " : "1 ") +
					(blending.isSelected() ? "blending " : "") +
					(cbw.isSelected() ? "content_basted_weights_(fast " : "") +
					"downsample_output=" + ((Number)downsample.getValue()).intValue() + " " +
					"fused_image_output=[Save 2d-slices, all in one directory] " +
					params.spec.getParameterString()
				);
				IJ.log("--- End SPIM Fusion Plugin Invocation ---");
			}
		}).get();
	}
	
	@Override
	public String toString() {
		return "SPIM Fusion Plugin (Use SPIM Registration Plugin!)";
	}

	@Override
	public int getCapabilities() {
		return Processor.FUSION;
	}
	
	private static JCheckBox multithread = new JCheckBox("", true), blending = new JCheckBox("", true), cbw = new JCheckBox("", true);
	private static JSpinner downsample = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));

	@Override
	public Component getControlPanel() {
		return LayoutUtils.form(
			"Multithread:", multithread,
			"Blending:", blending,
			"Content-Based Weights (Approximate):", cbw,
			"Downsample Factor:", downsample
		);
	}
}
