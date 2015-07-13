package edu.rutgers.dimacs.reu.utility;

import java.util.Collection;
import java.util.HashMap;

public class Graph {
	private String id;
	public HashMap<String, GraphNode> idToNode;
	public HashMap<String, Edge> edges;
	private boolean directed;

	public Graph(boolean directed, String id) {
		this.id = id;
		idToNode = new HashMap<>();
		edges = new HashMap<>();
		this.directed = directed;
	}

	public GraphNode addNode(String id) {
		GraphNode newNode = new GraphNode(id);
		idToNode.put(id, newNode);
		return newNode;
	}
	
	public Collection<GraphNode> getNodeSet() {
		return idToNode.values();
	}
	
	public Collection<Edge> getEdgeSet() {
		return edges.values();
	}
	
	public GraphNode getNode(String id) {
		return idToNode.get(id);
	}
	
	public Edge getEdge(String id) {
		return edges.get(id);
	}
	
	public int getNodeCount() {
		return idToNode.keySet().size();
	}
	
	public int getEdgeCount() {
		return edges.keySet().size();
	}
	
	public String getId() {
		return id;
	}

	// adds edge between string-identified nodes, creating nodes if necessary
	public void addEdge(String src, String dest) {
		if (src == null || dest == null)
			throw new RuntimeException("That's a null string");
		GraphNode srcNode = idToNode.get(src);
		if (srcNode == null) {
			srcNode = new GraphNode(src);
			idToNode.put(src, srcNode);
		}
		GraphNode destNode = idToNode.get(dest);
		if (destNode == null) {
			destNode = new GraphNode(dest);
			idToNode.put(dest, destNode);
		}
		addEdge(srcNode, destNode);
	}

	public Edge addEdge(GraphNode src, GraphNode dest) {
		if (src == null)
			throw new RuntimeException("Source node is null, fix it");
		if (dest == null)
			throw new RuntimeException("Destination node is null, fix it");
		if (directed) {
			src.neighbors.add(dest);
			edges.put("{" + src.id + "," + dest.id + "}", new DirectedEdge(src, dest));
		} else {
			src.neighbors.add(dest);
			dest.neighbors.add(src);
			edges.put("{" + src.id + "," + dest.id + "}", new UndirectedEdge(src, dest)); //unsafe, could have 2 undirected edges between pair
		}
		
		return edges.get("{" + src.id + "," + dest.id + "}");
	}

	public boolean hasEdgeBetween(GraphNode src, GraphNode dest) {
		return src != null && dest != null && src.neighbors.contains(dest);
	}

	public boolean hasEdgeBetween(String src, String dest) {
		return hasEdgeBetween(idToNode.get(src), idToNode.get(dest));
	}
	
	public void removeEdge(String src, String dest) {
		removeEdge(idToNode.get(src), idToNode.get(dest));
	}
	
	public void removeNode(GraphNode n) {
		for (GraphNode nbr : n.neighbors) {
			removeEdge(n,nbr);
		}
		idToNode.remove(n.id);
	}
	
	public void removeEdge(GraphNode src, GraphNode dest) {
		if (src == null)
			throw new RuntimeException("Source node is null, fix it");
		if (dest == null)
			throw new RuntimeException("Destination node is null, fix it");
		if(directed) {
			edges.remove(new DirectedEdge(src, dest));
			src.neighbors.remove(dest);
		}
		else {
			edges.remove(new UndirectedEdge(src, dest));
			src.neighbors.remove(dest);
			dest.neighbors.remove(src);
		}
	}

}
