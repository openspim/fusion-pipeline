package org.openspim.gui;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

@SuppressWarnings("serial")
public class SortableDualList<T> extends JPanel implements Iterable<T>, ActionListener {

	 // DefaultListModel is only slightly better-written than this.
	private static class SimpleListModel<S> implements ListModel
	{
		protected ArrayList<S> data = new ArrayList<S>();
		protected int oldSize = 0;
		protected List<ListDataListener> listeners = new LinkedList<ListDataListener>();

		public SimpleListModel()
		{
		}
		
		@SuppressWarnings("unused")
		public SimpleListModel(S... elements)
		{
			data.ensureCapacity(elements.length);
			for(S el : elements)
				data.add(el);
			oldSize = data.size();
		}
		
		public SimpleListModel(Iterator<S> elements)
		{
			while(elements.hasNext())
				data.add(elements.next());
			oldSize = data.size();
		}
		
		public List<S> getList()
		{
			return data;
		}
		
		public int[] addAll(int idx, List<S> from, int[] indices)
		{
			data.ensureCapacity(data.size() + indices.length);
			for(int i=0; i < indices.length; ++i)
			{
				data.add(idx, from.get(indices[i]));
				indices[i] = idx;
				
				++idx;
			}
			
			triggerFullRefresh();
			
			return indices;
		}
		
		public void addAll(int idx, List<S> from)
		{
			data.ensureCapacity(data.size() + from.size());
			for(S obj : from)
				data.add(idx++, obj);
			
			triggerFullRefresh();
		}
		
		public void removeAllIndices(int[] indices)
		{
			int offs = 0;
			for(int index : indices)
				data.remove(index - (offs++));
			
			triggerFullRefresh();
		}
		
		public int[] move(int[] indices, int offset)
		{
			if(offset == 0)
				return indices;
			
			// Indices is already sorted. Coerce to reasonable range.
			if(indices[indices.length - 1] + offset >= data.size())
				offset = data.size() - indices[indices.length - 1] - 1;
			if(indices[0] + offset < 0)
				offset = -indices[0];
			
			// Iteration order is important here.
			if(offset > 0)
			{
				for(int i=indices.length - 1; i >= 0; --i)
				{
					data.add(indices[i] + offset, data.remove(indices[i]));
					indices[i] += offset;
				}
			}
			else if(offset < 0)
			{
				for(int i=0; i < indices.length; ++i)
				{
					data.add(indices[i] + offset, data.remove(indices[i]));
					indices[i] += offset;
				}
			}
			
			triggerFullRefresh();
			
			return indices;
		}
		
		public void removeAll()
		{
			data.clear();
			triggerFullRefresh();
		}
		
		private void triggerFullRefresh()
		{
			ListDataEvent lde2 = null, lde1 = (data.size() > 0 ? new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, data.size()) : null);

			if(oldSize > data.size())
				lde2 = new ListDataEvent(this, ListDataEvent.INTERVAL_REMOVED, data.size(), oldSize);
			else if(oldSize < data.size())
				lde2 = new ListDataEvent(this, ListDataEvent.INTERVAL_ADDED, oldSize, data.size());
			
			oldSize = data.size();			

			for(ListDataListener ldl : listeners)
			{
				if(lde1 != null)
					ldl.contentsChanged(lde1);

				if(lde2 != null && lde2.getType() == ListDataEvent.INTERVAL_REMOVED)
					ldl.intervalRemoved(lde2);
				else if(lde2 != null && lde2.getType() == ListDataEvent.INTERVAL_ADDED)
					ldl.intervalAdded(lde2);
				
			}
		}
		
		@Override
		public void addListDataListener(ListDataListener ldl) {
			listeners.add(ldl);
		}

		@Override
		public S getElementAt(int idx) {
			return data.get(idx);
		}

		@Override
		public int getSize() {
			return data.size();
		}

