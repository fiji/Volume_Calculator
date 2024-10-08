/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2012 - 2024 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package volumeCalculator;
/*
Copyright (c) 2012, Peter C Marks and Maine Medical Center Research Institute
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
import ij3d.Utils;
import sc.fiji.skeletonize3D.Skeletonize3D_;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.jogamp.java3d.Appearance;
import org.jogamp.java3d.BranchGroup;
import org.jogamp.java3d.ColoringAttributes;
import org.jogamp.java3d.Group;
import org.jogamp.java3d.LineArray;
import org.jogamp.java3d.LineAttributes;
import org.jogamp.java3d.Node;
import org.jogamp.java3d.PointAttributes;
import org.jogamp.java3d.Shape3D;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Point3f;

import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.analyzeSkeleton.Vertex;

/**
 * <p>
 * A Java 3D representation (sceneGraph) of an Analyzed image of (typically)
 * a vessel network.
 * </p><p>
 * AnalyzeSkeleton has created a forest of trees (graphs). We traverse all the
 * edges in all the trees and create a Java 3D representation of those edges
 * using the LineArray Shape.
 * </p>
 * @author p c marks - Maine Medical Center Research Institute www.mcri.org
 *
 */
class AnalyzedGraph {

    /*
     * A set of static values that one can play around with.
     */
    private static float   INITIAL_SCALE = 1.5f;
    private static Color3f EDGE_POINT_COLOR = Utils.toColor3f(Color.yellow);
    private static Color3f TREE_POINT_COLOR = Utils.toColor3f(Color.MAGENTA);
    private static float   TREE_POINT_THICKNESS = 1.0f;
    private static final Color   EDGE_COLOR = Color.white;
    private static Color3f EDGE_COLOR_3f = Utils.toColor3f(EDGE_COLOR);
    private static float   EDGE_THICKNESS = 2.0f;
    private static float   VERTEX_THICKNESS = 2.0f;
    private static String  STATUS_BEGIN_CREATE_GRAPHIC = "Begin creating 3D graphic.";

    /* Handles the image that is being picked */
    private ImageProcessor ip;
    /* The two plugins that we use to do the skeletonization and analysis */
    private Skeletonize3D_ skeletonizer;
    private AnalyzeSkeleton_ analyzeSkeleton;
    //
    SkeletonResult skeletonResult;
    /*
     * AnalyzeSkeleton_ produces this graphical representation of the
     * vasculature. Many trees in this forest.
     */
    public SkeletonResult getSkeletonResult() {
        return skeletonResult;
    }
    private Graph[] forest;
    /**
     * This is the Java 3D scene tree and a subclass of 3D Viewer ContentNode
     **/
    private GraphContentNode sceneGraph;
    private BranchGroup treeBG;

    private Map<Integer,Set<Edge>> sliceGuide = new HashMap<Integer,Set<Edge>>();

    /**
     *
     */
    public Map<Integer,Set<Edge>> getSliceGuide() {
        return sliceGuide;
    }
    /**
     * The Java 3D scene tree corresponding to the Analysis output
     * @return GraphContentNode
     */
    public GraphContentNode getSceneGraph() {
        return sceneGraph;
    }
    private float width;
    private float height;
    private float depth;

    /**
     * Constructor
     *
     */
    public AnalyzedGraph() {
    }

    /**
     * Initialize this instance with a ImageJ ImagePlus. The image is
     * skeletonized and analyzed. The analysis structure - a tree - is used to create
     * a Java 3D scene tree (BranchGroup).
     *
     * @param imagePlus The image with which to initialize this instance.
     */
    void init(ImagePlus imagePlus) {

        // use the image dimensions for the canvas. Very important. These values
        // are used to scale the image appropriately. See point2point3f()
        width = imagePlus.getWidth();
        height = imagePlus.getHeight();
        depth = imagePlus.getStackSize();

        skeletonizer = new Skeletonize3D_();
        skeletonizer.setup("none", imagePlus);
        skeletonizer.run(ip);

        analyzeSkeleton = new AnalyzeSkeleton_();
        analyzeSkeleton.setup("none", imagePlus);
        // There are two run()'s in analyze skeleton; We use the non-UI one.
        skeletonResult =
                analyzeSkeleton.run(
                    AnalyzeSkeleton_.NONE,          // Prune Index
                    false,                          // prune ends?
                    false,                          // shortest path?
                    imagePlus,                      // The image to work on
                    true,                           // silent mode?
                    false);                         // verbose mode?
//        SkeletonResult skeletonResult =
//                analyzeSkeleton.run(AnalyzeSkeleton_.SHORTEST_BRANCH, imagePlus, true, false);

        // The SkeletonAnalyzer has tree graphs from which we will create
        // all of our Scene components.
        forest = skeletonResult.getGraph();
        ij.IJ.showStatus(STATUS_BEGIN_CREATE_GRAPHIC);
        construct(forest);
        constructSliceGuide(forest);
        ij.IJ.showStatus("");

    }

