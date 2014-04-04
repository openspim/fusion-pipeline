package org.openspim.toolkit;

import java.awt.Component;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public interface Processor {
	
	public static enum Stage
	{
		PREPROCESS(PREPROCESSOR | PRE_SEGMENTATION),
		SEGMENTATION(Processor.SEGMENTATION),
		POST_SEG_PRE_REG(POST_SEGMENTATION | PRE_REGISTRATION),
		REGISTRATION(Processor.REGISTRATION),
		POST_REG_PRE_FUSE(POST_REGISTRATION | PRE_FUSION),
		FUSION(Processor.FUSION),
		POSTPROCESS(POST_FUSION | POSTPROCESSOR);
		
		private int mask = 0;
		
		private Stage(int mask)
		{
			this.mask = mask;
		}
		
		public int getMask()
		{
			return mask;
		}
	}

	public static final int PREPROCESSOR = 1 << 0;
	public static final int PRE_SEGMENTATION = 1 << 1;

	public static final int SEGMENTATION = 1 << 2;
	
	public static final int POST_SEGMENTATION = 1 << 3;
	public static final int PRE_REGISTRATION = 1 << 4;
	
	public static final int REGISTRATION = 1 << 5;
	
	public static final int POST_REGISTRATION = 1 << 6;
	public static final int PRE_FUSION = 1 << 7;
	
	public static final int FUSION = 1 << 8;
	
	public static final int POST_FUSION = 1 << 9;
	public static final int POSTPROCESSOR = 1 << 10;
	
	public static class CapFilter implements Iterator<Processor> {
		private Processor next = null;
		private Iterator<Processor> source = null;
		private int bitmask = 0;
		
		public CapFilter(Iterator<Processor> source, int bitmask)
		{
			this.bitmask = bitmask;
			this.source = source;
			
			findNext();
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public Processor next() {
			Processor tmp = next;
			findNext();
			return tmp;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
		private void findNext() {
			while(source.hasNext())
				if(((next = source.next()).getCapabilities() & bitmask) != 0)
					return;
			
			next = null;
		}
	}
	
	public static class Params
	{
		public ExecutorService invokeOn;
		public Stage stage; // Current stage of processing.
		public SpimDataSpecifier spec; // Data specifier.
		
		public double beadRadius1;
		public double beadRadius2;
		public double threshold;
		public double xyUmPerPix;
		public double zUmPerPix;
		
		public float progressScale;
	}
	
	public abstract int getCapabilities();
	
	public abstract Component getControlPanel();
	
	public abstract void performProcessing(final Params params) throws InterruptedException, ExecutionException;
}