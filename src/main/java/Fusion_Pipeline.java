import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;

import org.openspim.gui.LayoutUtils;
import org.openspim.gui.SortableDualList;
import org.openspim.toolkit.OpenSPIMToolkit;
import org.openspim.toolkit.Processor;

import ij.IJ;
import ij.Macro;
import ij.plugin.PlugIn;

public class Fusion_Pipeline extends JDialog implements PlugIn, ActionListener {
	private static final long serialVersionUID = -6998219292878544044L;

	public void showAbout() {
		IJ.showMessage("Fusion Pipeline", "A flexible plugin for processing SPIM data.");
	}

	@Override
	public void run(String arg0) {
		setVisible(true);

		if(!doProcessing)
			return;
		
		Processor.Params params = new Processor.Params();
		params.invokeOn = Executors.newFixedThreadPool(((Number)threads.getValue()).intValue());
		params.spec = OpenSPIMToolkit.readSpecifierFields(specForm);

		OpenSPIMToolkit.readManualRegFields(manRegForm);
		params.beadRadius1 = OpenSPIMToolkit.RegField.RADIUS1.getDoubleValue();
		params.beadRadius2 = OpenSPIMToolkit.RegField.RADIUS2.getDoubleValue();
		params.threshold = OpenSPIMToolkit.RegField.THRESHOLD.getDoubleValue();
		params.xyUmPerPix = OpenSPIMToolkit.RegField.PIXEL_SIZE_XY.getDoubleValue();
		params.zUmPerPix = OpenSPIMToolkit.RegField.PIXEL_SIZE_Z.getDoubleValue();
		
		params.progressScale = 1 / (float)(preproc.countRHS());
		
		try {
			IJ.log("~~~~~~~~~~~~~~~~ BEGIN PREPROCESS STAGE ~~~~~~~~~~~~~~~~\n");

			params.stage = Processor.Stage.PREPROCESS;
			for(Processor proc : preproc)
				proc.performProcessing(params);
			
			IJ.log("~~~~~~~~~~~~~~~~ BEGIN SEGMENTATION STAGE ~~~~~~~~~~~~~~~~\n");

			params.stage = Processor.Stage.SEGMENTATION;
			((Processor)seg.getSelectedItem()).performProcessing(params);

			IJ.log("~~~~~~~~~~~~~~~~ BEGIN PRE-REGISTRATION STAGE ~~~~~~~~~~~~~~~~\n");

			params.stage = Processor.Stage.POST_SEG_PRE_REG;
			for(Processor proc : prereg)
				proc.performProcessing(params);
			
			IJ.log("~~~~~~~~~~~~~~~~ BEGIN REGISTRATION STAGE ~~~~~~~~~~~~~~~~\n");

			params.stage = Processor.Stage.REGISTRATION;
			((Processor)reg.getSelectedItem()).performProcessing(params);

			IJ.log("~~~~~~~~~~~~~~~~ BEGIN PRE-FUSION STAGE ~~~~~~~~~~~~~~~~\n");

			params.stage = Processor.Stage.POST_REG_PRE_FUSE;
			for(Processor proc : prefuse)
				proc.performProcessing(params);

			IJ.log("~~~~~~~~~~~~~~~~ BEGIN FUSION STAGE ~~~~~~~~~~~~~~~~\n");

			params.stage = Processor.Stage.FUSION;
			((Processor)fuse.getSelectedItem()).performProcessing(params);

			IJ.log("~~~~~~~~~~~~~~~~ BEGIN POSTPROCESS STAGE ~~~~~~~~~~~~~~~~\n");

			params.stage = Processor.Stage.POSTPROCESS;
			for(Processor proc : postproc)
				proc.performProcessing(params);

			IJ.log("~~~~~~~~~~~~~~~~ DONE! :D ~~~~~~~~~~~~~~~~\n");
		} catch(InterruptedException e) {
			IJ.log("~~~~~~~~~~~~~~~~ INTERRUPTED! D: ~~~~~~~~~~~~~~~~\n");
			IJ.handleException(e);
			params.invokeOn.shutdownNow();
		} catch(ExecutionException e) {
			IJ.log("~~~~~~~~~~~~~~~~ ERROR! D: ~~~~~~~~~~~~~~~~\n");
			IJ.handleException(e);
			params.invokeOn.shutdownNow();
		}
	}
	
	private Map<String, Component> specForm, manRegForm;
	private boolean doProcessing;
	private SortableDualList<Processor> preproc, prereg, prefuse, postproc;
	private JComboBox seg, reg, fuse;
	private JSpinner threads;
	private JCheckBox archipelago;

	private static ServiceLoader<Processor> procs = ServiceLoader.load(Processor.class);

	private static <T> List<T> fromIterator(Iterator<T> iter)
	{
		List<T> ret = new LinkedList<T>();
		
		while(iter.hasNext())
			ret.add(iter.next());
		
		return ret;
	}
	
	private void configureProcessor(Component source, Processor cfg)
	{
		if(cfg == null)
		{
			JOptionPane.showMessageDialog(source, "You must select a processor to configure.");
			return;
		}
		
		Component controls = cfg.getControlPanel();
		
		if(controls == null)
		{
			JOptionPane.showMessageDialog(source, "This processor requires no configuration.");
			return;
		}

		JDialog dlg = new JDialog(this, "Configure " + cfg.toString());
		dlg.getContentPane().add(cfg.getControlPanel());
		dlg.pack();
		java.awt.Point loc = source.getLocationOnScreen();
		dlg.setLocation(loc.x + source.getWidth() / 2 - dlg.getWidth() / 2, loc.y + source.getHeight() - dlg.getHeight() / 2);
		dlg.setModal(true);
		dlg.setVisible(true);
	}
	
