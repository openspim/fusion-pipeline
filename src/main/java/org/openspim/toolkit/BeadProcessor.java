package org.openspim.toolkit;

import ij.IJ;

import java.io.File;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public abstract class BeadProcessor extends ViewProcessor {
	
	@Override
	public void processView(File view)
	{
		// Important note: ViewProcessor already wraps this method in a thread!
		beginView(view);

		List<Vector3D> beads = OpenSPIMToolkit.loadBeadsSimple(view);

		if(beads == null)
		{
			IJ.log("Couldn't read beads for view " + view.getName() + "");
			return;
		}

		int n = 0;
		for(Vector3D bead : beads)
		{
			processBead(bead);
			reportProgress(view, (float)n / (float)beads.size());
		}
		
		endView();
	}
	
	@Override
	public int getCapabilities()
	{
		return Processor.POST_SEGMENTATION | Processor.POST_REGISTRATION | Processor.POST_FUSION;
	}

	public abstract void beginView(File view);
	public abstract void processBead(Vector3D at);
	public abstract void endView();
}
