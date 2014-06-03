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

		File outputFile = new File(OpenSPIMToolkit.getRegistrationDirectory(view), view.getName() + ".beads.txt");
		File oldBeadsFile = new File(OpenSPIMToolkit.getRegistrationDirectory(view), view.getName() + ".beads.unfiltered.txt");

		if(oldBeadsFile.exists() && !oldBeadsFile.delete())
		{
			IJ.log("Couldn't remove old registration file backup for view " + view.getName() + "");
			return;
		}

		if(!outputFile.renameTo(oldBeadsFile))
		{
			IJ.log("Couldn't backup unfiltered beads for view " + view.getName() + "");
			return;
		}

		int n = 0, kept = 0;
		try
		{
			BufferedReader readIn = new BufferedReader(new FileReader(oldBeadsFile));
			BufferedWriter writeOut = new BufferedWriter(new FileWriter(outputFile));

			writeOut.write(readIn.readLine()); // Header line
			writeOut.newLine();

			String line = null;
			while((line = readIn.readLine()) != null)
			{
				Scanner lineScanner = new Scanner(line);

				/*int beadId =*/ lineScanner.nextInt();
				/*int viewId =*/ lineScanner.nextInt();
				Vector3D bead = new Vector3D(lineScanner.nextDouble(), lineScanner.nextDouble(), lineScanner.nextDouble());

				if(processBead(view, bead))
				{
					++kept;
					writeOut.write(line);
					writeOut.newLine();
				}

				++n; reportProgress(view, (float)(n*n) / ((float)(n*(n + 4.0f)))); // Eh.
				lineScanner.close();
			}

			readIn.close();
			writeOut.close();
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

		IJ.log("Bead processor: Processed " + n + " beads; remaining: " + kept);
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
