package org.openspim.toolkit;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatBlitter;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

import javax.swing.JCheckBox;

import org.apache.commons.math3.geometry.euclidean.threed.PolyhedronsSet;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.openspim.gui.LayoutUtils;
import org.python.antlr.PythonParser.attr_return;

public class TriViewMultiProcessor {
	private static final double SCALE = 0.75;
	private static Map<File, ImagePlus> hullMap = new Hashtable<File, ImagePlus>();

	protected static class TriView implements ImageListener {
		private double zperxy;
		private ImagePlus xy, xz, zy;

		private volatile boolean done;

		private ImageStack procStack;
		private final int depth;

		public TriView(double zperxy, File f) {
			this.zperxy = zperxy;
			
			ImagePlus src = IJ.openImage(f.getAbsolutePath());
			
			FloatProcessor ixy, ixz, izy, slice = null;
			ixy = new FloatProcessor(src.getWidth(), src.getHeight());
			ixy.and(0);
			ixz = new FloatProcessor(src.getWidth(), src.getStackSize());
			izy = new FloatProcessor(src.getStackSize(), src.getHeight());
			float colavgs[] = new float[src.getWidth()];

			for(int z = 1; z <= src.getStackSize(); ++z) {
				slice = src.getStack().getProcessor(z).toFloat(0, slice);
				ixy.copyBits(slice, 0, 0, FloatBlitter.ADD);

				for(int y = 0; y < slice.getHeight(); ++y) {
					float stripavg = 0;

					for(int x = 0; x < slice.getWidth(); ++x) {
						float val = slice.getf(x, y);

						stripavg += val;

						if(y == 0)
							colavgs[x] = val;
						else
							colavgs[x] += val;
					}

					izy.setf(z - 1, y, stripavg / (float)slice.getWidth());
				}

				for(int x = 0; x < slice.getWidth(); ++x)
					ixz.setf(x, z - 1, colavgs[x] / (float)slice.getWidth());
			}

			ixy.multiply(1 / (double)src.getStackSize());
			ixy = (FloatProcessor) ixy.resize((int) (ixy.getWidth() * SCALE), (int) (ixy.getHeight() * SCALE));
			ixz = (FloatProcessor) ixz.resize((int) (ixz.getWidth() * SCALE), (int) (ixz.getHeight() * zperxy * SCALE));
			izy = (FloatProcessor) izy.resize((int) (izy.getWidth() * zperxy * SCALE), (int) (izy.getHeight() * SCALE));

			xy = new ImagePlus(f.getName() + " - X/Y", ixy);
			xz = new ImagePlus(f.getName() + " - X/Z", ixz);
			zy = new ImagePlus(f.getName() + " - Z/Y", izy);

			ImagePlus.addImageListener(this);

			procStack = new ImageStack(src.getWidth(), src.getHeight());
			this.depth = src.getStackSize();

			src.close();
		}

		public ImageStack show() {
			xy.show(); xz.show(); zy.show();

			Dimension ss = Toolkit.getDefaultToolkit().getScreenSize();
			Point xyp = xy.getWindow().getLocationOnScreen();

			xy.getWindow().setLocation(
				Math.max(0, Math.min(xyp.x, ss.width - xy.getWindow().getWidth() - zy.getWindow().getWidth())),
				Math.max(32, Math.min(xyp.y, ss.height - xy.getWindow().getHeight() - xz.getWindow().getHeight() - 32))
			);

			xyp = xy.getWindow().getLocationOnScreen();

			xz.getWindow().setLocation(
				xyp.x,
				xyp.y + xy.getWindow().getHeight()
			);

			zy.getWindow().setLocation(
				xyp.x + xy.getWindow().getWidth(),
				xyp.y
			);

			done = false;
			while(!done) try {
				Thread.sleep(50);
			} catch(InterruptedException ie) {
				break;
			}

			return procStack;
		}

