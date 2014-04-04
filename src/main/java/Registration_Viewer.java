import java.awt.Color;
import java.io.File;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.openspim.toolkit.OpenSPIMToolkit;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.io.FileInfo;
import ij.plugin.PlugIn;

public class Registration_Viewer implements PlugIn {

	public Registration_Viewer() {
	}

	@Override
	public void run(String arg0) {
		overlayRegistration(IJ.getImage());
	}

	public static void overlayRegistration(final ImagePlus imp) {
		FileInfo fi = imp.getOriginalFileInfo();

		final List<Vector3D> beads = OpenSPIMToolkit.loadBeadsSimple(new File(fi.directory, fi.fileName));

		ImagePlus.addImageListener(new ImageListener() {
			@Override
			public void imageClosed(ImagePlus arg0) {
				if (!arg0.equals(imp))
					return;

				ImagePlus.removeImageListener(this);
			}

			@Override
			public void imageOpened(ImagePlus arg0) {
			}

			@Override
			public void imageUpdated(ImagePlus arg0) {
				if (!arg0.equals(imp))
					return;

				Overlay o = new Overlay();
				o.setStrokeColor(Color.GREEN);

				for (Vector3D bead : beads) {
					if ((int) bead.getZ() != arg0.getSlice() - 1)
						continue;

					OvalRoi r = new OvalRoi(bead.getX() - 2, bead.getY() - 2, 4, 4);
					r.setStrokeColor(Color.GREEN);
					o.add(r);
				}

				arg0.setOverlay(o);
			}
		});
	}
}
