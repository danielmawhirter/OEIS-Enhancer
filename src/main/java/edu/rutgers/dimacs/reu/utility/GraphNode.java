package edu.rutgers.dimacs.reu.utility;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public class GraphNode implements Comparable<GraphNode> {
	public String id, clusterId;
	private HashSet<Edge> incidentEdges;
	//private TreeSet<GraphNode> neighbors;
	public int weight, int_edge_wsum;
	//private boolean removed = false;
	
	public GraphNode(String id) {
		super();
		this.id = id;
		//this.neighbors = new TreeSet<>();
		this.incidentEdges = new HashSet<>();
	}
	
	public GraphNode(GraphNode that, HashSet<Edge> edges) {
		super();
		this.id = that.id;
		this.incidentEdges = edges;
	}
	
	@Override
	public String toString() {
		return id;
	}
	
	public int getDegree() {
		return this.incidentEdges.size();
	}
	
	@Override
	public int compareTo(GraphNode that) {
		return this.id.compareTo(that.id);
	}
	
	@Override
	public boolean equals(Object o) {
		return o instanceof GraphNode && ((GraphNode)o).id.equals(this.id);
	}
	
	/*public void remove() {
		removed = true;
	}
	
	public boolean removed() {
		return removed;
	}*/
	
	/*public Collection<GraphNode> getNeighbors() {
		Collection<GraphNode> removed = new LinkedList<>();
		for(GraphNode gn : neighbors) {
			if(gn.removed()) removed.add(gn);
		}
		neighbors.removeAll(removed);
		return neighbors;
	}*/
	
	public Collection<GraphNode> getNeighbors() {
		LinkedList<GraphNode> neighbors = new LinkedList<>();
		for(Edge e : incidentEdges) {
			if(e.dest != this) {
				neighbors.push(e.dest);
			}
			if(e instanceof UndirectedEdge && e.src != this) {
				neighbors.push(e.src);
			}
		}
		return neighbors;
	}
	
	public Collection<Edge> getIncidentEdges() {
		return this.incidentEdges;
	}
	
	public boolean addIncidentEdge(Edge e) {
		return incidentEdges.add(e);
	}
	
	public boolean removeIncidentEdge(Edge e) {
		return incidentEdges.remove(e);
	}
	
	/*public boolean neighbors(GraphNode gn) {
		return neighbors.contains(gn);
	}*/
}
