/*******************************************************************************
 * JetUML - A desktop application for fast UML diagramming.
 *
 * Copyright (C) 2015 Cay S. Horstmann and the contributors of the 
 * JetUML project.
 *
 * See: https://github.com/prmr/JetUML
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package ca.mcgill.cs.stg.jetuml.framework;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ca.mcgill.cs.stg.jetuml.graph.Edge;
import ca.mcgill.cs.stg.jetuml.graph.Graph;
import ca.mcgill.cs.stg.jetuml.graph.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * A panel to draw a graph.
 */
@SuppressWarnings("serial")
public class GraphPanel extends JPanel
{
	private static final int DRAG_NONE = 0;
	private static final int DRAG_MOVE = 1;
	private static final int DRAG_RUBBERBAND = 2;
	private static final int DRAG_LASSO = 3;
	private static final int CONNECT_THRESHOLD = 8;
	private static final Color PURPLE = new Color(0.7f, 0.4f, 0.7f);
	
	private Graph graph;
	private GraphFrame frame;
	private ToolBar toolBar;
	private double zoom;	
	private boolean hideGrid;
	private boolean modified;
	private Object lastSelected;
	private Set selectedItems;
	private Point2D lastMousePoint;
	private Point2D mouseDownPoint;   
	private int dragMode;
	
