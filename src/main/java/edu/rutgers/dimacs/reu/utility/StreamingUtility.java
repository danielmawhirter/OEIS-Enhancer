package edu.rutgers.dimacs.reu.utility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.ws.rs.core.StreamingOutput;

public class StreamingUtility {

	private static final Logger LOGGER = Logger.getLogger(StreamingUtility.class.getName());

	private static void writeNodesJSON(TreeMap<Integer, TreeMap<Integer, Double>> graph, Set<Integer> path_ints,
			Set<Integer> landmark_ints, final Map<Integer, Double> nodeWeight, final Map<Integer, Double> lmWeight,
			ArrayList<Integer> neighborhoodSizes, Writer writer) throws IOException {
		if (null == graph)
			return;
		Set<Integer> vertices = graph.keySet();
		Map<Integer, String> descriptions = null;
		try {
			descriptions = DataStore.getInstance().getDescription(vertices);
		} catch (SQLException | NamingException e) {
			LOGGER.warning("Words and descriptions not available from DataStore");
		}

		boolean first = true;
		for (int gn_int : vertices) {
			if (first) {
				first = false;
			} else {
				writer.write(",");
			}

			writer.write("\n{\"name\":\"");
			writer.write(Integer.toString(gn_int));
			if (descriptions != null) {
				writer.write("\",\"description\":\"");
				writer.write(descriptions.get(gn_int));
			}
			if (null != path_ints && path_ints.contains(gn_int)) {
				writer.write("\",\"path\":\"true");
			}
			if (null != landmark_ints && landmark_ints.contains(gn_int)) {
				writer.write("\",\"landmark\":\"true");
				if(null != lmWeight && null != lmWeight.get(gn_int)) {
					writer.write("\",\"landmarkWeight\":\"");
					writer.write(lmWeight.get(gn_int).toString());
				}
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

	private static void writeEdgesJSON(TreeMap<Integer, TreeMap<Integer, Double>> graph, Writer writer) throws IOException {
		if (null == graph)
			return;
		boolean first = true;
		for (int one : graph.keySet()) {
			TreeMap<Integer, Double> oneAdj = graph.get(one);
			for (int two : oneAdj.keySet()) {
				if (!first) {
					writer.write(",");
				}
				first = false;
				writer.write("\n{\"id\":\"");
				writer.write(Integer.toString(one));
				writer.write("--");
				writer.write(Integer.toString(two));
				writer.write("\", \"source_name\":\"");
				writer.write(Integer.toString(one));
				writer.write("\", \"target_name\":\"");
				writer.write(Integer.toString(two));
				writer.write("\", \"edge_weight\":\"");
				writer.write(Double.toString(oneAdj.get(two)));
				writer.write("\"}");
			}
		}
	}

	public static StreamingOutput streamJSON(final Set<Integer> vertices, final Set<Integer> path_ints,
			final Set<Integer> landmark_ints, final Map<Integer, Double> nodeWeight,
			final Map<Integer, Double> lmWeight, final ArrayList<Integer> neighborhoodSizes, final long timeStart) {
		if (null == vertices)
			return null;
		return new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				TreeMap<Integer, TreeMap<Integer, Double>> graph = null;
				try {
					graph = DataStore.getInstance().getSubgraphInduced(vertices);
				} catch (ExecutionException e) {
					LOGGER.log(Level.SEVERE, "Cannot get crossrefs within", e);
				}

				writer.write("{\n\"nodes\":[\n");
				writeNodesJSON(graph, path_ints, landmark_ints, nodeWeight, lmWeight, neighborhoodSizes, writer);
				writer.write("], \"links\":[\n");
				writeEdgesJSON(graph, writer);
				writer.write("]\n}");
				writer.flush();
				writer.close();
				LOGGER.info("algorithm and response with JSON took "
						+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000)) + "ms");
			}
		};
	}

}
