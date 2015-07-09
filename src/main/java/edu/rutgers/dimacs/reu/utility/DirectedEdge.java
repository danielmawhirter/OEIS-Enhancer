package edu.rutgers.dimacs.reu.utility;

public class DirectedEdge extends Edge {

	public DirectedEdge(GraphNode one, GraphNode two) {
		super(one, two);
		this.src = one;
		this.dest = two;
	}

}