	private JButton configButton(final Callable<Processor> source)
	{
		JButton ret = new JButton("Configure...");
		
		ret.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent ae)
			{
				try {
					configureProcessor((Component) ae.getSource(), source.call());
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		});
		
		return ret;
	}
	
	private static Callable<Processor> fromListBox(final SortableDualList<Processor> from)
	{
		return new Callable<Processor>() {
			@Override
			public Processor call()
			{
				return from.getSelectedValueRHS();
			}
		};
	}
	
	private static Callable<Processor> fromComboBox(final JComboBox from)
	{
		return new Callable<Processor>() {
			@Override
			public Processor call()
			{
				return (Processor) from.getSelectedItem();
			}
		};
	}

	public Fusion_Pipeline()
	{
		super((JDialog)null, "OpenSPIM Toolkit: Fusion Pipeline");
		
		getRootPane().setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));
		
		JButton goBtn = new JButton("Process");
		goBtn.addActionListener(this);
		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.addActionListener(this);
		
		LayoutUtils.addAll(getContentPane(),
			LayoutUtils.vertPanel("Processing Options",
				LayoutUtils.form(
					"Thread Pool Size:", threads = new JSpinner(new SpinnerNumberModel(Runtime.getRuntime().availableProcessors(), 1, 10*Runtime.getRuntime().availableProcessors(), 1)),
					"Archipelago:", archipelago = new JCheckBox()
				)
			),
			LayoutUtils.horizPanel("Data Information",
				LayoutUtils.form(specForm = OpenSPIMToolkit.createSpecifierFields()),
				LayoutUtils.form(manRegForm = OpenSPIMToolkit.createManualRegFields())
			),
			LayoutUtils.horizPanel("Pre-Processing",
				preproc = new SortableDualList<Processor>(new Processor.CapFilter(procs.iterator(), Processor.PREPROCESSOR | Processor.PRE_SEGMENTATION)),
				Box.createHorizontalGlue(),
				Box.createHorizontalStrut(8),
				configButton(fromListBox(preproc))
			),
			LayoutUtils.horizPanel("Segmentation",
				seg = new JComboBox(fromIterator(new Processor.CapFilter(procs.iterator(), Processor.SEGMENTATION)).toArray(new Processor[0])),
				Box.createHorizontalStrut(8),
				configButton(fromComboBox(seg))
			),
			LayoutUtils.horizPanel("Post-Segmentation / Pre-Registration",
				prereg = new SortableDualList<Processor>(new Processor.CapFilter(procs.iterator(), Processor.POST_SEGMENTATION | Processor.PRE_REGISTRATION)),
				Box.createHorizontalGlue(),
				Box.createHorizontalStrut(8),
				configButton(fromListBox(prereg))
			),
			LayoutUtils.horizPanel("Registration",
				reg = new JComboBox(fromIterator(new Processor.CapFilter(procs.iterator(), Processor.REGISTRATION)).toArray(new Processor[0])),
				Box.createHorizontalStrut(8),
				configButton(fromComboBox(reg))
			),
			LayoutUtils.horizPanel("Post-Registration / Pre-Fusion",
				prefuse = new SortableDualList<Processor>(new Processor.CapFilter(procs.iterator(), Processor.POST_REGISTRATION | Processor.PRE_FUSION)),
				Box.createHorizontalGlue(),
				Box.createHorizontalStrut(8),
				configButton(fromListBox(prefuse))
			),
			LayoutUtils.horizPanel("Fusion",
				fuse = new JComboBox(fromIterator(new Processor.CapFilter(procs.iterator(), Processor.FUSION)).toArray(new Processor[0])),
				Box.createHorizontalStrut(8),
				configButton(fromComboBox(fuse))
			),
			LayoutUtils.horizPanel("Post-Processing",
				postproc = new SortableDualList<Processor>(new Processor.CapFilter(procs.iterator(), Processor.POST_FUSION | Processor.POSTPROCESSOR)),
				Box.createHorizontalGlue(),
				Box.createHorizontalStrut(8),
				configButton(fromListBox(postproc))
			),
			LayoutUtils.horizPanel(
				Box.createHorizontalGlue(),
				goBtn,
				cancelBtn
			)
		);

		archipelago.setEnabled(false);
		
		pack();

		setModal(true);
		
		getRootPane().registerKeyboardAction(
			new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent arg0) {
					cancelDialog();
				}
			},
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
		);
	}
	
	private void cancelDialog()
	{
		Macro.setOptions(OpenSPIMToolkit.RegField.getMacroOptions(
			OpenSPIMToolkit.RegField.PATH,
			OpenSPIMToolkit.RegField.PATTERN,
			OpenSPIMToolkit.RegField.TIMEPOINTS,
			OpenSPIMToolkit.RegField.ANGLES
		));

		setVisible(false);
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		if(ae.getActionCommand() == "Process") {
			OpenSPIMToolkit.readSpecifierFields(specForm);
		}
		
		if(ae.getActionCommand() != "Process")
		{
			cancelDialog();
		}
		else
		{
			doProcessing = true; // do stuff!
			setVisible(false);
		}
	}
}
