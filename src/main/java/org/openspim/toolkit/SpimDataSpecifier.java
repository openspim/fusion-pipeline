package org.openspim.toolkit;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements a class representing all SPIM data files to be used for this
 * registration/fusion process. That is, this is a code-based (and iterable)
 * representation of the pattern (spim_TL{tt}_Angle{a}.ome.tiff), time, and
 * angle specifiers (i.e. 1-12 and 0-300:60).
 *
 * @author LOCI
 *
 */
public class SpimDataSpecifier implements Iterable<File>, Iterator<File>
{
	/**
	 * Implements a class representing the time/angle specifiers.
	 * Timepoints and angles are specified as follows:
	 * - A single value is just that number.
	 * - A range of values is given in min-max:step form.
	 *   - If step is omitted, it is assumed to be 1.
	 * - Multiple values or ranges are separated by commas.
	 *
	 * @author LOCI
	 *
	 */
	public static class MultiSpec implements Iterable<Integer>
	{
		private static Pattern rangeRegex = Pattern.compile("^([0-9]+)(-([0-9]+)(:([0-9]+))?)?$");
		
		private List<Integer> sequence;
		public String source;

		public MultiSpec(String specifier)
		{
			source = specifier;

			String[] inranges = specifier.split(",");
			sequence = new LinkedList<Integer>();

			for(String r : inranges) {
				Matcher m = rangeRegex.matcher(r.trim());
				if(!m.matches())
				{
					ij.IJ.log("Invalid range \"" + r + "\"");
					continue;
				}

				int min = Integer.parseInt(m.group(1));
				int max = min, step = 1;

				if(m.group(3) != null && !m.group(3).isEmpty())
					max = Integer.parseInt(m.group(3));

				if(m.group(5) != null && !m.group(5).isEmpty())
					step = Integer.parseInt(m.group(5));

				for(int i = min; i <= max; i += step)
					sequence.add(i);
			}
		}

		public int count()
		{
			return sequence.size();
		}

		public int get(int i)
		{
			return sequence.get(i);
		}

		@Override
		public Iterator<Integer> iterator()
		{
			return sequence.iterator();
		}
	}

	private File directory;
	private String pattern;
	private MultiSpec timepoints;
	private MultiSpec angles;

	private Integer timepoint;
	private Iterator<Integer> angleIterator;
	private Iterator<Integer> timepointIterator;

	public SpimDataSpecifier(File directory, String pattern, String timepoints, String angles)
	{
		this.directory = directory;
		this.pattern = pattern;
		this.timepoints = new MultiSpec(timepoints);
		this.angles = new MultiSpec(angles);

		angleIterator = timepointIterator = null;
		timepoint = 0;
	}

	public int count()
	{
		return timepoints.count() * angles.count();
	}

	@Override
	public Iterator<File> iterator()
	{
		if(angleIterator != null || timepointIterator != null)
			throw new UnsupportedOperationException("multiple simultaneous iteration");

		timepointIterator = timepoints.iterator();
		angleIterator = angles.iterator();
		timepoint = timepointIterator.next();
		return this;
	}

	@Override
	public boolean hasNext()
	{
		return angleIterator != null && timepointIterator != null && (angleIterator.hasNext() || timepointIterator.hasNext());
	}

	private static Pattern tpPat = Pattern.compile("\\{t+\\}");
	private static Pattern aPat = Pattern.compile("\\{a+\\}");

	public File next()
	{
		if(!angleIterator.hasNext())
		{
			angleIterator = angles.iterator();
			timepoint = timepointIterator.next();
		}

		int angle = angleIterator.next();

		String fn = pattern;
		Matcher m = tpPat.matcher(fn);
		while(m.find())
			fn = fn.replace(m.group(), String.format("%0" + (m.group().length() - 2) + "d", timepoint));

		m = aPat.matcher(fn);
		while(m.find())
			fn = fn.replace(m.group(), String.format("%0" + (m.group().length() - 2) + "d", angle));
		
		if(!hasNext())
		{
			angleIterator = null;
			timepointIterator = null;
		}

		return new File(directory, fn);
	}

	public int getCurrentTimepoint()
	{
		return timepoint;
	}

	@Override
	public void remove()
	{
		throw new UnsupportedOperationException();
	}

	public String getParameterString()
	{
		return String.format("spim_data_directory=[%s] pattern_of_spim=[%s] timepoints_to_process=[%s] angles_to_process=[%s]",
			directory.getAbsolutePath(), pattern, timepoints.source, angles.source);
	}
}
