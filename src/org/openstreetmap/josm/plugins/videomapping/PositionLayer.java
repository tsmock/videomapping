package org.openstreetmap.josm.plugins.videomapping;


import java.sql.Time;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.*;

import static org.openstreetmap.josm.tools.I18n.*;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.plugins.videomapping.video.GPSVideoPlayer;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

//Basic rendering and GPS layer interaction
public class PositionLayer extends Layer implements MouseListener,MouseMotionListener {
    private List<WayPoint> ls;
    public GpsPlayer player;
    private boolean dragIcon=false; //do we move the icon by hand?
    private WayPoint iconPosition;
    private Point mouse;
    private ImageIcon icon;
    private SimpleDateFormat mins;
    private SimpleDateFormat ms;
    private GPSVideoPlayer gps;
        
    public PositionLayer(String name, final List<WayPoint> ls) {
        super(name);
        this.ls=ls;
        player= new GpsPlayer(ls);

        icon=ImageProvider.get("videomapping.png");
        mins = new SimpleDateFormat("hh:mm:ss:S");
        ms= new SimpleDateFormat("mm:ss");
        Main.map.mapView.addMouseListener(this);
        Main.map.mapView.addMouseMotionListener(this);							
        
    }


    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public Object getInfoComponent() {
        return null;
    }

    @Override
    public Component[] getMenuEntries() {
        return new Component[]{
                new JMenuItem(LayerListDialog.getInstance().createShowHideLayerAction(this)),
                new JMenuItem(LayerListDialog.getInstance().createDeleteLayerAction(this)),
                new JSeparator(),
                //TODO here my stuff
                new JSeparator(),
                new JMenuItem(new LayerListPopup.InfoAction(this))};//TODO here infos about the linked videos
    }
      


    @Override
    public String getToolTipText() {
        return tr("Shows current position in the video");
    }

    // no merging necessary
    @Override
    public boolean isMergable(Layer arg0) {
        return false;
    }

    @Override
    public void mergeFrom(Layer arg0) {
        
    }

    
    
    @Override
    //Draw the current position, infos, waypoints
    public void paint(Graphics2D g, MapView map, Bounds bound) {
        Point p;
        //TODO Source out redundant calculations
        //TODO make icon transparent
        //draw all GPS points
        g.setColor(Color.GREEN);
        for(WayPoint n: ls) {
            p = Main.map.mapView.getPoint(n.getEastNorth());
            g.drawOval(p.x - 2, p.y - 2, 4, 4);
            }
        //draw surrounding points
        g.setColor(Color.YELLOW);
        if(player.getPrev()!=null)
        {
            p = Main.map.mapView.getPoint(player.getPrev().getEastNorth());
            g.drawOval(p.x - 2, p.y - 2, 4, 4);
            Point p2 = Main.map.mapView.getPoint(player.getCurr().getEastNorth());
            g.drawLine(p.x, p.y, p2.x, p2.y);
        }
        if(player.getNext()!=null)
        {
            p = Main.map.mapView.getPoint(player.getNext().getEastNorth());
            g.drawOval(p.x - 2, p.y - 2, 4, 4);
            Point p2 = Main.map.mapView.getPoint(player.getCurr().getEastNorth());
            g.drawLine(p.x, p.y, p2.x, p2.y);
        }
        //draw interpolated points
        g.setColor(Color.CYAN);
        g.setBackground(Color.CYAN);
        LinkedList<WayPoint> ipo=(LinkedList<WayPoint>) player.getInterpolatedLine(5);
        for (WayPoint wp : ipo) {
            p=Main.map.mapView.getPoint(wp.getEastNorth());
            g.fillArc(p.x, p.y, 4, 4, 0, 360);
            //g.drawOval(p.x - 2, p.y - 2, 4, 4);
        }
        //draw cam icon
        g.setColor(Color.RED);
        if(dragIcon)
        {
            if(iconPosition!=null)
            {
                p=Main.map.mapView.getPoint(iconPosition.getEastNorth());
                icon.paintIcon(null, g, p.x-icon.getIconWidth()/2, p.y-icon.getIconHeight()/2);				
                g.drawString(mins.format(iconPosition.getTime()),p.x-10,p.y-10);
            }
        }
        else
        {
            if (player.getCurr()!=null){
            p=Main.map.mapView.getPoint(player.getCurr().getEastNorth());
            icon.paintIcon(null, g, p.x-icon.getIconWidth()/2, p.y-icon.getIconHeight()/2);			
            g.drawString(ms.format(player.getRelativeTime()),p.x-10,p.y-10);
            }
        }
    }
    
    private WayPoint getNearestWayPoint(Point mouse)
    {
        final int MAX=10;
        Point p;
        Rectangle rect = new Rectangle(mouse.x-MAX/2,mouse.y-MAX/2,MAX,MAX);
        //iterate through all possible notes
        for(WayPoint n : ls) //TODO this is not very clever, what better way to find this WP?
        {
            p = Main.map.mapView.getPoint(n.getEastNorth());
            if (rect.contains(p))
            {				
                return n;
            }
            
        }
        return null;
        
    }
    
    //upper left corner like rectangle
    private Rectangle getIconRect()
    {
        Point p = Main.map.mapView.getPoint(player.getCurr().getEastNorth());
        return new Rectangle(p.x-icon.getIconWidth()/2,p.y-icon.getIconHeight()/2,icon.getIconWidth(),icon.getIconHeight());
    }


    @Override
    public void visitBoundingBox(BoundingXYVisitor arg0) {
        // TODO don't know what to do here

    }

    public void mouseClicked(MouseEvent e) {		
    }

    public void mouseEntered(MouseEvent arg0) {	
    }

    public void mouseExited(MouseEvent arg0) {
    }

    //init drag&drop
    public void mousePressed(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON1) {
            //is it on the cam icon?
            if (player.getCurr()!=null)
            {
                if (getIconRect().contains(e.getPoint()))
                {
                    mouse=e.getPoint();
                    dragIcon=true;
                    //ani.cancel();
                }
            }
        }
        
    }
    
    //
    public void mouseReleased(MouseEvent e) {		
        //only leftclicks on our layer
        if(e.getButton() == MouseEvent.BUTTON1) {
            if(dragIcon)
            {
                dragIcon=false;
            }
            else
            {
                //simple click
                WayPoint wp = getNearestWayPoint(e.getPoint());
                if(wp!=null)
                {
                    player.jump(wp);
                    if(gps!=null) gps.notifyGPSClick();
                }
            }
            Main.map.mapView.repaint();
        }
        
    }
    
    //slide and restrict during movement
    public void mouseDragged(MouseEvent e) {		
        if(dragIcon)
        {			
            mouse=e.getPoint();
            //restrict to GPS track
            iconPosition=player.getInterpolatedWaypoint(mouse);

            Main.map.mapView.repaint();
        }
    }

    //visualize drag&drop
    public void mouseMoved(MouseEvent e) {		
        if (player.getCurr()!=null)
        {						
            if (getIconRect().contains(e.getPoint()))
            {
                Main.map.mapView.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            else
            {
                Main.map.mapView.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
        
    }

    public void setGPSPlayer(GPSVideoPlayer player) {
        this.gps = player;
        
    }
}
