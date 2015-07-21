package edu.rutgers.dimacs.reu.utility;

public class DirectedEdge extends Edge {

	public int multiplicity;

	public DirectedEdge(GraphNode one, GraphNode two) {
		super(one, two);
		this.src = one;
		this.dest = two;
	}

	@Override
	public void mergeIn(Edge edge) {
		DirectedEdge e = (DirectedEdge) edge;
		this.multiplicity += e.multiplicity;
	}

}