		@Override
		public void removeListDataListener(ListDataListener ldl) {
			listeners.remove(ldl);
		}
		
	}
	
	private JList left, right;
	private SimpleListModel<T> leftModel, rightModel;
	private JButton moveAllLeft, moveLeft, moveRight, moveAllRight, moveUp, moveDown, moveTop, moveBottom;
	
	private Dimension listBoxSize = new Dimension(176, 88);
	private Dimension shiftButtonSize = new Dimension(66, 22);
	private Dimension sortButtonSize = new Dimension(88, 22);
	
	public SortableDualList(Iterator<T> source)
	{
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		
		JScrollPane spl = new JScrollPane(left = new JList(leftModel = new SimpleListModel<T>(source)));
		spl.setPreferredSize(listBoxSize);
		add(spl);
		
		JPanel shiftButtons = new JPanel();
		shiftButtons.setLayout(new GridLayout(4, 1));
		LayoutUtils.addAll(shiftButtons,
			moveAllLeft = new JButton("<<"),
			moveLeft = new JButton("<"),
			moveRight = new JButton(">"),
			moveAllRight = new JButton(">>")
		);
		add(shiftButtons);
		
		moveAllLeft.setPreferredSize(shiftButtonSize);
		moveLeft.setPreferredSize(shiftButtonSize);
		moveRight.setPreferredSize(shiftButtonSize);
		moveAllRight.setPreferredSize(shiftButtonSize);
		
		moveAllLeft.addActionListener(this);
		moveLeft.addActionListener(this);
		moveRight.addActionListener(this);
		moveAllRight.addActionListener(this);

		JScrollPane spr = new JScrollPane(right = new JList(rightModel = new SimpleListModel<T>()));
		spr.setPreferredSize(listBoxSize);
		add(spr);
		
		JPanel sortButtons = new JPanel();
		sortButtons.setLayout(new GridLayout(4, 1));
		LayoutUtils.addAll(sortButtons,
			moveTop = new JButton("Top"),
			moveUp = new JButton("Up"),
			moveDown = new JButton("Down"),
			moveBottom = new JButton("Bottom")
		);

		moveTop.setPreferredSize(sortButtonSize);
		moveUp.setPreferredSize(sortButtonSize);
		moveDown.setPreferredSize(sortButtonSize);
		moveBottom.setPreferredSize(sortButtonSize);
		
		moveTop.addActionListener(this);
		moveUp.addActionListener(this);
		moveDown.addActionListener(this);
		moveBottom.addActionListener(this);
		
		add(LayoutUtils.vertPanel(
			Box.createVerticalGlue(),
			sortButtons,
			Box.createVerticalGlue()
		));
	}
	
	@Override
	public Iterator<T> iterator() {
		return rightModel.getList().iterator();
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		if(">".equals(ae.getActionCommand())) {
			if(left.getSelectedIndices().length <= 0)
				return;

			int[] sel = right.getSelectedIndices();
			int idx = (sel.length <= 0) ? rightModel.getSize() : sel[sel.length - 1];
			right.setSelectedIndices(rightModel.addAll(idx, leftModel.getList(), left.getSelectedIndices()));
			leftModel.removeAllIndices(left.getSelectedIndices());
			left.clearSelection();
		} else if(">>".equals(ae.getActionCommand())) {
			int[] sel = right.getSelectedIndices();
			int idx = (sel.length <= 0) ? rightModel.getSize() : sel[sel.length - 1];
			rightModel.addAll(idx, leftModel.getList());
			leftModel.removeAll();
		} else if("<".equals(ae.getActionCommand())) {
			if(right.getSelectedIndices().length <= 0)
				return;

			int[] sel = left.getSelectedIndices();
			int idx = (sel.length <= 0) ? leftModel.getSize() : sel[sel.length - 1];
			left.setSelectedIndices(leftModel.addAll(idx, rightModel.getList(), right.getSelectedIndices()));
			rightModel.removeAllIndices(right.getSelectedIndices());
			right.clearSelection();
		} else if("<<".equals(ae.getActionCommand())) {
			int[] sel = left.getSelectedIndices();
			int idx = (sel.length <= 0) ? leftModel.getSize() : sel[sel.length - 1];
			leftModel.addAll(idx, rightModel.getList());
			rightModel.removeAll();
		} else if("Top".equals(ae.getActionCommand())) {
			right.setSelectedIndices(rightModel.move(right.getSelectedIndices(), -rightModel.getSize()));
		} else if("Up".equals(ae.getActionCommand())) {
			right.setSelectedIndices(rightModel.move(right.getSelectedIndices(), -1));
		} else if("Down".equals(ae.getActionCommand())) {
			right.setSelectedIndices(rightModel.move(right.getSelectedIndices(), 1));
		} else if("Bottom".equals(ae.getActionCommand())) {
			right.setSelectedIndices(rightModel.move(right.getSelectedIndices(), rightModel.getSize()));
		}
	}
	
	public T getSelectedValueRHS()
	{
		return (T)right.getSelectedValue();
	}
	
	@SuppressWarnings("unchecked")
	public T[] getValuesRHS()
	{
		return (T[])rightModel.getList().toArray();
	}
	
	public int countRHS()
	{
		return rightModel.getSize();
	}
}
