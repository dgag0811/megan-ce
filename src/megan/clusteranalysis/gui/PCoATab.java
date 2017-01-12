/*
 *  Copyright (C) 2017 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.clusteranalysis.gui;

import javafx.geometry.Point3D;
import jloda.graph.*;
import jloda.graphview.*;
import jloda.gui.GraphViewPopupListener;
import jloda.gui.PopupMenu;
import jloda.gui.director.IDirector;
import jloda.gui.find.IObjectSearcher;
import jloda.gui.find.NodeLabelSearcher;
import jloda.gui.find.SearchManager;
import jloda.phylo.PhyloGraph;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import megan.clusteranalysis.ClusterViewer;
import megan.clusteranalysis.GUIConfiguration;
import megan.clusteranalysis.pcoa.ComputeEllipse;
import megan.clusteranalysis.pcoa.Ellipse;
import megan.clusteranalysis.pcoa.PCoA;
import megan.clusteranalysis.pcoa.Utilities;
import megan.clusteranalysis.pcoa.geom3d.Matrix3D;
import megan.clusteranalysis.pcoa.geom3d.Vector3D;
import megan.clusteranalysis.tree.Distances;
import megan.clusteranalysis.tree.Taxa;
import megan.fx.NotificationsInSwing;
import megan.viewer.MainViewer;
import megan.viewer.ViewerBase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * tab that shows a PCoA plot of the data
 * Daniel Huson, 5.2010, 4.2015
 */
public class PCoATab extends JPanel implements ITab {
    private final double COORDINATES_SCALE_FACTOR = 10000;

    private final ClusterViewer clusterViewer;
    private final ViewerBase graphView;
    private final PhyloGraph graph;
    private PCoA pcoa;
    private int firstPC = 0;
    private int secondPC = 1;
    private int thirdPC = 2;

    boolean flipH = false;
    boolean flipV = false;

    private boolean showAxes = ProgramProperties.get("ShowPCoAAxes", true);

    private boolean showBiPlot = ProgramProperties.get("ShowBiPlot", false);
    private boolean showTriPlot = ProgramProperties.get("ShowTriPlot", false);

    private boolean showGroupsAsEllipses = ProgramProperties.get("ShowGroups", true);
    private boolean showGroupsAsConvexHulls = ProgramProperties.get("ShowGroupsAsConvexHulls", true);

    private final NodeSet biplotNodes;
    private final EdgeSet biplotEdges;

    private final NodeSet triplotNodes;
    private final EdgeSet triplotEdges;

    private final NodeSet convexHullCenters;
    private final EdgeSet convexHullEdges;
    private final ArrayList<Ellipse> ellipses;

    private int biplotSize = ProgramProperties.get("BiplotSize", 3);
    private int triplotSize = ProgramProperties.get("TriplotSize", 3);
    private Pair<String, double[]>[] biplot;
    private Pair<String, double[]>[] triplot;
    private final NodeArray<Point3D> node2point3D; // for external use


    // 3D figure:
    private final Matrix3D transformation3D;
    private final NodeArray<Vector3D> node2vector; // used only in this class
    private boolean is3dMode = false;

    private final IObjectSearcher searcher;

    public long lastSynced = 0;

