package edu.rutgers.dimacs.reu.utility;

public class UndirectedEdge extends Edge {

	public int forwardMultiplicity = 0, reverseMultiplicity = 0;

	public UndirectedEdge(GraphNode one, GraphNode two) {
		super(one, two);
		if (one.compareTo(two) < 0) {
			this.src = one;
			this.dest = two;
			forwardMultiplicity++;
		} else {
			this.dest = one;
			this.src = two;
			reverseMultiplicity++;
		}
	}

	@Override
	public void mergeIn(Edge edge) {
		UndirectedEdge e = (UndirectedEdge) edge;
		this.forwardMultiplicity += e.forwardMultiplicity;
		this.reverseMultiplicity += e.reverseMultiplicity;
	}
}