		private static Roi scaleRoi(final Roi in, final double scaleX, final double scaleY)
		{
			final FloatPolygon inpoly = in.getFloatPolygon();
			float[] x = new float[inpoly.npoints];
			float[] y = new float[inpoly.npoints];

			for(int i=0; i < inpoly.npoints; ++i)
			{
				x[i] = (float) (inpoly.xpoints[i]*scaleX);
				y[i] = (float) (inpoly.ypoints[i]*scaleY);
			}

			return new PolygonRoi(x, y, inpoly.npoints, PolygonRoi.POLYGON);
		}

		@Override
		public void imageClosed(ImagePlus img) {
			if(!done && (xy.equals(img) || xz.equals(img) || zy.equals(img))) {
				ImagePlus.removeImageListener(this);

				Roi xyr = scaleRoi(xy.getRoi(), 1/SCALE, 1/SCALE);
				Roi xzr = scaleRoi(xz.getRoi(), 1/SCALE, 1/(SCALE*zperxy));
				Roi zyr = scaleRoi(zy.getRoi(), 1/(SCALE*zperxy), 1/SCALE);

				xy.close();
				xz.close();
				zy.close();

				int width = procStack.getWidth();
				int height = procStack.getHeight();

				ImageProcessor xybase = new ByteProcessor(width, height);
				xybase.setColor(0);
				xybase.fill();
				xybase.setColor(1);
				xybase.fill(xyr);

				for(int z = 1; z <= depth; ++z) {
					ImageProcessor slice = xybase.duplicate();
					slice.setColor(0);

					for(int y = 0; y < height; ++y)
						for(int x = 0; x < width; ++x)
							if(!xzr.contains(x, z - 1) || !zyr.contains(z - 1, y))
								slice.set(x, y, 0);

					procStack.addSlice(slice);
				}

				done = true;
			}
		}

		@Override
		public void imageOpened(ImagePlus arg0) {}

		@Override
		public void imageUpdated(ImagePlus arg0) {}
	}

	private static File hullFilePath(File view) {
		return new File(OpenSPIMToolkit.getRegistrationDirectory(view), view.getName() + ".3vmp.hull.tiff");
	}

	private static boolean loadHullForView(File view) {
		File hull = hullFilePath(view);

		if(hull.exists())
			hullMap.put(view, IJ.openImage(hull.getAbsolutePath()));

		return hull.exists();
	}

	private static void saveHullForView(File view) {
		ImagePlus hull = hullMap.get(view);
		File path = hullFilePath(view);

		IJ.saveAsTiff(hull, path.getAbsolutePath());
	}

	public static class SampleExcluder extends BeadProcessor {
		@Override
		public void beginView(Params par, File f) {
			if(leave.isSelected() && hullMap.containsKey(f))
				return;
			else if(load.isSelected() && loadHullForView(f))
				return;
			else
				hullMap.put(f, new ImagePlus("", new TriView(par.zUmPerPix / par.xyUmPerPix, f).show()));
		}

		@Override
		public boolean processBead(File view, Vector3D at) {
			return hullMap.get(view).getStack().getProcessor((int) at.getZ() + 1).get((int) at.getX(), (int) at.getY()) == 0;
		}

		@Override
		public void endView(File view) {
			if(save.isSelected())
				saveHullForView(view);

			if(!leave.isSelected()) {
				hullMap.get(view).close();
				hullMap.remove(view);
			}
		}

		@Override
		public String toString() {
			return "3-View Sample Exclusion";
		}

		@Override
		public int getCapabilities()
		{
			return Processor.POST_SEGMENTATION;
		}

		@Override
		public Component getControlPanel() {
			return TriViewMultiProcessor.getControlPanel();
		}
	}

	public static class SampleIsolator extends ViewProcessor {
		// This is where Java falls flat on its face.
		private void nullifierLoopByte(ImagePlus imp, ImagePlus hull) {
			int w = imp.getWidth();
			int h = imp.getHeight();
			int d = imp.getStackSize();
			ImageStack stck = imp.getStack();
			ImageStack hstck = hull.getStack();

			for(int z = 1; z <= d; ++z) {
				byte[] pixels = (byte[]) stck.getPixels(z);
				byte[] hullpix = (byte[]) hstck.getPixels(z);

				for(int y=0; y < h; ++y)
					for(int x = 0; x < w; ++x)
						pixels[x + y*w] &= hullpix[x + y*w];

				stck.setPixels(pixels, z);
			}
		}

