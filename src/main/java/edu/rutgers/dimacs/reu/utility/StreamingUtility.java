package edu.rutgers.dimacs.reu.utility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

public class StreamingUtility {

	private static final Logger LOGGER = Logger.getLogger(StreamingUtility.class.getName());

	private static void writeNodesJSON(Graph graph, Set<Integer> path_ints, Set<Integer> landmark_ints,
			final Map<Integer, Double> nodeWeight, final Map<Integer, Double> lmWeight,
			ArrayList<Integer> neighborhoodSizes, Writer writer) throws IOException {
		LinkedList<Integer> wordNodes = new LinkedList<>();
		for (GraphNode gn : graph.getNodeSet()) {
			wordNodes.add(Integer.parseInt(gn.toString()));
		}
		Map<Integer, String> descriptions = null;
		try {
			descriptions = DataStore.getInstance().getDescription(wordNodes);
		} catch (SQLException | NamingException e) {
			LOGGER.warning("Words and descriptions not available from DataStore");
		}

		boolean first = true;
		for (GraphNode gn : graph.getNodeSet()) {
			int gn_int = Integer.parseInt(gn.toString());
			if (first) {
				first = false;
			} else {
				writer.write(",");
			}

			writer.write("\n{\"name\":\"");
			writer.write(gn.toString());
			if (descriptions != null) {
				writer.write("\",\"description\":\"");
				writer.write(descriptions.get(gn_int));
			}
			if (null != path_ints && path_ints.contains(gn_int)) {
				writer.write("\",\"path\":\"true");
			}
			if (null != landmark_ints && landmark_ints.contains(gn_int)) {
				writer.write("\",\"landmark\":\"true");
				writer.write("\",\"landmarkWeight\":\"");
				writer.write(lmWeight.get(gn_int).toString());
			}
			if (null != nodeWeight && nodeWeight.containsKey(gn_int)) {
				writer.write("\",\"nodeWeight\":\"");
				writer.write(nodeWeight.get(gn_int).toString());
			}
			if (null != neighborhoodSizes && gn_int < neighborhoodSizes.size()) {
				writer.write("\",\"neighborhoodSize\":\"");
				writer.write(neighborhoodSizes.get(gn_int).toString());
			}
			writer.write("\"}");
		}
	}

	private static void writeEdgesJSON(Graph graph, Writer writer) throws IOException {
		boolean first = true;
		TreeSet<Edge> orderedEdges = new TreeSet<>(graph.getEdgeSet());
		for (Edge edge : orderedEdges) {
			if (first) {
				first = false;
				writer.write("\n{");
				writer.write(edge.toString());
				writer.write("}");
			} else {
				writer.write(",\n{");
				writer.write(edge.toString());
				writer.write("}");
			}
		}
	}

	public static StreamingOutput finalJSON(final Graph graph, final Set<Integer> path_ints,
			final Set<Integer> landmark_ints, final Map<Integer, Double> nodeWeight,
			final Map<Integer, Double> lmWeight, final ArrayList<Integer> neighborhoodSizes, final long timeStart) {
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException, WebApplicationException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				if (null != graph) {
					writer.write("{\n\"nodes\":[\n");
					writeNodesJSON(graph, path_ints, landmark_ints, nodeWeight, lmWeight, neighborhoodSizes, writer);
					writer.write("], \"links\":[\n");
					writeEdgesJSON(graph, writer);
					writer.write("]\n}");
					writer.flush();
					writer.close();
					LOGGER.info("algorithm and response with JSON took "
							+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000)) + "ms");
				} else {
					writer.write("{\"error\":\"Graph not available: DataStore failure\"}");
				}
			}
		};
		return stream;
	}

	public static Graph buildGraph(Set<Integer> ints) {
		Graph graph = new Graph(false, "");

		// add all the nodes
		for (int n : ints) {
			graph.addNode(Integer.toString(n));
		}

		Map<Integer, Collection<Integer>> refs;
		try {
			refs = DataStore.getInstance().getCrossrefsWithin(ints);
		} catch (ExecutionException e) {
			LOGGER.warning("Unable to get crossrefs from DataStore");
			return null;
		}

		// for each node, get the neighborhood
		// and if the neighbor_gn exists, add the edge
		for (GraphNode gn : graph.getNodeSet()) {
			int gn_int = Integer.parseInt(gn.toString());
			for (int neighbor_int : refs.get(gn_int)) {
				String neighbor_str = Integer.toString(neighbor_int);
				GraphNode neighbor_gn = graph.getNode(neighbor_str);
				if (neighbor_gn != null) {
					graph.addEdge(gn, neighbor_gn);
				}
			}
		}

		return graph;
	}
}
