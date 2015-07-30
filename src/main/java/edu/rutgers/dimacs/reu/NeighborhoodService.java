package edu.rutgers.dimacs.reu;

import static javax.ejb.LockType.READ;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.json.JSONException;
import org.json.JSONObject;

import edu.rutgers.dimacs.reu.utility.DataStore;
import edu.rutgers.dimacs.reu.utility.DataStore.EdgeTypeGroup;

@Singleton
@Path("neighborhoods")
@Lock(READ)
public class NeighborhoodService {

	private static final Logger LOGGER = Logger
			.getLogger(NeighborhoodService.class.getName());

	public NeighborhoodService() {
		LOGGER.info("Neighborhood Service Instanciated");
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response get(String str_obj) {
		try {
			JSONObject obj = new JSONObject(str_obj);
			String existing_str = obj.getString("existing");
			boolean egonets = obj.getBoolean("egonets");
			if ("NONE".equals(existing_str)) {
				return Response.ok("{\"nodes\':[],\"links\":[]}").build();
			}
			String[] existing = obj.getString("existing").split("-");
			if(egonets) {
				return Response.serverError().build();
			} else {
				LOGGER.info("Returning neighborhoods as stream");
				return Response.ok(neighborhoods(existing)).build();
			}
		} catch (NumberFormatException | JSONException | ExecutionException e) {
			LOGGER.log(Level.SEVERE, "Exception while getting neighborhoods", e);;
		}
		return Response.serverError().build();
	}

	private StreamingOutput neighborhoods(final String[] existing)
			throws ExecutionException {
		final TreeSet<String> lexicalNodes = new TreeSet<>();
		final LexicalEdgeSet lexicalEdges = new LexicalEdgeSet();
		for (String s : existing) {
			int current = Integer.parseInt(s);
			Iterable<Integer> it = DataStore.getInstance()
					.getAdjacentUndirected(current, EdgeTypeGroup.NORMALONLY);
			for(Integer neighbor : it) {
				lexicalNodes.add(neighbor.toString());
				lexicalEdges.add(s, neighbor.toString());
			}
		}
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				writer.write("{\"nodes\":[");
				boolean first = true;
				for(String s : lexicalNodes) {
					if(first) first = false;
					else writer.write(",");
					writer.write("{\"name\":\"");
					writer.write(s);
					writer.write("\"}");
				}
				writer.write("],\"links\":");
				lexicalEdges.stream(writer);
				writer.write("}");
				writer.flush();
				writer.close();
			}
		};
		return stream;
	}

	private static class LexicalEdgeSet {
		private TreeMap<String, LexicalEdge> edges;

		public LexicalEdgeSet() {
			edges = new TreeMap<>();
		}

		public void add(String one, String two) {
			String id = LexicalEdge.Id(one, two);
			LexicalEdge currentEdge = edges.get(id);
			if (null == currentEdge) {
				currentEdge = new LexicalEdge(one, two);
				edges.put(id, currentEdge);
			}
		}

		public void stream(Writer writer) throws IOException {
			writer.write("[");
			boolean first = true;
			for (String s : edges.keySet()) {
				LexicalEdge e = edges.get(s);
				if (first) {
					first = false;
					writer.write(e.toString());
				} else {
					writer.write("," + e.toString());
				}
			}
			writer.write("]");
		}

		private static class LexicalEdge {
			String src, dest;

			public LexicalEdge(String one, String two) {
				if (one.compareTo(two) < 0) {
					this.src = one;
					this.dest = two;
				} else {
					this.dest = one;
					this.src = two;
				}
			}

			public static String Id(String one, String two) {
				if (one.compareTo(two) < 0) {
					return one + "--" + two;
				} else {
					return two + "--" + one;
				}
			}

			@Override
			public String toString() {
				return "{\"id\":\"" + src.toString() + "--" + dest.toString()
						+ "\",\"source_name\":\"" + src.toString()
						+ "\", \"target_name\":\"" + dest.toString() + "\"}";
			}
		}
	}
}