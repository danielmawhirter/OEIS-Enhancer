package edu.rutgers.dimacs.reu.utility;

public abstract class Edge implements Comparable<Edge> {
	public GraphNode src, dest;

	public Edge(GraphNode one, GraphNode two) {}

	@Override
	public boolean equals(Object o) {
		return o instanceof Edge && ((Edge) o).src.equals(this.src)
				&& ((Edge) o).dest.equals(this.dest);
	}

	@Override
	public int compareTo(Edge e) {
		int compare = this.src.compareTo(e.src);
		if (compare != 0)
			return compare;
		return this.dest.compareTo(e.dest);
	}
	
	public String getID() {
		return "{" + src.id + "," + dest.id + "}";
	}

	@Override
	public String toString() {
		return "\"id\":\"" + src + "--" + dest + "\", \"source_name\":\"" + src
				+ "\", \"target_name\":\"" + dest + "\"";
	}
	
}