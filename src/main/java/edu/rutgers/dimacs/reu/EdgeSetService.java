package edu.rutgers.dimacs.reu;

//Service, handles queries for edges among subset of antichain

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.PathParam;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.json.*;

import edu.rutgers.dimacs.reu.utility.DataStore;
import edu.rutgers.dimacs.reu.utility.HierarchyTree;
import edu.rutgers.dimacs.reu.utility.TreeNode;
import static javax.ejb.LockType.READ;

@Singleton
@Lock(READ)
public class EdgeSetService {
	private final ClassLoader cl;
	private final static Logger LOGGER = Logger.getLogger(PathService.class
			.getName());

	public EdgeSetService() {
		super();
		cl = this.getClass().getClassLoader();
		LOGGER.info("EdgeSetService instanciated");
	}

	// toy/twelve-five-eight
	@GET
	@Path("incidentEdges/{graph}/{query}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAll(@PathParam("graph") String graph,
			@PathParam("query") String query) {
		LOGGER.info("Query: " + graph + "/" + query);
		return getEdges(graph, Arrays.asList(query.split("-")));
	}

	@POST
	@Path("incidentEdges/getIncident")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getViaPOST(String s) {
		JSONObject obj = new JSONObject(s);
		JSONArray query = obj.getJSONArray("query");
		String graph = obj.getString("graph");
		LinkedList<String> list = new LinkedList<String>();
		for (int i = 0; i < query.length(); i++) {
			list.add(query.getString(i));
		}
		return getEdges(graph, list);
	}

	private Response getEdges(String graph, List<String> nodes) {
		HashMap<String, String> leafToCluster;
		StringBuilder logString = new StringBuilder();
		long timeStart = System.nanoTime();
		HierarchyTree tree = null;
		try {
			tree = DataStore.getInstance().getTree(graph);
		} catch (ExecutionException e) {
			LOGGER.severe(e.getMessage());
			return Response.serverError().build();
		}
		logString.append(Integer.toString(nodes.size()));
		logString.append(" vertices in query on ");
		logString.append(graph);
		leafToCluster = new HashMap<>();
		for (String s : nodes) {
			for (String d : tree.getLeaves(s)) {
				leafToCluster.put(d, s);
			}
		}
		logString.append("\nTree interaction took ");
		logString
				.append(Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000)));
		logString.append("ms\n");

		InputStream edge_is = cl.getResourceAsStream("graphs/" + graph
				+ "/edges.txt");
		timeStart = System.nanoTime();
		StreamingOutput stream;
		try {
			stream = getAsJSONStream(leafToCluster, edge_is);
		} catch (NumberFormatException | IOException e) {
			LOGGER.severe(e.getMessage());
			return Response.serverError().build();
		}
		logString.append("Edge interaction took ");
		logString
				.append(Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000)));
		logString.append("ms\nQuery success");
		LOGGER.info(logString.toString());
		return Response.ok(stream).build();
	}

	@GET
	@Path("expansionSchedule/{graph}/{target}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getExpansion(@PathParam("graph") String graph,
			@PathParam("target") String target) {
		HierarchyTree tree;
		try {
			tree = DataStore.getInstance().getTree(graph);
		} catch (ExecutionException e) {
			LOGGER.severe(e.getMessage());
			return Response.serverError().build();
		}
		TreeNode<String> current = tree.getNode(target);
		final LinkedList<String> schedule = new LinkedList<>();
		while (current != null) {
			schedule.addFirst(current.getObject());
			current = current.getParent();
		}
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				writer.write("[");
				boolean first = true;
				for (String s : schedule) {
					if (first) {
						writer.write("\"" + s + "\"");
						first = false;
					} else
						writer.write(",\"" + s + "\"");
				}
				writer.write("]");
				writer.flush();
				writer.close();
			}
		};
		return Response.ok(stream).build();
	}

	public static StreamingOutput getAsJSONStream(
			Map<String, String> leafToCluster, InputStream is)
			throws NumberFormatException, IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		EdgeCounter counter = new EdgeCounter();
		String line;
		while ((line = br.readLine()) != null) {
			String[] split = line.split(" ");
			String lineCluster = leafToCluster.get(split[0]);
			if (lineCluster != null) {
				for (int i = 0; i < Integer.parseInt(split[1]); i++) {
					String itemCluster = leafToCluster.get(split[2 + i]);
					if (itemCluster != null && !lineCluster.equals(itemCluster)) {
						counter.add(lineCluster, itemCluster);
					}
				}
			}
		}
		is.close();
		br.close();
		return counter.stream();
	}

	private static class EdgeCounter {
		private TreeMap<String, Edge> edges;

		public EdgeCounter() {
			edges = new TreeMap<>();
		}

		public void add(String one, String two) {
			String id = Edge.Id(one, two);
			Edge currentEdge = edges.get(id);
			if (null == currentEdge) {
				currentEdge = new Edge(one, two);
				edges.put(id, currentEdge);
			} else {
				currentEdge.forward |= Edge.forward(one, two);
				currentEdge.reverse |= !Edge.forward(one, two);
			}
			currentEdge.count++;
		}

		// array of edges
		public StreamingOutput stream() {
			StreamingOutput stream = new StreamingOutput() {
				@Override
				public void write(OutputStream os) throws IOException,
						WebApplicationException {
					Writer writer = new BufferedWriter(new OutputStreamWriter(
							os));
					writer.write("[");
					boolean first = true;
					for (String s : edges.keySet()) {
						Edge e = edges.get(s);
						if (first) {
							first = false;
							writer.write(e.toString());
						} else {
							writer.write("," + e.toString());
						}
					}
					writer.write("]");
					writer.flush();
					writer.close();
				}
			};
			return stream;
		}

		private static class Edge {
			String src, dest;
			boolean forward, reverse;
			Integer count = 0;

			public Edge(String one, String two) {
				if (one.compareTo(two) < 0) {
					this.src = one;
					this.dest = two;
					forward = true;
					reverse = false;
				} else {
					this.dest = one;
					this.src = two;
					forward = false;
					reverse = true;
				}
			}

			public static String Id(String one, String two) {
				if (one.compareTo(two) < 0) {
					return one + "--" + two;
				} else {
					return two + "--" + one;
				}
			}

			public static boolean forward(String one, String two) {
				return one.compareTo(two) < 0;
			}

			@Override
			public String toString() {
				return "{\"id\":\"" + src.toString() + "--" + dest.toString()
						+ "\",\"source_name\":\"" + src.toString()
						+ "\", \"target_name\":\"" + dest.toString()
						+ "\",\"value\":" + count.toString()
						+ ((forward && !reverse) ? ",\"forward\":true" : "")
						+ ((reverse && !forward) ? ",\"reverse\":true" : "")
						+ "}";
			}
		}
	}

}
