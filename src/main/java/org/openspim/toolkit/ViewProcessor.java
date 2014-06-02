package org.openspim.toolkit;

import ij.IJ;

import java.io.File;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class ViewProcessor implements Processor {
	private Map<File, Float> progressMap = new Hashtable<File, Float>();
	
	@Override
	public void performProcessing(final Params params) throws InterruptedException, ExecutionException
	{
		List<Future<Void>> futures = new LinkedList<Future<Void>>();
		for(final File f : params.spec)
		{
			progressMap.put(f, 0.0f);

			futures.add(params.invokeOn.submit(new Callable<Void>() {
				@Override
				public Void call()
				{
					ViewProcessor.this.processView(params, f);

					return null;
				}
			}));
		}
		
		for(Future<Void> f : futures)
			f.get();
	}

	protected void reportProgress(File f, float progress)
	{
		progressMap.put(f, progress);
		
		float overall = 0;
		
		for(Float value : progressMap.values())
			overall += value / progressMap.size();
		
		IJ.showProgress(overall);
		IJ.showStatus(String.format("%s: %.2f%%", toString(), overall*100));
	}

	public abstract void processView(Params p, File view);
}