    /**
     * constructor
     *
     * @param parent
     */
    public PCoATab(final ClusterViewer parent) {
        this.clusterViewer = parent;
        graphView = new ViewerBase(parent.getDir(), new PhyloTree(), false) {
            public JFrame getFrame() {
                return null;
            }

            public boolean isShowFindToolBar() {
                return parent.isShowFindToolBar();
            }

            public void setShowFindToolBar(boolean showFindToolBar) {
                parent.setShowFindToolBar(showFindToolBar);
            }

            public SearchManager getSearchManager() {
                return parent.getSearchManager();
            }

            public void drawScaleBar(Graphics2D gc, Rectangle rect) {
                PCoATab.this.drawScaleBar(this, gc, rect);
            }

            public java.util.Collection<Integer> getSelectedIds() {
                return super.getSelectedIds();
            }

            public void fitGraphToWindow() {
                Dimension size = getScrollPane().getSize();
                if (size.getWidth() == 0 || size.getHeight() == 0) {
                    try {

                        Runnable runnable = new Runnable() {
                            public void run() {
                                PCoATab.this.validate();
                                getScrollPane().validate();
                            }
                        };
                        if (SwingUtilities.isEventDispatchThread()) {
                            System.err.println("RUN");
                            runnable.run(); // already in the swing thread, just run
                        } else {
                            System.err.println("INVOKE");
                            SwingUtilities.invokeAndWait(runnable);
                        }
                    } catch (InterruptedException | InvocationTargetException e) {
                        Basic.caught(e);
                    }
                    size = getScrollPane().getSize();
                }
                if (getGraph().getNumberOfNodes() > 0) {
                    // need more width than in GraphView
                    trans.fitToSize(new Dimension(Math.max(100, size.width - 300), Math.max(50, size.height - 200)));
                } else {
                    trans.fitToSize(new Dimension(0, 0));
                }
                centerGraph();
            }

            public void centerGraph() {
                final JScrollBar hScrollBar = getScrollPane().getHorizontalScrollBar();
                final JScrollBar vScrollBar = getScrollPane().getVerticalScrollBar();

                hScrollBar.setValue((hScrollBar.getMaximum() - hScrollBar.getModel().getExtent() + hScrollBar.getMinimum()) / 2);
                vScrollBar.setValue((vScrollBar.getMaximum() - vScrollBar.getModel().getExtent() + vScrollBar.getMinimum()) / 2);

                // weird, but the following two lines prevent the plot from sometimes appearing half a window too low in the window: todo: doesn't work
                hScrollBar.setValue(hScrollBar.getValue() + 1);
                vScrollBar.setValue(vScrollBar.getValue() + 1);
            }

            public void resetViews() {
            }

            public void paint(Graphics g) {
                super.paint(g);

                if (showGroupsAsEllipses) {
                    for (Ellipse ellipse : ellipses) {
                        ellipse.paint(g, trans);
                    }
                }
            }
        };
        graph = (PhyloGraph) graphView.getGraph();
        graphView.setCanvasColor(Color.WHITE);

        graphView.trans.setLockXYScale(true);
        graphView.trans.setTopMargin(120);
        graphView.trans.setBottomMargin(120);
        graphView.trans.setLeftMargin(200);
        graphView.trans.setRightMargin(300);

        graphView.setFixedNodeSize(true);
        graphView.setAllowMoveInternalEdgePoints(false);
        graphView.setAllowMoveNodes(false);
        graphView.setAllowMoveInternalEdgePoints(false);
        graphView.setAllowRotation(false);

        graphView.setAutoLayoutLabels(true);
        graphView.setAllowEdit(false);

        biplotNodes = new NodeSet(graph);
        biplotEdges = new EdgeSet(graph);

        triplotNodes = new NodeSet(graph);
        triplotEdges = new EdgeSet(graph);

        convexHullCenters = new NodeSet(graph);
        convexHullEdges = new EdgeSet(graph);
        ellipses = new ArrayList<>();

        transformation3D = new Matrix3D();
        node2vector = new NodeArray<>(graph);
        node2point3D = new NodeArray<Point3D>(graph);
        setLayout(new BorderLayout());
        add(graphView.getScrollPane(), BorderLayout.CENTER);

        this.setPreferredSize(new Dimension(0, 0));

        graphView.getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        graphView.getScrollPane().setWheelScrollingEnabled(false);
        graphView.getScrollPane().setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        graphView.trans.removeAllChangeListeners();

        graphView.trans.addChangeListener(new ITransformChangeListener() {
            public void hasChanged(Transform trans) {
                graphView.recomputeMargins();
            }
        });

        graphView.getScrollPane().addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent event) {
                if (graphView.getScrollPane().getSize().getHeight() > 400 && graphView.getScrollPane().getSize().getWidth() > 400)
                    graphView.fitGraphToWindow();
                else
                    graphView.trans.fireHasChanged();
            }
        });

        graphView.addNodeActionListener(new NodeActionAdapter() {
            public void doMoveLabel(NodeSet nodes) {
                for (Node v = nodes.getFirstElement(); v != null; v = nodes.getNextElement(v)) {
                    graphView.setLabelLayout(v, NodeView.USER);
                }
            }

            public void doSelect(NodeSet nodes) {
                Set<String> samples = new HashSet<>();
                for (Node v : nodes) {
                    if (!biplotNodes.contains(v) && !triplotNodes.contains(v)) {
                        String label = graphView.getTree().getLabel(v);
                        if (label != null)
                            samples.add(label);
                    }
                }

                final boolean allowMove = (graphView.getSelectedNodes().equals(graph.getUnhiddenSubset(biplotNodes))
                        || graphView.getSelectedNodes().equals(graph.getUnhiddenSubset(triplotNodes))) && !isIs3dMode();
                graphView.setAllowMoveNodes(allowMove);
                graphView.getDocument().getSampleSelection().setSelected(samples, true);
                clusterViewer.updateView(IDirector.ENABLE_STATE);
            }

            public void doDeselect(NodeSet nodes) {
                Set<String> samples = new HashSet<>();
                for (Node v : nodes) {
                    if (!biplotNodes.contains(v) && !triplotNodes.contains(v)) {
                        String label = graphView.getTree().getLabel(v);
                        if (label != null)
                            samples.add(label);
                    }
                }
                final boolean allowMove = (graphView.getSelectedNodes().equals(graph.getUnhiddenSubset(biplotNodes))
                        || graphView.getSelectedNodes().equals(graph.getUnhiddenSubset(triplotNodes)));

                graphView.setAllowMoveNodes(allowMove);
                graphView.getDocument().getSampleSelection().setSelected(samples, false);
                clusterViewer.updateView(IDirector.ENABLE_STATE);
            }

            public void doClick(NodeSet nodes, int clicks) {
                if (clicks == 2 && nodes.size() > 0) {
                    final NodeSet nodesToSelect = new NodeSet(graph);

                    final boolean select = graphView.getSelected(nodes.getFirstElement());

                    if (clusterViewer.getGroup2Nodes().size() > 0) { // select all groups
                        for (Node v : nodes) {
                            final String joinId = graphView.getDocument().getSampleAttributeTable().getGroupId(graph.getLabel(v));
                            // todo: implement this
                        }
                    }
                    graphView.setSelected(nodesToSelect, select);
                }
            }

            public void doClickLabel(NodeSet nodes, int clicks) {
                doClick(nodes, clicks);
            }
        });

        final JPopupMenu panelPopupMenu = new PopupMenu(GUIConfiguration.getPanelPopupConfiguration(), parent.getCommandManager());

        graphView.addPanelActionListener(new PanelActionListener() {
            @Override
            public void doMouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.isPopupTrigger()) {
                    panelPopupMenu.show(PCoATab.this, mouseEvent.getX(), mouseEvent.getY());
                }
            }
        });

        ((GraphViewListener) graphView.getGraphViewListener()).setAllowSelectConnectedComponent(true);

        final Single<Point> previousLocation = new Single<>();
        graphView.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                previousLocation.set(e.getLocationOnScreen());
            }

            public void mouseReleased(MouseEvent e) {
                super.mousePressed(e);
                if (previousLocation.get() != null) {
                    if (showGroupsAsConvexHulls && !clusterViewer.isLocked()) {
                        // computeConvexHullsAndEllipsesForGroups(clusterViewer.getGroup2Nodes());
                        updateTransform(is3dMode);
                    }
                    previousLocation.set(null);
                }
            }
        });
        graphView.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
                if (isIs3dMode() && !e.isShiftDown() && !e.isAltDown() && !e.isAltGraphDown()) {
                    int h = e.getLocationOnScreen().x - previousLocation.get().x;
                    int v = e.getLocationOnScreen().y - previousLocation.get().y;
                    previousLocation.set(e.getLocationOnScreen());
                    if (h != 0) {
                        transformation3D.rotateY(0.02 * h);
                    }
                    if (v != 0) {
                        //if(false)
                        transformation3D.rotateX(-0.02 * v);
                    }
                    if (h != 0 || v != 0) {
                        updateTransform(is3dMode);
                        if ((showGroupsAsConvexHulls || showGroupsAsEllipses) && !clusterViewer.isLocked()) {
                            computeConvexHullsAndEllipsesForGroups(clusterViewer.getGroup2Nodes());
                            // updateConvexHullOfGroups(clusterViewer.getGroup2Nodes());
                        }
                    }
                }
            }
        });

        getGraphView().setPopupListener(new GraphViewPopupListener(getGraphView(),
                megan.clusteranalysis.GUIConfiguration.getNodePopupConfiguration(),
                megan.clusteranalysis.GUIConfiguration.getEdgePopupConfiguration(),
                megan.clusteranalysis.GUIConfiguration.getPanelPopupConfiguration(), clusterViewer.getCommandManager()));


        graphView.addKeyListener(new KeyAdapter() {
            /**
             * Invoked when a key has been pressed.
             *
             * @param e
             */
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                // System.err.println("PRESSED: " + e);
            }
        });

        searcher = new NodeLabelSearcher(clusterViewer.getFrame(), "PCoA", graphView);
    }

    /**
     * get the associated graph view
     *
     * @return graph view
     */
    public ViewerBase getGraphView() {
        return graphView;
    }

    /**
     * compute the plot and set the data
     *
     * @param samples
     * @param distances
     */
    public void setData(Taxa samples, Distances distances) {
        graphView.setFont(ProgramProperties.get(ProgramProperties.DEFAULT_FONT, getFont()));

        transformation3D.identity();
        graph.deleteAllEdges();
        graph.deleteAllNodes();

        biplotNodes.clear();
        biplotEdges.clear();

        triplotNodes.clear();
        triplotEdges.clear();

        try {
            if (distances != null) {  // if distances given, rerun calculation, otherwise just rebuild graph (after replace of PCs)
                pcoa = new PCoA(samples, distances);
                runPCoA(pcoa);
            }
            if (!pcoa.isDone())
                throw new Exception("PCoA computation: failed");
            if (pcoa.getNumberOfPositiveEigenValues() < 2)
                throw new Exception("PCoA computation: too few positive eigenvalues");

            {
                NodeSet selectedNodes = clusterViewer.getParentViewer().getSelectedNodes();
                HashMap<String, int[]> class2counts = new HashMap<>();
                for (Node v : selectedNodes) {
                    String className = clusterViewer.getParentViewer().getLabel(v); // todo: need to get proper name
                    NodeData nodeData = (NodeData) v.getData();
                    class2counts.put(className, nodeData.getSummarized());
                }

                if (class2counts.size() >= 2) {
                    pcoa.computeLoadingVectorsBiPlot(samples.size(), class2counts);
                }

                {
                    List<String> sampleNames = new ArrayList<>(samples.size());
                    for (int t = 1; t <= samples.size(); t++) {
                        String name = samples.getLabel(t);
                        sampleNames.add(name);
                    }
                    HashMap<String, float[]> attribute2counts = clusterViewer.getDir().getDocument().getSampleAttributeTable().getNumericalAttributes(sampleNames);

                    if (attribute2counts.size() > 0) {
                        pcoa.computeLoadingVectorsTriPlot(samples.size(), attribute2counts);
                    }
                }
            }
            // System.err.println("Stress (3D): " + stress3D);
            // firstVariationExplained=pcoa.getVariationExplained(firstPC);
            // System.err.println("Variation explained by PC"+(firstPC+1)+": "+firstVariationExplained);
            // secondVariationExplained=pcoa.getVariationExplained(secondPC);
            //  System.err.println("Variation explained by PC"+(secondPC+1)+": "+secondVariationExplained);


            // compute points:
            for (int t = 1; t <= samples.size(); t++) {
                String name = samples.getLabel(t);
                double[] coordinates = pcoa.getProjection(firstPC, secondPC, thirdPC, name);
                final Node v = graph.newNode();
                graph.setLabel(v, name);
                final NodeView nv = graphView.getNV(v);
                nv.setLabel(graphView.getDocument().getSampleLabelGetter().getLabel(name));
                nv.setLabelVisible(clusterViewer.isShowLabels());
                graphView.setLocation(v, (flipH ? -1 : 1) * COORDINATES_SCALE_FACTOR * coordinates[0],
                        (flipV ? -1 : 1) * COORDINATES_SCALE_FACTOR * coordinates[1]);
                // nv.setLabelLayoutFromAngle(Geometry.computeAngle(nv.getLocation()));
                final double z = coordinates.length >= 2 ? COORDINATES_SCALE_FACTOR * coordinates[2] : 0;
                node2vector.set(v, new Vector3D(graphView.getLocation(v).getX(), graphView.getLocation(v).getY(), z));
                node2point3D.set(v, new Point3D(graphView.getLocation(v).getX(), graphView.getLocation(v).getY(), z));

                nv.setWidth(clusterViewer.getNodeRadius());
                nv.setHeight(clusterViewer.getNodeRadius());
                nv.setColor(Color.BLACK);
                nv.setBackgroundColor(Color.BLACK);
                nv.setShape(NodeView.OVAL_NODE);
            }

            // compute biplot arrows:
            computeBiPlotVectors(getBiplotSize());
            if (!isShowBiPlot())
                setShowBiPlot(false);

            computeTriPlotVectors(getTriplotSize());
            if (!isShowTriPlot())
                setShowTriPlot(false);

            clusterViewer.getStatusBar().setText2(clusterViewer.getStatusBar().getText2() + " Eigenvalues: " + Basic.toString(pcoa.getEigenValues(), ","));
            // System.err.println("" + item.get() + "=(" + 100 * item.get(0).floatValue() + "," + 100 * item.get(1).floatValue() + ")");
            graphView.trans.setCoordinateRect(graphView.getBBox());
            graphView.fitGraphToWindow();
            graphView.getScrollPane().revalidate();
        } catch (Exception ex) {
            NotificationsInSwing.showError(MainViewer.getLastActiveFrame(), "PCoA calculation failed: " + ex);
        }
    }

    /**
     * run the MDS code
     *
     * @return items
     */
    private void runPCoA(final PCoA pcoa) throws CanceledException {
        ProgressListener progressListener = clusterViewer.getDir().getDocument().getProgressListener();
        if (progressListener == null)
            progressListener = new ProgressSilent();

        pcoa.calculateClassicMDS(progressListener);
    }

    public PCoA getPCoA() {
        return pcoa;
    }

    public int getFirstPC() {
        return firstPC;
    }

    public void setFirstPC(int firstPC) {
        this.firstPC = firstPC;
    }

    public int getSecondPC() {
        return secondPC;
    }

    public void setSecondPC(int secondPC) {
        this.secondPC = secondPC;
    }

    public int getThirdPC() {
        return thirdPC;
    }

    public void setThirdPC(int thirdPC) {
        this.thirdPC = thirdPC;
    }

    public boolean isFlipH() {
        return flipH;
    }

    public void setFlipH(boolean flipH) {
        this.flipH = flipH;
    }

    public boolean isFlipV() {
        return flipV;
    }

    public void setFlipV(boolean flipV) {
        this.flipV = flipV;
    }

    public boolean isShowBiPlot() {
        return showBiPlot;
    }

    public void setShowBiPlot(boolean showBiPlot) {
        this.showBiPlot = showBiPlot;
        ProgramProperties.put("ShowBiPlot", showBiPlot);
        if (showBiPlot)
            computeBiPlotVectors(biplotSize);

        for (Node v : biplotNodes) {
            graph.setHidden(v, !showBiPlot);
        }
        for (Edge e : biplotEdges) {
            graph.setHidden(e, !showBiPlot);
        }
    }

    public boolean isShowTriPlot() {
        return showTriPlot;
    }

    public void setShowTriPlot(boolean showTriPlot) {
        this.showTriPlot = showTriPlot;
        ProgramProperties.put("ShowTriPlot", showTriPlot);
        if (showTriPlot)
            computeTriPlotVectors(triplotSize);

        for (Node v : triplotNodes) {
            graph.setHidden(v, !showTriPlot);
        }
        for (Edge e : triplotEdges) {
            graph.setHidden(e, !showTriPlot);
        }
    }

    public int getBiplotSize() {
        return biplotSize;
    }

    public void setBiplotSize(int biplotSize) {
        if (biplotSize > 0)
            showBiPlot = true;
        else {
            setShowBiPlot(false);
            return;
        }
        this.biplotSize = biplotSize;
        if (biplotSize > 0)
            ProgramProperties.put("BiplotSize", biplotSize);
        computeBiPlotVectors(biplotSize);
    }

    public void setTriplotSize(int triplotSize) {
        if (triplotSize > 0)
            showTriPlot = true;
        else {
            setShowTriPlot(false);
            return;
        }
        this.triplotSize = triplotSize;
        if (triplotSize > 0)
            ProgramProperties.put("TriplotSize", triplotSize);
        computeTriPlotVectors(triplotSize);
    }

    public int getTriplotSize() {
        return triplotSize;
    }

    public void setShowGroupsAsConvexHulls(boolean showGroupsAsConvexHulls) {
        this.showGroupsAsConvexHulls = showGroupsAsConvexHulls;
        ProgramProperties.put("ShowGroupsAsConvexHulls", showGroupsAsConvexHulls);
        for (Iterator<Edge> it = graph.edgeIteratorIncludingHidden(); it.hasNext(); ) {
            Edge e = it.next();
            if (convexHullEdges.contains(e))
                graph.setHidden(e, !showGroupsAsConvexHulls);
        }
    }

    public boolean isShowGroupsAsEllipses() {
        return showGroupsAsEllipses;
    }

    public void setShowGroupsAsEllipses(boolean showGroupsAsEllipses) {
        this.showGroupsAsEllipses = showGroupsAsEllipses;
        ProgramProperties.put("ShowGroups", showGroupsAsEllipses);
    }

    public boolean isShowGroupsAsConvexHulls() {
        return showGroupsAsConvexHulls;
    }

    /**
     * compute the scale factor to be used when drawing loadings
     *
     * @param vector
     * @return
     */
    private double computeLoadingsScaleFactor(double[] vector) {
        if (vector.length >= 2) {
            final double length = Math.sqrt(Geometry.squaredDistance(0, 0, vector[0], vector[1]));
            if (length > 0) {
                final Rectangle2D bbox = graphView.getBBox();
                return 0.2 * Math.min(bbox.getWidth(), bbox.getHeight()) / (length);
            }
        }
        return 1;
    }

    /**
     * computes the convex hulls and ellipses of all groups of nodes
     *
     * @param group2Nodes
     */
    public void computeConvexHullsAndEllipsesForGroups(final Map<String, LinkedList<Node>> group2Nodes) {
        synchronized (convexHullEdges) {
            {
                final Set<Edge> edgeToDelete = new HashSet<>();
                for (final Iterator<Edge> it = graph.edgeIteratorIncludingHidden(); it.hasNext(); ) {
                    Edge e = it.next();
                    if (convexHullEdges.contains(e)) {
                        edgeToDelete.add(e);
                    }
                }

                for (Edge e : edgeToDelete) {
                    graph.deleteEdge(e);
                }
                for (Edge e = graph.getFirstEdge(); e != null; e = e.getNext()) {
                    try {
                        graph.checkOwner(e);
                    } catch (NotOwnerException ex) {
                        Basic.caught(ex);
                    }
                }
            }
            convexHullEdges.clear();
            ellipses.clear();

            for (Node v : convexHullCenters) {
                graph.deleteNode(v);
            }
            convexHullCenters.clear();

            for (String joinId : group2Nodes.keySet()) {
                final LinkedList<Node> nodes = group2Nodes.get(joinId);
                if (nodes.size() > 1) {
                    ArrayList<Point2D> points = new ArrayList<>(nodes.size());
                    long r = 0;
                    long g = 0;
                    long b = 0;

                    for (Node v : nodes) {
                        if (v == null)
                            continue;

                        final Point2D aPt = new PointNode(Math.round(graphView.getLocation(v).getX()), Math.round(graphView.getLocation(v).getY()), v);
                        points.add(aPt);
                        final String sample = graph.getLabel(v);
                        final Color color = graphView.getDocument().getChartColorManager().getSampleColor(sample);
                        r += color.getRed();
                        g += color.getGreen();
                        b += color.getBlue();
                    }
                    if (points.size() > 1) {
                        final Color color = new Color((int) (r / nodes.size()), (int) (g / nodes.size()), (int) (b / nodes.size()));
                        final ArrayList<Point2D> hull = ConvexHull.quickHull(points);

                        if (showGroupsAsEllipses) {
                            final ArrayList<Point2D> points4 = new ArrayList<>(4 * points.size());
                            for (Point2D p : points) {
                                points4.add(new Point2D.Double(p.getX() - 32, p.getY() - 32));
                                points4.add(new Point2D.Double(p.getX() - 32, p.getY() + 32));
                                points4.add(new Point2D.Double(p.getX() + 32, p.getY() - 32));
                                points4.add(new Point2D.Double(p.getX() + 32, p.getY() + 32));
                            }
                            final Ellipse ellipse = ComputeEllipse.computeEllipse(points4);
                            ellipse.setColor(color);
                            ellipses.add(ellipse);
                        }

                        if (!showGroupsAsConvexHulls)
                            continue;

                        for (int i = 0; i < hull.size(); i++) {
                            final Node v = ((PointNode) hull.get(i > 0 ? i - 1 : hull.size() - 1)).getNode(); // prev node in polygon
                            final Node w = ((PointNode) hull.get(i)).getNode(); // current node in polygon
                            if (v != w) {
                                final Edge e = graph.newEdge(v, w, EdgeView.UNDIRECTED);
                                graphView.setColor(e, color);
                                graphView.setDirection(e, EdgeView.UNDIRECTED);
                                convexHullEdges.add(e);
                            }
                        }
                        final Node center = graph.newNode();
                        convexHullCenters.add(center);
                        graphView.setLocation(center, computeCenter(points));
                        node2vector.set(center, new Vector3D(graphView.getLocation(center).getX(), graphView.getLocation(center).getY(), 0));
                        graphView.setWidth(center, 0);
                        graphView.setHeight(center, 0);
                        for (Node v : nodes) {
                            Edge e = graph.newEdge(center, v);
                            graphView.setDirection(e, EdgeView.UNDIRECTED);
                            graphView.setLineWidth(e, 0);
                            graphView.setColor(e, null);
                            convexHullEdges.add(e);
                        }
                    }
                }
            }
        }
    }

    /**
     * computes the center for a set of points
     *
     * @param points
     * @return center
     */
    private Point2D computeCenter(ArrayList<Point2D> points) {
        final Point center = new Point(0, 0);
        if (points.size() > 0) {
            for (Point2D aPt : points) {
                center.x += (int) aPt.getX();
                center.y += (int) aPt.getY();
            }
            center.x /= points.size();
            center.y /= points.size();
        }
        return center;
    }

    /**
     * compute the given number of biplot vectors to show
     */
    private void computeBiPlotVectors(int numberOfBiplotVectorsToShow) {
        // delete all existing biplot nodes:
        for (Node v : biplotNodes)
            graph.deleteNode(v);
        biplotNodes.clear();
        biplotEdges.clear();
        biplot = null;

        // compute biplot arrows:
        final int top = Math.min(numberOfBiplotVectorsToShow, getPCoA().getLoadingVectorsBiPlot().size());
        final Color color = Colors.parseColor(ProgramProperties.get("BiPlotColorName", "darkseagreen"));

        if (top > 0) {
            final Node zero = graph.newNode();
            graphView.setLocation(zero, 0, 0);
            graphView.setShape(zero, NodeView.NONE_NODE);
            biplotNodes.add(zero);

            biplot = new Pair[getPCoA().getLoadingVectorsBiPlot().size()];
            for (int i = 0; i < getPCoA().getLoadingVectorsBiPlot().size(); i++) {
                final Pair<String, double[]> pair = getPCoA().getLoadingVectorsBiPlot().get(i);
                final double x = pair.getSecond()[firstPC];
                final double y = pair.getSecond()[secondPC];
                final double z = (thirdPC < pair.getSecond().length ? pair.getSecond()[thirdPC] : 0);
                biplot[i] = new Pair<>(pair.getFirst(), new double[]{x, y, z});
            }
            Arrays.sort(biplot, new Comparator<Pair<String, double[]>>() {
                @Override
                public int compare(Pair<String, double[]> a, Pair<String, double[]> b) {
                    double aSquaredLength = Utilities.getSquaredLength(a.getSecond());
                    double bSquaredLength = Utilities.getSquaredLength(b.getSecond());
                    if (aSquaredLength > bSquaredLength)
                        return -1;
                    else if (aSquaredLength < bSquaredLength)
                        return 1;
                    else
                        return a.getFirst().compareTo(b.getFirst());
                }
            });

            double scaleFactor = computeLoadingsScaleFactor(biplot[0].getSecond());

            for (int i = 0; i < top; i++) {
                final Pair<String, double[]> pair = biplot[i];
                final double x = (flipH ? -1 : 1) * scaleFactor * pair.getSecond()[0];
                final double y = (flipV ? -1 : 1) * scaleFactor * pair.getSecond()[1];
                final double z = scaleFactor * pair.getSecond()[2];
                final Node v = graph.newNode();
                biplotNodes.add(v);
                graph.setLabel(v, pair.getFirst());
                graphView.setLabel(v, pair.getFirst());
                final NodeView nv = graphView.getNV(v);
                nv.setLocation(x, y);
                node2vector.set(v, new Vector3D(x, y, z));

                nv.setLabelLayoutFromAngle(Geometry.computeAngle(nv.getLocation()));
                nv.setLabelColor(color);
                nv.setShape(NodeView.NONE_NODE);
                final Edge e = graph.newEdge(zero, v);
                biplotEdges.add(e);
                final EdgeView ev = graphView.getEV(e);
                ev.setDirection(EdgeView.DIRECTED);
                ev.setColor(color);
                graph.setInfo(e, EdgeView.DIRECTED);
            }
        }
        updateTransform(is3dMode); // without this, start off at wrong locations...
    }

    /**
     * compute the given number of triplot vectors to show
     */
    private void computeTriPlotVectors(int numberOfTriplotVectorsToShow) {
        // delete all existing triplot nodes:
        for (Node v : triplotNodes)
            graph.deleteNode(v);
        triplotNodes.clear();
        triplotEdges.clear();
        triplot = null;

        // compute triplot arrows:
        final int top = Math.min(numberOfTriplotVectorsToShow, getPCoA().getLoadingVectorsTriPlot().size());
        final Color color = Colors.parseColor(ProgramProperties.get("TriPlotColorName", "sandybrown"));


        if (top > 0) {
            final Node zero = graph.newNode();
            graphView.setLocation(zero, 0, 0);
            graphView.setShape(zero, NodeView.NONE_NODE);
            triplotNodes.add(zero);

            triplot = new Pair[getPCoA().getLoadingVectorsTriPlot().size()];
            for (int i = 0; i < getPCoA().getLoadingVectorsTriPlot().size(); i++) {
                final Pair<String, double[]> pair = getPCoA().getLoadingVectorsTriPlot().get(i);
                final double x = pair.getSecond()[firstPC];
                final double y = pair.getSecond()[secondPC];
                final double z = (thirdPC < pair.getSecond().length ? pair.getSecond()[thirdPC] : 0);
                triplot[i] = new Pair<>(pair.getFirst(), new double[]{x, y, z});
            }
            Arrays.sort(triplot, new Comparator<Pair<String, double[]>>() {
                @Override
                public int compare(Pair<String, double[]> a, Pair<String, double[]> b) {
                    double aSquaredLength = Utilities.getSquaredLength(a.getSecond());
                    double bSquaredLength = Utilities.getSquaredLength(b.getSecond());
                    if (aSquaredLength > bSquaredLength)
                        return -1;
                    else if (aSquaredLength < bSquaredLength)
                        return 1;
                    else
                        return a.getFirst().compareTo(b.getFirst());
                }
            });

            double scaleFactor = computeLoadingsScaleFactor(triplot[0].getSecond());


            for (int i = 0; i < top; i++) {
                final Pair<String, double[]> pair = triplot[i];
                final double x = (flipH ? -1 : 1) * scaleFactor * pair.getSecond()[0];
                final double y = (flipV ? -1 : 1) * scaleFactor * pair.getSecond()[1];
                final double z = scaleFactor * pair.getSecond()[2];
                final Node v = graph.newNode();
                triplotNodes.add(v);
                graph.setLabel(v, pair.getFirst());
                graphView.setLabel(v, pair.getFirst());
                final NodeView nv = graphView.getNV(v);
                nv.setLocation(x, y);
                node2vector.set(v, new Vector3D(x, y, z));
                triplot[i].setSecond(new double[]{x, y, z});

                nv.setLabelLayoutFromAngle(Geometry.computeAngle(nv.getLocation()));
                nv.setLabelColor(color);
                nv.setShape(NodeView.NONE_NODE);

                final Edge e = graph.newEdge(zero, v);
                triplotEdges.add(e);
                final EdgeView ev = graphView.getEV(e);
                ev.setDirection(EdgeView.DIRECTED);
                ev.setColor(color);
                graph.setInfo(e, EdgeView.DIRECTED);
            }
        }
        updateTransform(is3dMode); // without this, start off at wrong locations...
    }

    /**
     * apply 3d transformation matrix. If draw3D==true, modify colors etc to look 3D
     *
     * @param draw3D
     */
    public void updateTransform(boolean draw3D) {
        if (draw3D) {
            final SortedSet<Pair<Double, Node>> pairs = new TreeSet<>(new Pair<Double, Node>());

            for (Iterator<Node> it = graph.nodeIteratorIncludingHidden(); it.hasNext(); ) {
                final Node v = it.next();
                Vector3D vector = node2vector.get(v);
                if (vector != null) {
                    Vector3D newVector = new Vector3D(vector);
                    newVector.transform(transformation3D);
                    graphView.setLocation(v, newVector.get(0), newVector.get(1));
                    pairs.add(new Pair<>(newVector.get(2), v));
                    if (newVector.get(2) >= 0) {
                        graphView.setColor(v, Color.BLACK);
                    } else {
                        graphView.setColor(v, Color.LIGHT_GRAY);
                    }
                } else
                    pairs.add(new Pair<>(0d, v));

            }
            // this will make drawer draw graph from back to front
            List<Node> nodeOrder = new ArrayList<>(pairs.size());
            for (Pair<Double, Node> pair : pairs) {
                nodeOrder.add(pair.get2());
            }
            graph.reorderNodes(nodeOrder);
        } else // 2D
        {
            for (Iterator<Node> it = graph.nodeIteratorIncludingHidden(); it.hasNext(); ) {
                final Node v = it.next();
                graphView.setColor(v, Color.BLACK);
            }
        }

        graphView.repaint();
    }

    public Matrix3D getTransformation3D() {
        return transformation3D;
    }

    public boolean isIs3dMode() {
        return is3dMode;
    }

    public void set3dMode(boolean is3dMode) {
        this.is3dMode = is3dMode;
        if (!is3dMode)
            transformation3D.identity();
        if (is3dMode)
            System.err.println("3D mode: Control-click-drag to rotate");
    }

    /**
     * draws a centerAndScale bar
     */
    protected void drawScaleBar(GraphView graphView, Graphics2D gc, Rectangle rect) {
        final Font oldFont = gc.getFont();
        try {
            if (pcoa.isDone() && pcoa.getNumberOfPositiveEigenValues() >= 2) {
                final Rectangle grid = new Rectangle(rect.x + 40, rect.y + 20, rect.width - 60, rect.height - 40);

                gc.setStroke(new BasicStroke(1));

                if (!isIs3dMode()) {
                    {
                        gc.setFont(Font.decode("Dialog-PLAIN-12"));
                        String label = getTitle2D();
                        Dimension labelSize = Basic.getStringSize(gc, label, gc.getFont()).getSize();
                        gc.setColor(Color.DARK_GRAY);
                        gc.drawString(label, grid.x + grid.width / 2 - labelSize.width / 2, grid.y - 4);
                    }

                    if (isShowAxes()) {
                        gc.setFont(Font.decode("Dialog-PLAIN-11"));
                        gc.setColor(Color.LIGHT_GRAY);

                        final Point zeroDC = graphView.trans.w2d(0, 0);
                        final DecimalFormat tickNumberFormat = new DecimalFormat("#.####");

                        {
                            final double factor = COORDINATES_SCALE_FACTOR / pcoa.getEigenValues()[firstPC];
                            double step = 0.0000001d;
                            int jump = 5;
                            while (step < 100000 && graphView.trans.w2d(step * factor, 0).getX() - zeroDC.getX() < 50) {
                                step *= jump;
                                if (jump == 5)
                                    jump = 2;
                                else
                                    jump = 5;
                            }

                            for (Boolean top : Arrays.asList(true, false)) {
                                Integer v0;
                                if (top)
                                    v0 = Math.round(grid.y);
                                else
                                    v0 = Math.round(grid.y + grid.height);

                                gc.drawLine(grid.x, v0, grid.x + grid.width, v0);
                                for (Integer sign : Arrays.asList(-1, 1)) {
                                    for (int i = (sign == 1 ? 0 : 1); i < 1000; i++) {
                                        Point2D tickWC = new Point2D.Double(sign * i * step * factor, 0);
                                        Point tickDC = graphView.trans.w2d(tickWC);
                                        final String label = (i == 0 ? String.format("PC%d", firstPC + 1) : tickNumberFormat.format(sign * i * step));
                                        final Dimension labelSize = Basic.getStringSize(gc, label, gc.getFont()).getSize();
                                        if (tickDC.x - labelSize.width / 2 >= grid.x && tickDC.x + labelSize.width / 2 <= grid.x + grid.width) {
                                            gc.drawLine(tickDC.x, v0, tickDC.x, v0 + (top ? 2 : -2));
                                            if (!top)
                                                gc.drawString(label, tickDC.x - labelSize.width / 2, v0 + labelSize.height);
                                        }
                                        if (sign == -1 && tickDC.x <= grid.x || sign == 1 && tickDC.x >= grid.x + grid.width)
                                            break;
                                    }
                                }
                            }
                        }

                        {
                            final double factor = COORDINATES_SCALE_FACTOR / pcoa.getEigenValues()[secondPC];
                            double step = 0.0000001d;
                            int jump = 5;
                            while (step < 100000 && graphView.trans.w2d(0, step * factor).getY() - zeroDC.getY() < 50) {
                                step *= jump;
                                if (jump == 5)
                                    jump = 2;
                                else
                                    jump = 5;
                            }


                            int yTickStart = grid.y;

                            for (Boolean left : Arrays.asList(true, false)) {
                                Integer h0;
                                if (left)
                                    h0 = Math.round(grid.x);
                                else
                                    h0 = Math.round(grid.x + grid.width);

                                gc.drawLine(h0, grid.y, h0, grid.y + grid.height);
                                for (Integer sign : Arrays.asList(-1, 1)) {
                                    for (int i = (sign == 1 ? 0 : 1); i < 1000; i++) {
                                        Point2D tickWC = new Point2D.Double(0, sign * i * step * factor);
                                        Point tickDC = graphView.trans.w2d(tickWC);
                                        final String label = (i == 0 ? String.format("PC%d", secondPC + 1) : tickNumberFormat.format(sign * i * step));
                                        final Dimension labelSize = Basic.getStringSize(gc, label, gc.getFont()).getSize();
                                        if (tickDC.y - labelSize.height / 2 >= yTickStart && tickDC.y + labelSize.height / 2 <= grid.y + grid.height) {
                                            gc.drawLine(h0, tickDC.y, h0 + (left ? 2 : -2), tickDC.y);
                                            if (left)
                                                gc.drawString(label, h0 - labelSize.width - 2, tickDC.y + labelSize.height / 2);
                                        }
                                        if (sign == -1 && tickDC.y <= yTickStart || sign == 1 && tickDC.y >= grid.y + grid.height)
                                            break;
                                    }
                                }
                            }
                        }
                    }
                } else // three dimensional
                {
                    {
                        gc.setFont(Font.decode("Dialog-PLAIN-12"));
                        String label = getTitle3D();
                        Dimension labelSize = Basic.getStringSize(gc, label, gc.getFont()).getSize();
                        gc.setColor(Color.DARK_GRAY);
                        gc.drawString(label, grid.x + grid.width / 2 - labelSize.width / 2, grid.y - 4);
                    }
                    if (isShowAxes()) {
                        gc.setFont(Font.decode("Dialog-PLAIN-11"));

                        final Vector3D centerVector = new Vector3D(0, 0, 0);
                        centerVector.transform(transformation3D);

                        gc.setColor(Color.LIGHT_GRAY);
                        final Point center = graphView.trans.w2d(centerVector.get(0), centerVector.get(1));
                        final int axisLength = 50;

                        final Point2D worldOrig = graphView.trans.d2w(0, 0);
                        final Point2D worldX = graphView.trans.d2w(axisLength, 0);

                        {
                            Vector3D v = new Vector3D(worldX.getX() - worldOrig.getX(), 0, 0);
                            v.transform(transformation3D);
                            final Point point = graphView.trans.w2d(v.get(0), v.get(1));
                            gc.drawLine(center.x, center.y, point.x, point.y);
                            drawArrowHead(gc, center, point);
                            String label = String.format("PC%d", firstPC + 1);
                            gc.drawString(label, point.x, point.y);
                        }
                        {
                            Vector3D v = new Vector3D(0, -(worldX.getX() - worldOrig.getX()), 0);
                            v.transform(transformation3D);
                            final Point point = graphView.trans.w2d(v.get(0), v.get(1));
                            gc.drawLine(center.x, center.y, point.x, point.y);
                            drawArrowHead(gc, center, point);
                            String label = String.format("PC%d", secondPC + 1);
                            gc.drawString(label, point.x, point.y);
                        }
                        {
                            Vector3D v = new Vector3D(0, 0, worldX.getX() - worldOrig.getX());
                            v.transform(transformation3D);
                            final Point point = graphView.trans.w2d(v.get(0), v.get(1));
                            gc.drawLine(center.x, center.y, point.x, point.y);
                            drawArrowHead(gc, center, point);
                            String label = String.format("PC%d", thirdPC + 1);
                            gc.drawString(label, point.x, point.y);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // ignore
        }
        gc.setFont(oldFont);
    }

    public String getTitle2D() {
        return String.format("PCoA of %s using %s: PC %d (%.1f%%) vs PC %d (%.1f%%)",
                clusterViewer.getDataType(), clusterViewer.getEcologicalIndex(), (firstPC + 1), pcoa.getPercentExplained(firstPC), (secondPC + 1),
                pcoa.getPercentExplained(secondPC));
    }

    public String getTitle3D() {
        return String.format("PCoA of %s using %s: PC %d (%.1f%%) vs PC %d (%.1f%%) vs PC %d (%.1f%%)",
                clusterViewer.getDataType(), clusterViewer.getEcologicalIndex(), (firstPC + 1), pcoa.getPercentExplained(firstPC), (secondPC + 1),
                pcoa.getPercentExplained(secondPC), (thirdPC + 1), pcoa.getPercentExplained(thirdPC));
    }

    /**
     * Draw an arrow head.
     *
     * @param gc Graphics
     * @param vp Point
     * @param wp Point
     */
    public static void drawArrowHead(Graphics gc, Point vp, Point wp) {
        final Point diff = new Point(wp.x - vp.x, wp.y - vp.y);
        if (diff.x != 0 || diff.y != 0) {
            final int arrowLength = 5;
            final double arrowAngle = 2.2;
            double alpha = Geometry.computeAngle(diff);
            Point a = new Point(arrowLength, 0);
            a = Geometry.rotate(a, alpha + arrowAngle);
            a.translate(wp.x, wp.y);
            Point b = new Point(arrowLength, 0);
            b = Geometry.rotate(b, alpha - arrowAngle);
            b.translate(wp.x, wp.y);
            gc.drawLine(a.x, a.y, wp.x, wp.y);
            gc.drawLine(wp.x, wp.y, b.x, b.y);
        }
    }

    public String getLabel() {
        return "PCoA Plot";
    }

    public String getMethod() {
        return "PCoA";
    }

    /**
     * sync
     *
     * @param taxa
     * @param distances
     * @throws Exception
     */
    public void compute(Taxa taxa, Distances distances) throws Exception {
        if (graph.getNumberOfNodes() == 0) {
            System.err.println("Computing " + getLabel());
            getGraphView().setAutoLayoutLabels(true);
            setData(taxa, distances);
            getGraphView().setFixedNodeSize(true);
            getGraphView().resetViews();
            getGraphView().getScrollPane().revalidate();
            getGraphView().fitGraphToWindow();
            getGraphView().setFont(ProgramProperties.get(ProgramProperties.DEFAULT_FONT, getGraphView().getFont()));
            clusterViewer.addFormatting(getGraphView());
            clusterViewer.updateConvexHulls = true;
        }
    }

    /**
     * this tab has been selected
     */
    @Override
    public void activate() {
    }

    /**
     * this tab has been deselected
     */
    @Override
    public void deactivate() {

    }

    public void clear() {
        lastSynced = System.currentTimeMillis();
        graphView.getGraph().deleteAllNodes();
    }

    public boolean isSampleNode(Node v) {
        return !biplotNodes.contains(v) && !triplotNodes.contains(v) && getGraphView().getLabel(v) != null
                && getGraphView().getLabel(v).trim().length() > 0;
    }

    public boolean isBiplotNode(Node v) {
        return biplotNodes.contains(v);
    }

    public Point3D getPoint3D(Node v) {
        return node2point3D.get(v);
    }

    public Pair<String, double[]>[] getBiplot() {
        return biplot;
    }

    public Pair<String, double[]>[] getTriplot() {
        return triplot;
    }

    @Override
    public void updateView(String what) {
    }


    /**
     * zoom to fit
     */
    public void zoomToFit() {
        if (is3dMode) {
            getTransformation3D().identity();
            updateTransform(is3dMode);
        }
        graphView.fitGraphToWindow();
    }

    /**
     * zoom to selection
     */
    public void zoomToSelection() {
        graphView.zoomToSelection();
    }

    /**
     * gets the searcher associated with this tab
     *
     * @return searcher
     */
    @Override
    public IObjectSearcher getSearcher() {
        return searcher;
    }

    public boolean isShowAxes() {
        return this.showAxes;
    }

    public void setShowAxes(boolean showAxes) {
        this.showAxes = showAxes;
        ProgramProperties.put("ShowPCoAAxes", showAxes);
        clusterViewer.updateView(IDirector.ENABLE_STATE);
    }

    public boolean isApplicable() {
        return true;
    }

    public boolean needsUpdate() {
        return graph.getNumberOfNodes() == 0;
    }

    class PointNode extends Point2D.Double {
        private final Node v;

        public PointNode(double x, double y, Node v) {
            super(x, y);
            this.v = v;
        }

        public Node getNode() {
            return v;
        }
    }

}
