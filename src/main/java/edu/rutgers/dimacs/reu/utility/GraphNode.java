package edu.rutgers.dimacs.reu.utility;

import java.util.Collection;
import java.util.LinkedList;
import java.util.TreeSet;

public class GraphNode implements Comparable<GraphNode> {
	public String id, clusterId;
	private TreeSet<GraphNode> neighbors;
	public int weight, int_edge_wsum;
	private boolean removed = false;
	
	public GraphNode(String id) {
		super();
		this.id = id;
		this.neighbors = new TreeSet<>();;
	}
	
	@Override
	public String toString() {
		return id;
	}
	
	public int getDegree() {
		return this.neighbors.size();
	}
	
	@Override
	public int compareTo(GraphNode that) {
		return this.id.compareTo(that.id);
	}
	@Override
	public boolean equals(Object o) {
		return o instanceof GraphNode && ((GraphNode)o).id.equals(this.id);
	}
	
	public void remove() {
		removed = true;
	}
	
	public boolean removed() {
		return removed;
	}
	
	public Collection<GraphNode> getNeighbors() {
		Collection<GraphNode> removed = new LinkedList<>();
		for(GraphNode gn : neighbors) {
			if(gn.removed()) removed.add(gn);
		}
		neighbors.removeAll(removed);
		return neighbors;
	}
	
	public boolean addNeighbor(GraphNode gn) {
		return neighbors.add(gn);
	}
	
	public boolean removeNeighbor(GraphNode gn) {
		return neighbors.remove(gn);
	}
	
	public boolean neighbors(GraphNode gn) {
		return neighbors.contains(gn);
	}
}
