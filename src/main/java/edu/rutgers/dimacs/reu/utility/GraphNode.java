package edu.rutgers.dimacs.reu.utility;

import java.util.TreeSet;

public class GraphNode implements Comparable<GraphNode> {
	public String id, clusterId;
	public TreeSet<GraphNode> neighbors;
	public int weight, int_edge_wsum;
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
}