		private void nullifierLoopShort(ImagePlus imp, ImagePlus hull) {
			int w = imp.getWidth();
			int h = imp.getHeight();
			int d = imp.getStackSize();
			ImageStack stck = imp.getStack();
			ImageStack hstck = hull.getStack();

			for(int z = 1; z <= d; ++z) {
				short[] pixels = (short[]) stck.getPixels(z);
				byte[] hullpix = (byte[]) hstck.getPixels(z);

				for(int y=0; y < h; ++y)
					for(int x = 0; x < w; ++x)
						pixels[x + y*w] *= (hullpix[x + y*w] & 1);

				stck.setPixels(pixels, z);
			}
		}

		private void nullifierLoopInt(ImagePlus imp, ImagePlus hull) {
			int w = imp.getWidth();
			int h = imp.getHeight();
			int d = imp.getStackSize();
			ImageStack stck = imp.getStack();
			ImageStack hstck = hull.getStack();

			for(int z = 1; z <= d; ++z) {
				int[] pixels = (int[]) stck.getPixels(z);
				byte[] hullpix = (byte[]) hstck.getPixels(z);

				for(int y=0; y < h; ++y)
					for(int x = 0; x < w; ++x)
						pixels[x + y*w] *= (hullpix[x + y*w] & 1);

				stck.setPixels(pixels, z);
			}
		}

		private void nullifierLoopFloat(ImagePlus imp, ImagePlus hull) {
			int w = imp.getWidth();
			int h = imp.getHeight();
			int d = imp.getStackSize();
			ImageStack stck = imp.getStack();
			ImageStack hstck = hull.getStack();

			for(int z = 1; z <= d; ++z) {
				float[] pixels = (float[]) stck.getPixels(z);
				byte[] hullpix = (byte[]) hstck.getPixels(z);

				for(int y=0; y < h; ++y)
					for(int x = 0; x < w; ++x)
						pixels[x + y*w] *= (hullpix[x + y*w] & 1);

				stck.setPixels(pixels, z);
			}
		}

		@Override
		public void processView(Params par, File view) {
			if(leave.isSelected() && hullMap.containsKey(view))
				;
			else if(load.isSelected() && loadHullForView(view))
				;
			else
				hullMap.put(view, new ImagePlus("", new TriView(par.zUmPerPix / par.xyUmPerPix, view).show()));

			ImagePlus hull = hullMap.get(view);

			ImagePlus imp = IJ.openImage(view.getAbsolutePath());

			switch(imp.getType()) {
			case ImagePlus.GRAY8:
				nullifierLoopByte(imp, hull);
				break;
			case ImagePlus.GRAY16:
				nullifierLoopShort(imp, hull);
				break;
			case ImagePlus.GRAY32:
				nullifierLoopFloat(imp, hull);
				break;
			default:
				nullifierLoopInt(imp, hull);
				break;
			}

			IJ.save(imp, view.getAbsolutePath());
			imp.close();

			hullMap.get(view).close();
			hullMap.remove(view);
		}

		@Override
		public String toString() {
			return "3-View Sample Isolator";
		}

		@Override
		public int getCapabilities()
		{
			return Processor.POST_REGISTRATION;
		}

		@Override
		public Component getControlPanel() {
			return TriViewMultiProcessor.getControlPanel();
		}
	}

	private static JCheckBox save = new JCheckBox("", true);
	private static JCheckBox load = new JCheckBox("", true);
	private static JCheckBox leave = new JCheckBox("", false);

	static {
		save.setToolTipText("If checked, the selected regions of each image will be saved to disk for later reuse.");
		load.setToolTipText("If checked, saved regions will be loaded instead of consulting the user.");
		leave.setToolTipText("If checked, cached selections will be used instead of loading from disk or consulting the user. (This option consumes extra memory.)");
	}

	public static Component getControlPanel() {
		return LayoutUtils.form(
			"Save selections:", save,
			"Load saved selections:", load,
			"Leave selections in cache:", leave
		);
	}
}
