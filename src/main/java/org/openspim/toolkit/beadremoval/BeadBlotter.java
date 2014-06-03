package org.openspim.toolkit.beadremoval;

import ij.IJ;
import ij.ImagePlus;

import java.awt.Component;
import java.io.File;

import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.openspim.gui.LayoutUtils;
import org.openspim.toolkit.BeadProcessor;
import org.openspim.toolkit.Processor;

public class BeadBlotter extends BeadProcessor
{
	public BeadBlotter()
	{
	}

	private ImagePlus currentImage;
	private String currentViewPath;
	private double zperxy;

	@Override
	public void beginView(Params par, File view)
	{
		zperxy = par.zUmPerPix / par.xyUmPerPix;
		currentImage = IJ.openImage(currentViewPath = view.getAbsolutePath());
	}

	@Override
	public boolean processBead(File view, Vector3D bead)
	{
		double s = ((Number)sigmaSpinner.getValue()).doubleValue();
		int stride = currentImage.getWidth();
		BlotMode mode = (BlotMode)modeCombo.getSelectedItem();
		double min = ((Number)minSpinner.getValue()).doubleValue();
		double max = ((Number)maxSpinner.getValue()).doubleValue();
		double ds = s*dsSlider.getValue();

		for(int z = Math.max((int)(bead.getZ() - ds*zperxy), 1); z <= Math.min((int)(bead.getZ() + ds*zperxy), currentImage.getStackSize()); ++z)
		{
			Object pix = currentImage.getStack().getProcessor(z).getPixels();

			for(int y = Math.max((int)(bead.getY() - ds), 0); y <= Math.min((int)(bead.getY() + ds), currentImage.getHeight() - 1); ++y)
			{
				for(int x = Math.max((int)(bead.getX() - ds), 0); x <= Math.min((int)(bead.getX() + ds), stride - 1); ++x)
				{
					switch(currentImage.getType())
					{
					case ImagePlus.GRAY8:
						((byte[])pix)[y*stride + x] *= Math.min(Math.max(mode.attenuatePixel(new Vector3D(x, y, z), bead, s), min), max);
						break;
					case ImagePlus.GRAY16:
						((short[])pix)[y*stride + x] *= Math.min(Math.max(mode.attenuatePixel(new Vector3D(x, y, z), bead, s), min), max);
						break;
					case ImagePlus.GRAY32:
						((float[])pix)[y*stride + x] *= Math.min(Math.max(mode.attenuatePixel(new Vector3D(x, y, z), bead, s), min), max);
						break;
					default:
						throw new Error("Unsupported image type (" + currentImage.getType() + ")!");
					};
				}
			}
		}
		
		return true;
	}
	
	@Override
	public void endView(File view)
	{
		IJ.saveAs(currentImage, "Tiff", currentViewPath);
		currentImage.close();
	}

	private static interface FillFunction
	{
		public abstract double value(Vector3D at, Vector3D center, double sigma);
	};

	private static FillFunction flatSphere = new FillFunction()
	{
		@Override
		public double value(Vector3D at, Vector3D center, double sigma)
		{
			return (at.distanceSq(center) < sigma*sigma ? 0 : 1);
		}
	};
	
	private static FillFunction fadeSphere = new FillFunction()
	{
		@Override
		public double value(Vector3D at, Vector3D center, double sigma)
		{
			return Math.sqrt(at.distanceSq(center) / (sigma*sigma));
		}
	};

	private static double SQRT2PI = Math.sqrt(2*Math.PI);

	private static FillFunction gaussSpherePSF = new FillFunction()
	{
		@Override
		public double value(Vector3D at, Vector3D center, double sigma)
		{
			double dx = at.getX() - center.getX();
			double dy = at.getY() - center.getY();
			double xydist = Math.max(dx*dx + dy*dy - sigma*sigma, 0);
			double dz = at.getZ() - center.getZ();
			double nsig = sigma + (dz*dz)/(sigma*sigma);

			return 1 - (Math.abs(dz)/sigma)*(1/(nsig*SQRT2PI))*Math.exp(-(xydist)/(2*nsig*nsig));
		}
	};

	public static enum BlotMode
	{
		GAUSSIAN3D_PSF ("Gaussian w/ PSF", gaussSpherePSF),
		FADE3D ("Fading Sphere", fadeSphere),
		FLAT ("Flat Sphere", flatSphere);

		private String text;
		private FillFunction func;

		private BlotMode(String text, FillFunction func)
		{
			this.text = text;
			this.func = func;
		}

		@Override
		public String toString()
		{
			return text;
		}

		public double attenuatePixel(Vector3D at, Vector3D center, double sigma)
		{
			return func.value(at, center, sigma);
		}
	};

	public static JSpinner sigmaSpinner =  new JSpinner(new SpinnerNumberModel(1.0, 0.1, 100.0, 0.1));
	public static JSpinner minSpinner = new JSpinner(new SpinnerNumberModel(0.01, 0.0, 1.0, 0.01));
	public static JSpinner maxSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1.0, 0.01));
	public static JSlider dsSlider = new JSlider(1, 64, 8);
	public static JComboBox modeCombo = new JComboBox(BlotMode.values());

	@Override
	public Component getControlPanel() {
		return LayoutUtils.form(
			"Blotting Mode", modeCombo,
			"Bead Width (Sigma)", sigmaSpinner,
			"Min. Brightness", minSpinner,
			"Max. Brightness", maxSpinner,
			"Domain Multiple (sigmas)", dsSlider
		);
	}

	@Override
	public String toString() {
		return "Bead Blotter";
	}
	
	@Override
	public int getCapabilities() {
		return Processor.PRE_FUSION;
	}

}
