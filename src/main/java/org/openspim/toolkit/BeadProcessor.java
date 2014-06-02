package org.openspim.toolkit;

import ij.IJ;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public abstract class BeadProcessor extends ViewProcessor {
	@Override
	public void processView(Params par, File view)
	{
		// Important note: ViewProcessor already wraps this method in a thread!
		beginView(par, view);

		File beads = new File(OpenSPIMToolkit.getRegistrationDirectory(view), view.getName() + ".beads.txt");
		beads.renameTo(new File(beads.getParentFile(), view.getName() + ".beads.unfiltered.txt"));

		File outputFile = new File(OpenSPIMToolkit.getRegistrationDirectory(view), view.getName() + ".beads.txt");

		try
		{
			if(!beads.exists() || !outputFile.createNewFile())
			{
				IJ.log("Couldn't read beads for view " + view.getName() + "");
				return;
			}

			BufferedReader lines = new BufferedReader(new FileReader(beads));
			BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));

			out.write(lines.readLine()); // Header line

			int n = 0;
			String line = null;
			while((line = lines.readLine()) != null)
			{
				Scanner lineScanner = new Scanner(line);

				/*int beadId =*/ lineScanner.nextInt();
				/*int viewId =*/ lineScanner.nextInt();
				Vector3D bead = new Vector3D(lineScanner.nextDouble(), lineScanner.nextDouble(), lineScanner.nextDouble());

				if(processBead(view, bead))
					out.write(lines.readLine());

				reportProgress(view, (float)n / ((float)n + 1.0f)); ++n;
				lineScanner.close();
			}

			lines.close();
			out.close();
		}
		catch(FileNotFoundException fnfe)
		{
			ij.IJ.handleException(fnfe);
		}
		catch(IOException ioe)
		{
			ij.IJ.handleException(ioe);
		}

		endView(view);
	}

	@Override
	public int getCapabilities()
	{
		return Processor.POST_SEGMENTATION | Processor.POST_REGISTRATION | Processor.POST_FUSION;
	}

	public abstract void beginView(Params par, File view);
	public abstract boolean processBead(File view, Vector3D at);
	public abstract void endView(File view);
}
