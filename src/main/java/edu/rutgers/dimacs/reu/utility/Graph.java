package edu.rutgers.dimacs.reu.utility;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class Graph {
	//static int total = 0;
	private String id;
	public Map<String, GraphNode> idToNode;
	public Map<String, Edge> edges;
	private final boolean directed;

	public Graph(boolean directed, String id) {
		this.id = id;
		idToNode = new HashMap<>();
		edges = new HashMap<>();
		this.directed = directed;
	}
	
	public Graph(Graph that) {
		this.id = that.id;
		this.directed = that.directed;
		
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
	
	public int getNodeCount() {
		return idToNode.keySet().size();
	}
	
	public int getEdgeCount() {
		return edges.keySet().size();
	}
	
	public String getId() {
		return id;
	}
	
	public boolean contains(GraphNode n) {
		return idToNode.values().contains(n);
	}

	public Edge addEdge(String src, String dest) {
		if (src == null || dest == null)
			throw new RuntimeException("That's a null string");
		GraphNode srcNode = idToNode.get(src);
		if (srcNode == null) {
			srcNode = new GraphNode(src);
			idToNode.put(src, srcNode);
			//total++;
		}
		GraphNode destNode = idToNode.get(dest);
		if (destNode == null) {
			destNode = new GraphNode(dest);
			idToNode.put(dest, destNode);
			//total++;
		}
		return addEdge(srcNode, destNode);
	}

	public Edge addEdge(GraphNode src, GraphNode dest) {
		if (src == null)
			throw new RuntimeException("Source node is null, fix it");
		if (dest == null)
			throw new RuntimeException("Destination node is null, fix it");
		Edge adding = null, existing = null;
		if (directed) {
			adding = new DirectedEdge(src, dest);
		} else {
			adding = new UndirectedEdge(src, dest);
		}
		existing = edges.get(adding.getId());
		if(existing == null) {
			edges.put(adding.getId(), adding);
			src.addIncidentEdge(adding);
			dest.addIncidentEdge(adding);
			return adding;
		} else {
			existing.mergeIn(adding);
			return existing;
		}
		
	}

	/*public boolean hasEdgeBetween(GraphNode src, GraphNode dest) {
		return src != null && dest != null && src.neighbors(dest);
	}*/

	/*public boolean hasEdgeBetween(String src, String dest) {
		return hasEdgeBetween(idToNode.get(src), idToNode.get(dest));
	}*/
	
	public Edge getEdge(GraphNode src, GraphNode dest) {
		Edge getting = null;
		if (directed) {
			getting = new DirectedEdge(src, dest);
		} else {
			getting = new UndirectedEdge(src, dest);
		}
		return edges.get(getting.getId());
	}
	
	public Edge getEdge(String src, String dest) {
		return getEdge(idToNode.get(src), idToNode.get(dest));
	}
	
	public void removeEdge(String src, String dest) {
		removeEdge(idToNode.get(src), idToNode.get(dest));
	}
	
	public void removeNode(GraphNode n) {
		//LinkedList<GraphNode> removal = new LinkedList<>(n.getNeighbors());
		LinkedList<Edge> removal = new LinkedList<>(n.getIncidentEdges());
		while(removal.size() > 0) {
			removeEdge(removal.pop());
		}
		idToNode.remove(n.toString());
	}
	
	public void removeEdge(GraphNode src, GraphNode dest) {
		if (src == null)
			throw new RuntimeException("Source node is null, fix it");
		if (dest == null)
			throw new RuntimeException("Destination node is null, fix it");
		Edge removing = null;
		if (directed) {
			removing = new DirectedEdge(src, dest);
		} else {
			removing = new UndirectedEdge(src, dest);
		}
		Edge existing = edges.get(removing.getId());
		if(existing != null)  {
			src.removeIncidentEdge(existing);
			dest.removeIncidentEdge(existing);
			edges.remove(existing.getId());
		}
	}
	
	public void removeEdge(Edge e) {
		Edge existing = edges.get(e.getId());
		if(existing != null)  {
			existing.src.removeIncidentEdge(existing);
			existing.dest.removeIncidentEdge(existing);
			edges.remove(existing.getId());
		}
	}
	
}
