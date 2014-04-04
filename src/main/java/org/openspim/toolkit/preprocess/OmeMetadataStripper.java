package org.openspim.toolkit.preprocess;

import ij.IJ;
import ij.ImagePlus;

import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import javax.swing.JCheckBox;

import org.openspim.gui.LayoutUtils;
import org.openspim.toolkit.Processor;
import org.openspim.toolkit.ViewProcessor;

public class OmeMetadataStripper extends ViewProcessor {
	private static abstract class WBCWrapper implements WritableByteChannel
	{
		private WritableByteChannel wbc;
		private long wroteSoFar, maxExpected;
		
		public WBCWrapper(WritableByteChannel wbc, long max)
		{
			this.wbc = wbc;
			maxExpected = max;
			wroteSoFar = 0;
		}

		@Override
		public void close() throws IOException {
			wbc.close();
		}

		@Override
		public boolean isOpen() {
			return wbc.isOpen();
		}

		@Override
		public int write(ByteBuffer arg0) throws IOException {
			int wrote = wbc.write(arg0);
			wroteSoFar += wrote;
			wroteBytes(wroteSoFar, maxExpected);
			return wrote;
		}
		
		public abstract void wroteBytes(long sofar, long max);
	}
	
	private boolean copy(final File from, final File to)
	{
		FileChannel input = null;
		WritableByteChannel output = null;
		
		try {
			input = new FileInputStream(from).getChannel();

			output = new WBCWrapper(new FileOutputStream(to).getChannel(), input.size())
			{
				@Override
				public void wroteBytes(long sofar, long max) {
					reportProgress(from, 0.5f * (float)sofar / (float)max);
				}
			};

			return input.transferTo(0, input.size(), output) == input.size();
		} catch(IOException ioe) {
			return false;
		} finally {
			try {
				input.close();
				output.close();
			} catch(IOException ioe) {
				// done our due diligence; give up.
			}
		}
	}
	
	private static boolean write(String text, File to)
	{
		PrintWriter output = null;
		
		try {
			output = new PrintWriter(to);
			output.print(text);
		} catch(IOException ioe) {
			return false;
		} finally {
			output.close();
		}
		
		return true;
	}
	
	@Override
	public void processView(File view) {
		if(backup.isSelected() && autoskip.isSelected() && (new File(new File(view.getParentFile(), "backup"), view.getName()).exists()))
		{
			IJ.log("Backup of view " + view.getName() + " already exists; skipping.");
			reportProgress(view, 1.0f);
			return;
		}
		
		ImagePlus img = IJ.openImage(view.getAbsolutePath());
		reportProgress(view, backup.isSelected() ? 0.33f : 0.5f);
		
		if(backup.isSelected())
		{
			File odir = new File(view.getParentFile(), "backup");
			if(!odir.exists())
				odir.mkdir();
			
			copy(view, new File(odir, view.getName()));
			
			IJ.log("Backed up view " + view.getName() + ".");
			reportProgress(view, 0.66f);
		}
		
		if(save.isSelected() && !(new File(view.getParent(), "metadata.xml").exists()))
		{
			write((String)img.getProperty("Info"), new File(view.getParent(), "metadata.xml"));
			IJ.log("Exported metadata.xml from view " + view.getName() + ". (Other views should be identical.)");
		}
		
		IJ.saveAs(img, "tiff", view.getAbsolutePath());
		
		IJ.log("Rewrote view " + view.getName() + " without(?) OME-XML metadata.");
		reportProgress(view, 1.0f);
	}

	private static JCheckBox backup = new JCheckBox("", true), autoskip = new JCheckBox("", true), save = new JCheckBox();
	
	@Override
	public Component getControlPanel() {
		return LayoutUtils.form(
			"Backup Originals:", backup,
			"Skip if Backup Exists:", autoskip,
			"Export Metadata:", save
		);
	}

	@Override
	public String toString() {
		return "OME-TIFF Metadata Stripper";
	}

	@Override
	public int getCapabilities() {
		return Processor.PRE_SEGMENTATION;
	}
}