    void construct(Graph[] forest) {
        // The SkeletonAnalyzer has made tree graph structures from which
        // we will create all of our Scene components.
        // Create a scene tree and fill it up one tree at a time.
        sceneGraph = new GraphContentNode();
        // Do this so that the sceneGraph can return SceneGraphPaths
        sceneGraph.setCapability(BranchGroup.ENABLE_PICK_REPORTING);
        int graphCount = 0;

        // Traverse all the edges in all the trees.
        // The following algorithm is based on a similar algorithm that
        // appears in the AnalyzeSkeleton plugin by Ignacio Carrero
        for (Graph tree : forest) {

            // Skip those trees with no (zero) edges
            if (tree.getEdges().size() < 1) {
                continue;
            }
//            // Skip those trees with one or less vertices
//            if (tree.getVertices().size() < 1) {
//                continue;
//            }
            graphCount++;
            int vcount = 0;
            int ecount = 0;
            // Create empty stacks
            Stack<Vertex> stack = new Stack<Vertex>();
            Stack<Group> groupStack = new Stack<Group>(); // Java 3D Groups
            // Mark all vertices as non-visited
            for (final Vertex v : tree.getVertices()) {
                v.setVisited(false);
            }
            // Push the root onto the stack
            stack.push(tree.getEdges().get(0).getV1());

            // Create and push a BranchGroup for the tree onto its own stack
            // and enable the ability to return itself in a SceneGraphPath
            treeBG = new BranchGroup();
            treeBG.setCapability(BranchGroup.ENABLE_PICK_REPORTING);
            sceneGraph.addChild(treeBG);
            groupStack.push(treeBG);
            int visitOrder = 0;
            // Follow all the vertices and edges building the sceneGraph
            // as we go.
            while (!stack.empty()) {
                Vertex vertex = stack.pop();
                Group vertexGroup = groupStack.pop();
                // Has it been visited yet?
                if (!vertex.isVisited()) {

                    // If the vertex has not been visited yet, then
                    // the edge from the predecessor to this vertex
                    // is marked as TREE
                    // A vertex will be represented by a Group Node
                    UserData ud = new UserData(vertex);
                    vertexGroup.setUserData(ud);

                    PointAttributes attr = new PointAttributes();
                    attr.setPointSize(VERTEX_THICKNESS);
                    PointAttributes treePointAttr = new PointAttributes();
                    treePointAttr.setPointSize(TREE_POINT_THICKNESS);
                    Appearance appear = new Appearance();
                    appear.setPointAttributes(attr);

                    if (vertex.getPredecessor() != null) {
                        vertex.getPredecessor().setType(Edge.TREE);
                    }
                    // mark as visited
                    vertex.setVisited(true, visitOrder++);
                    vcount++;

                    ArrayList<Edge> previousEdges = new ArrayList<Edge>();

                    edgeLoop:
                    for (final Edge edge : vertex.getBranches()) {
                        /*
                         * Look for duplicate, triplicate, ... branches leaving this
                         * vertex. Permit only one or we'll have identical Java 3D
                         * LineArray's and we won't be able to distinguish one from
                         * the other when the user picks one.
                         */
                        for (Edge previousEdge : previousEdges) {
                            if (edge.equals(previousEdge)) {
                                continue edgeLoop;
                            }
                            if (edge.getV1().equals(previousEdge.getV1()) &&
                                    edge.getV2().equals(previousEdge.getV2())) {
                                continue edgeLoop;
                            }
                        }
                        previousEdges.add(edge);

                        // For the undefined branches:
                        // We push the unvisited vertices on the stack,
                        // and mark the edge to the others as BACK
                        if (edge.getType() != Edge.BACK) {
                            Vertex oppVertex = edge.getOppositeVertex(vertex);
                            if (!oppVertex.isVisited()) {
                                ecount++;

                                Vertex v1 = edge.getV1();
                                Vertex v2 = edge.getV2();
                                Group edgeGroup = new Group();
                                // Enable the ability to return itself in a SceneGraphPath
                                edgeGroup.setCapability(BranchGroup.ENABLE_PICK_REPORTING);

                                vertexGroup.addChild(edgeGroup);

                                int numberOfEdges  = 1 + edge.getSlabs().size();
                                int numberOfPoints = 2 * numberOfEdges;

                                LineArray la = new LineArray(numberOfPoints, LineArray.COORDINATES);
                                la.setCoordinate(0, point2point3f(v1.getPoints().get(0)));
                                for (int edgePoint = 0; edgePoint < edge.getSlabs().size();edgePoint++) {
                                    Point point = edge.getSlabs().get(edgePoint);
                                    la.setCoordinate((2*edgePoint)+1, point2point3f(point));
                                    la.setCoordinate((2*edgePoint)+2, point2point3f(point));
                                }
                                la.setCoordinate(numberOfPoints-1, point2point3f(v2.getPoints().get(0)));

//                                LineArray la = new LineArray(2, LineArray.COORDINATES);
//                                la.setCoordinate(0, point2point3f(v1.getPoints().get(0)));
//                                la.setCoordinate(1, point2point3f(v2.getPoints().get(0)));
//++
                                la.setCapability(LineArray.ALLOW_COLOR_READ);
                                la.setCapability(LineArray.ALLOW_COLOR_WRITE);
//++
                                Appearance appearance = new Appearance();
                                appearance.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
                                appearance.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_WRITE);

                                LineAttributes lineAttributes = new LineAttributes();
                                lineAttributes.setLineWidth(EDGE_THICKNESS);
                                appearance.setLineAttributes(lineAttributes);

                                ColoringAttributes colorAttributes = new ColoringAttributes();
                                colorAttributes.setCapability(ColoringAttributes.ALLOW_COLOR_READ);
                                colorAttributes.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);
                                colorAttributes.setColor(EDGE_COLOR_3f);
                                appearance.setColoringAttributes(colorAttributes);

                                // Build a shape to represent the edge
                                Shape3D edgeShape = new Shape3D(la, appearance);

                                edgeShape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
                                edgeShape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);

                                ud = new UserData(edge);
                                edgeShape.setUserData(ud);

                                edgeGroup.addChild(edgeShape);


                                groupStack.push(edgeGroup);
                                stack.push(oppVertex);
                                oppVertex.setPredecessor(edge);
                            } else {
                                edge.setType(Edge.BACK);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A little function that translates an analysis point to
     * an AWT point.
     *
     * @param point - Analysis point
     * @return Point3f - AWT point
     */
    private Point3f point2point3f(Point point) {
        float x, y, z;
//        x = (point.x - (width / 2.0f)) / width;
//        y = (point.y - (height / 2.0f)) / height;
//        z = (point.z - (depth / 2.0f)) / depth;
        x = (point.x) / width;
        y = (point.y) / height;
//        z = (point.z) / depth;
        z = (point.z) / height;
        Point3f point3f = new Point3f(x, y, z);
        point3f.scale(INITIAL_SCALE);
        return point3f;
    }

    void resetColorAtGroup(Group startGroup, Color3f edgeColor) {
        Color3f currentColor = new Color3f();
        Color3f resetColor = Utils.toColor3f(EDGE_COLOR);
        Iterator<Node> children = startGroup.getAllChildren();
        while (children.hasNext()) {
            Node node = children.next();
            if (node instanceof Shape3D) {
                Shape3D shape = (Shape3D) node;
                if (shape.getGeometry() instanceof LineArray) {
                    Appearance appearance = shape.getAppearance();
                    ColoringAttributes ca = appearance.getColoringAttributes();
                    ca.getColor(currentColor);
                    if (!currentColor.equals(edgeColor)) {
                        continue;
                    }
                    ca.setColor(resetColor);
                    appearance.setColoringAttributes(ca);
                    LineArray segments = (LineArray) shape.getGeometry();
                    int nSegs = segments.getVertexCount();
                    for (int i = 0; i < nSegs; i++) {
                        UserData ud = (UserData) (shape.getUserData());
                        ud.setColorIndex(99);
                    }
                }

            } else {
                // Assume this node is a Group and recurse - follow the
                // branches.
                resetColorAtGroup((Group) node, edgeColor);

            }
        }
    }

    /**
     * Reset all the edges of the Java 3D graph to original color:
     *
     * @param edgeColor
     */
    void resetColor(Color3f edgeColor) {
        resetColorAtGroup(sceneGraph, edgeColor);
    }


    private void constructSliceGuide(Graph[] forest) {
        for (Graph tree: forest) {
            for (Edge edge : tree.getEdges()) {
                saveSliceInfo(edge.getV1().getPoints().get(0).z, edge);
                saveSliceInfo(edge.getV2().getPoints().get(0).z, edge);
                for (Point point : edge.getSlabs()) {
                    saveSliceInfo(point.z, edge);
                }
            }
        }
    }
    private void saveSliceInfo(Integer slice, Edge edge) {
        if (sliceGuide.containsKey(slice)) {
            sliceGuide.get(slice).add(edge);
        } else {
            Set<Edge> newSet = new HashSet<Edge>();
            newSet.add(edge);
            sliceGuide.put(slice, newSet);
        }

    }

    private void constructEdgeGuide(Graph[] forest) {
        for (Graph tree : forest) {
            for (Edge edge : tree.getEdges()) {
                saveEdgeInfo(edge.getV1().getPoints().get(0), edge);
                saveEdgeInfo(edge.getV2().getPoints().get(0), edge);
            }
        }
    }

    private static Map<Point, Set<Edge>> edgeGuide = new HashMap<Point, Set<Edge>>();

    public Map<Point, Set<Edge>> getEdgeGuide() {
        return edgeGuide;
    }
    private void saveEdgeInfo(Point point, Edge edge) {
        if (edgeGuide.containsKey(point)) {
            edgeGuide.get(point).add(edge);
        } else {
            Set<Edge> newSet = new HashSet<Edge>();
            newSet.add(edge);
            edgeGuide.put(point, newSet);
        }
    }
}
