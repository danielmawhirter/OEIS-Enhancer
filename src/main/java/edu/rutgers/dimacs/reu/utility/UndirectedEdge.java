package edu.rutgers.dimacs.reu.utility;

public class UndirectedEdge extends Edge {

	public UndirectedEdge(GraphNode one, GraphNode two) {
		super(one, two);
		if (one.compareTo(two) < 0) {
			this.src = one;
			this.dest = two;
		} else {
			this.dest = one;
			this.src = two;
		}
	}
}
