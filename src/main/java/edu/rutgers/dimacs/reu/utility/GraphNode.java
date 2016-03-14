package edu.rutgers.dimacs.reu.utility;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public class GraphNode implements Comparable<GraphNode> {
	private String id;
	public String label = null;
	private HashSet<Edge> incidentEdges;
	private HashSet<String> properties;
	public int weight, int_edge_wsum;
	
	public GraphNode(String id) {
		super();
		this.id = id;
		this.incidentEdges = new HashSet<>();
		this.properties = new HashSet<>();
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
	
	public void addProperty(String prop) {
		this.properties.add(prop);
	}
	
	public boolean hasProperty(String prop) {
		return this.properties.contains(prop);
	}
}