	/**
	 * Constructs a graph.
	 * @param pToolBar the tool bar with the node and edge tools
	 */
	public GraphPanel(ToolBar pToolBar)
	{
		zoom = 1;
		toolBar = pToolBar;
		setBackground(Color.WHITE);
		selectedItems = new HashSet();
      
		addMouseListener(new MouseAdapter() 
		{
			public void mousePressed(MouseEvent pEvent)
			{
				requestFocus();
				final Point2D mousePoint = new Point2D.Double(pEvent.getX() / zoom, pEvent.getY() / zoom);
				boolean isCtrl = (pEvent.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0; 
				Node n = graph.findNode(mousePoint);
				Edge e = graph.findEdge(mousePoint);
				Object tool = toolBar.getSelectedTool();
				if(pEvent.getClickCount() > 1 || (pEvent.getModifiers() & InputEvent.BUTTON1_MASK) == 0)
				{  // double/right-click
					if(e != null)
					{
						setSelectedItem(e);
						editSelected();
					}
					else if(n != null)
					{
						setSelectedItem(n);
						editSelected();
					}
					else
					{
						toolBar.showPopup(GraphPanel.this, mousePoint, new ActionListener()
                        {
                           public void actionPerformed(ActionEvent pEvent)
                           {
                        	   Object tool = toolBar.getSelectedTool();
                        	   if(tool instanceof Node)
                        	   {
                        		   Node prototype = (Node) tool;
                        		   Node newNode = (Node) prototype.clone();
                        		   boolean added = graph.add(newNode, mousePoint);
                        		   if(added)
                        		   {
                        			   setModified(true);
                        			   setSelectedItem(newNode);
                        		   }
                        	   }
                           	}
                        });
					}
				}
				else if(tool == null) // select
				{
					if(e != null)
					{
						setSelectedItem(e);
					}
					else if(n != null)
					{
						if(isCtrl) 
						{
							addSelectedItem(n);
						}
						else if(!selectedItems.contains(n)) 
						{
							setSelectedItem(n);
						}
						dragMode = DRAG_MOVE;
					}
					else
					{
						if(!isCtrl) 
						{
							clearSelection();
						}
						dragMode = DRAG_LASSO;
					}
				}
				else if(tool instanceof Node)
				{
					Node prototype = (Node) tool;
					Node newNode = (Node) prototype.clone();
					boolean added = graph.add(newNode, mousePoint);
					if(added)
					{
						setModified(true);
						setSelectedItem(newNode);
						dragMode = DRAG_MOVE;
					}
					else if(n != null)
					{
						if(isCtrl) 
						{
							addSelectedItem(n);
						}
						else if(!selectedItems.contains(n))
						{
							setSelectedItem(n);
						}
						dragMode = DRAG_MOVE;
					}
				}
				else if(tool instanceof Edge)
				{
					if(n != null) 
					{
						dragMode = DRAG_RUBBERBAND;
					}
				}
				lastMousePoint = mousePoint;
				mouseDownPoint = mousePoint;
				repaint();
			}

			public void mouseReleased(MouseEvent pEvent)
			{
				Point2D mousePoint = new Point2D.Double(pEvent.getX() / zoom, pEvent.getY() / zoom);
				Object tool = toolBar.getSelectedTool();
				if(dragMode == DRAG_RUBBERBAND)
				{
					Edge prototype = (Edge) tool;
					Edge newEdge = (Edge) prototype.clone();
					if(mousePoint.distance(mouseDownPoint) > CONNECT_THRESHOLD && graph.connect(newEdge, mouseDownPoint, mousePoint))
					{
						setModified(true);
						setSelectedItem(newEdge);
					}
				}
				else if(dragMode == DRAG_MOVE)
				{
					graph.layout();
					setModified(true);
				}
				dragMode = DRAG_NONE;
				revalidate();
				repaint();
			}
		});

		addMouseMotionListener(new MouseMotionAdapter()
		{
			public void mouseDragged(MouseEvent pEvent)
			{
				Point2D mousePoint = new Point2D.Double(pEvent.getX() / zoom, pEvent.getY() / zoom);
				boolean isCtrl = (pEvent.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0; 
				if(dragMode == DRAG_MOVE && lastSelected instanceof Node)
				{               
					Node lastNode = (Node) lastSelected;
					Rectangle2D bounds = lastNode.getBounds();
					double dx = mousePoint.getX() - lastMousePoint.getX();
					double dy = mousePoint.getY() - lastMousePoint.getY();
                            
					// we don't want to drag nodes into negative coordinates
					// particularly with multiple selection, we might never be 
					// able to get them back.
					Iterator iter = selectedItems.iterator();
					while(iter.hasNext())
					{
						Object selected = iter.next();                 
						if(selected instanceof Node)
						{
							Node n = (Node) selected;
							bounds.add(n.getBounds());
						}
					}
					dx = Math.max(dx, -bounds.getX());
					dy = Math.max(dy, -bounds.getY());
               
					iter = selectedItems.iterator();
					while(iter.hasNext())
					{
						Object selected = iter.next();                 
						if(selected instanceof Node)
						{
							Node n = (Node) selected;
							n.translate(dx, dy);                           
						}
					}
					// we don't want continuous layout any more because of multiple selection
					// graph.layout();
				}            
				else if(dragMode == DRAG_LASSO)
				{
					double x1 = mouseDownPoint.getX();
					double y1 = mouseDownPoint.getY();
					double x2 = mousePoint.getX();
					double y2 = mousePoint.getY();
					Rectangle2D.Double lasso = new Rectangle2D.Double(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x1 - x2) , Math.abs(y1 - y2));
					Iterator iter = graph.getNodes().iterator();
					while(iter.hasNext())
					{
						Node n = (Node) iter.next();
						if(!isCtrl && !lasso.contains(n.getBounds())) 
						{
							removeSelectedItem(n);
						}
						else if (lasso.contains(n.getBounds())) 
						{
							addSelectedItem(n);
						}
					}
				}
				lastMousePoint = mousePoint;
				repaint();
			}
		});
	}

   /**
    * Edits the properties of the selected graph element.
    */
   public void editSelected()
   {
      Object edited = lastSelected;
      if (lastSelected == null)
      {
         if (selectedItems.size() == 1)
            edited = selectedItems.iterator().next();
         else
            return;
      }

      PropertySheet sheet = new PropertySheet(edited);
      sheet.addChangeListener(new ChangeListener()
      {
         public void stateChanged(ChangeEvent event)
         {
            graph.layout();
            repaint();
         }
      });
      JOptionPane.showInternalMessageDialog(this, sheet, 
            ResourceBundle.getBundle("ca.mcgill.cs.stg.jetuml.framework.EditorStrings").getString("dialog.properties"),            
            JOptionPane.PLAIN_MESSAGE);
      setModified(true);
   }

   /**
    * Removes the selected nodes or edges.
    */
   public void removeSelected()
   {
      Iterator iter = selectedItems.iterator();
      while (iter.hasNext())
      {
         Object selected = iter.next();                 
         if (selected instanceof Node)
         {
            graph.removeNode((Node) selected);
         }
         else if (selected instanceof Edge)
         {
            graph.removeEdge((Edge) selected);
         }
      }
      if (selectedItems.size() > 0) setModified(true);
      repaint();
   }

   /**
    * Set the graph in the panel
    * @param aGraph the graph to be displayed and edited
    */
   public void setGraph(Graph aGraph)
   {
      graph = aGraph;
      setModified(false);
      revalidate();
      repaint();
   }

   @Override
   public void paintComponent(Graphics pGraphics)
   {
	   super.paintComponent(pGraphics);
	   Graphics2D g2 = (Graphics2D) pGraphics;
	   g2.scale(zoom, zoom);
	   Rectangle2D bounds = getBounds();
	   Rectangle2D graphBounds = graph.getBounds(g2);
	   if(!hideGrid) 
	   {
		   Grid.draw(g2, new Rectangle2D.Double(0, 0, Math.max(bounds.getMaxX() / zoom, graphBounds.getMaxX()), 
				   Math.max(bounds.getMaxY() / zoom, graphBounds.getMaxY())));
	   }
	   graph.draw(g2, new Grid());

      Iterator iter = selectedItems.iterator();
      Set toBeRemoved = new HashSet();
      while (iter.hasNext())
      {
         Object selected = iter.next();                 
      
         if (!graph.getNodes().contains(selected)
               && !graph.getEdges().contains(selected)) 
         {
            toBeRemoved.add(selected);
         }
         else if (selected instanceof Node)
         {
            Rectangle2D grabberBounds = ((Node) selected).getBounds();
            drawGrabber(g2, grabberBounds.getMinX(), grabberBounds.getMinY());
            drawGrabber(g2, grabberBounds.getMinX(), grabberBounds.getMaxY());
            drawGrabber(g2, grabberBounds.getMaxX(), grabberBounds.getMinY());
            drawGrabber(g2, grabberBounds.getMaxX(), grabberBounds.getMaxY());
         }
         else if (selected instanceof Edge)
         {
            Line2D line = ((Edge) selected).getConnectionPoints();
            drawGrabber(g2, line.getX1(), line.getY1());
            drawGrabber(g2, line.getX2(), line.getY2());
         }
      }

      iter = toBeRemoved.iterator();
      while (iter.hasNext())      
         removeSelectedItem(iter.next());                 
      
      if (dragMode == DRAG_RUBBERBAND)
      {
         Color oldColor = g2.getColor();
         g2.setColor(PURPLE);
         g2.draw(new Line2D.Double(mouseDownPoint, lastMousePoint));
         g2.setColor(oldColor);
      }      
      else if (dragMode == DRAG_LASSO)
      {
         Color oldColor = g2.getColor();
         g2.setColor(PURPLE);
         double x1 = mouseDownPoint.getX();
         double y1 = mouseDownPoint.getY();
         double x2 = lastMousePoint.getX();
         double y2 = lastMousePoint.getY();
         Rectangle2D.Double lasso = new Rectangle2D.Double(Math.min(x1, x2), 
               Math.min(y1, y2), Math.abs(x1 - x2) , Math.abs(y1 - y2));
         g2.draw(lasso);
         g2.setColor(oldColor);
      }      
   }

   /**
    * Draws a single "grabber", a filled square
    * @param g2 the graphics context
    * @param x the x coordinate of the center of the grabber
    * @param y the y coordinate of the center of the grabber
    */
   public static void drawGrabber(Graphics2D g2, double x, double y)
   {
      final int SIZE = 5;
      Color oldColor = g2.getColor();
      g2.setColor(PURPLE);
      g2.fill(new Rectangle2D.Double(x - SIZE / 2, y - SIZE / 2, SIZE, SIZE));
      g2.setColor(oldColor);
   }

   public Dimension getPreferredSize()
   {
      Rectangle2D bounds = graph.getBounds((Graphics2D) getGraphics());
      return new Dimension((int) (zoom * bounds.getMaxX()),
            (int) (zoom * bounds.getMaxY()));
   }

   /**
    * Changes the zoom of this panel. The zoom is 1 by default and is multiplied
    * by sqrt(2) for each positive stem or divided by sqrt(2) for each negative
    * step.
    * @param steps the number of steps by which to change the zoom. A positive
    * value zooms in, a negative value zooms out.
    */
   public void changeZoom(int steps)
   {
      final double FACTOR = Math.sqrt(2);
      for (int i = 1; i <= steps; i++)
         zoom *= FACTOR;
      for (int i = 1; i <= -steps; i++)
         zoom /= FACTOR;
      revalidate();
      repaint();
   }

   public void selectNext(int n)
   {
      ArrayList selectables = new ArrayList();
      selectables.addAll(graph.getNodes());
      selectables.addAll(graph.getEdges());
      if (selectables.size() == 0) return;
      java.util.Collections.sort(selectables, new java.util.Comparator()
      {
         public int compare(Object obj1, Object obj2)
         {
            double x1;
            double y1;
            if (obj1 instanceof Node)
            {
               Rectangle2D bounds = ((Node) obj1).getBounds();
               x1 = bounds.getX();
               y1 = bounds.getY();
            }
            else
            {
               Point2D start = ((Edge) obj1).getConnectionPoints().getP1();
               x1 = start.getX();
               y1 = start.getY();
            }
            double x2;
            double y2;
            if (obj2 instanceof Node)
            {
               Rectangle2D bounds = ((Node) obj2).getBounds();
               x2 = bounds.getX();
               y2 = bounds.getY();
            }
            else
            {
               Point2D start = ((Edge) obj2).getConnectionPoints().getP1();
               x2 = start.getX();
               y2 = start.getY();
            }
            if (y1 < y2) return -1;
            if (y1 > y2) return 1;
            if (x1 < x2) return -1;
            if (x1 > x2) return 1;
            return 0;
         }
      });
      int index;
      if (lastSelected == null) index = 0;
      else index = selectables.indexOf(lastSelected) + n;
      while (index < 0)
         index += selectables.size();
      index %= selectables.size();
      setSelectedItem(selectables.get(index));
      repaint();
   }

   /**
    * Checks whether this graph has been modified since it was last saved.
    * @return true if the graph has been modified
    */
   public boolean isModified()
   {
      return modified;
   }

   /**
    * Sets or resets the modified flag for this graph
    * @param newValue true to indicate that the graph has been modified
    */
   public void setModified(boolean newValue)
   {
      modified = newValue;

      if (frame == null)
      {
         Component parent = this;
         do
         {
            parent = parent.getParent();
         }
         while (parent != null && !(parent instanceof GraphFrame));
         if (parent != null) frame = (GraphFrame) parent;
      }
      if (frame != null)
      {
         String title = frame.getFileName();
         if (title != null)
         {
            if (modified)
            {
               if (!frame.getTitle().endsWith("*")) frame.setTitle(title + "*");
            }
            else frame.setTitle(title);
         }
      }
   }

   private void addSelectedItem(Object obj)
   {
      lastSelected = obj;      
      selectedItems.add(obj);
   }
   
   private void removeSelectedItem(Object obj)
   {
      if (obj == lastSelected)
         lastSelected = null;
      selectedItems.remove(obj);
   }
   
   private void setSelectedItem(Object obj)
   {
      selectedItems.clear();
      lastSelected = obj;
      if (obj != null) selectedItems.add(obj);
   }
   
   private void clearSelection()
   {
      selectedItems.clear();
      lastSelected = null;
   }
   
   /**
    * Sets the value of the hideGrid property
    * @param newValue true if the grid is being hidden
    */
   public void setHideGrid(boolean newValue)
   {
      hideGrid = newValue;
      repaint();
   }

   /**
    * Gets the value of the hideGrid property
    * @return true if the grid is being hidden
    */
   public boolean getHideGrid()
   {
      return hideGrid;
   }

   
}