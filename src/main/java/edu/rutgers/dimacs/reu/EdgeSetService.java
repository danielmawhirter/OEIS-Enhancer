package edu.rutgers.dimacs.reu;

//Service, handles queries for edges among subset of antichain

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.PathParam;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.json.*;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.CacheLoader;

import edu.rutgers.dimacs.reu.utility.HierarchyTree;
import edu.rutgers.dimacs.reu.utility.TreeNode;
import static javax.ejb.LockType.READ;

@Singleton
@Lock(READ)
public class EdgeSetService {
	private final ClassLoader cl;
	LoadingCache<String, HierarchyTree> treeCache;
	LoadingCache<Integer, LinkedList<String>> requestTokenCache;
	private static AtomicInteger tokenNum = new AtomicInteger(0);
	private final static Logger LOGGER = Logger.getLogger(PathService.class
			.getName());

	public EdgeSetService() {
		super();
		cl = this.getClass().getClassLoader();
		treeCache = CacheBuilder.newBuilder().maximumSize(10)
				.expireAfterAccess(5, TimeUnit.MINUTES)
				.build(new CacheLoader<String, HierarchyTree>() {
					@Override
					public HierarchyTree load(String graph) throws IOException {
						InputStream tree_is = cl.getResourceAsStream("graphs/"
								+ graph + "/tree.txt");
						return new HierarchyTree(tree_is);
					}
				});
		requestTokenCache = CacheBuilder.newBuilder().maximumSize(10)
				.expireAfterAccess(20, TimeUnit.SECONDS)
				.build(new CacheLoader<Integer, LinkedList<String>>() {
					@Override
					public LinkedList<String> load(Integer arg0) {
						return new LinkedList<>();
					}
				});
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
	@Path("incidentEdges/getToken")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public String getToken(String s) {
		JSONObject obj = new JSONObject(s);
		JSONArray query = obj.getJSONArray("query");
		int token = tokenNum.incrementAndGet();
		try {
			LinkedList<String> list = requestTokenCache.get(token);
			for(int i = 0; i < query.length(); i++) {
				list.add(query.getString(i));
			}
		} catch (ExecutionException e) {
			LOGGER.severe(e.getMessage());
			return "{\"error\":\"cache error\"}";
		}
		LOGGER.info("Token " + Integer.toString(token)
				+ " generated");
		return new JSONObject().put("token", token).toString();
	}

	/*@GET
	@Path("incidentEdges/beginInput")
	@Produces(MediaType.APPLICATION_JSON)
	public String beginInput() {
		int token = tokenNum.incrementAndGet();
		try {
			requestTokenCache.get(token);
		} catch (ExecutionException e) {
			LOGGER.severe(e.getMessage());
			return "{\"error\":\"cache error\"}";
		}
		LOGGER.info("Token " + Integer.toString(token)
				+ " generated");
		return "{\"token\":" + Integer.toString(token) + "}";
	}
	
	@GET
	@Path("incidentEdges/addInput/{token}/{query}")
	@Produces(MediaType.APPLICATION_JSON)
	public String addInput(@PathParam("token") int token,
			@PathParam("query") String query) {
		try {
			LinkedList<String> nodes = requestTokenCache.get(token);
			if(null == nodes) return "{\"error\":\"timeout error\"}";
			nodes.addAll(Arrays.asList(query.split("-")));
		} catch (ExecutionException e) {
			LOGGER.severe(e.getMessage());
			return "{\"error\":\"cache error\"}";
		}
		LOGGER.info("Token " + Integer.toString(token) + " added data: "
				+ query);
		return "{\"token\":" + Integer.toString(token) + "}";
	}*/

	@GET
	@Path("incidentEdges/withToken/{graph}/{token}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response readyForEdges(@PathParam("graph") String graph,
			@PathParam("token") int token) {
		try {
			return getEdges(graph, requestTokenCache.get(token));
		} catch (ExecutionException e) {
			LOGGER.severe(e.getMessage());
			return Response.serverError().build();
		}
	}

	private Response getEdges(String graph, List<String> nodes) {
		HashMap<String, String> leafToCluster;
		StringBuilder logString = new StringBuilder();
		long timeStart = System.nanoTime();
		HierarchyTree tree = null;
		try {
			tree = treeCache.get(graph);
		} catch (ExecutionException e) {
			LOGGER.severe(e.getMessage());
			return Response.serverError().build();
		}
		logString.append(Integer.toString(nodes.size()));
		logString.append(" vertices in query\n");
		leafToCluster = new HashMap<>();
		for (String s : nodes) {
			for (String d : tree.getLeaves(s)) {
				leafToCluster.put(d, s);
			}
		}
		logString.append("Tree interaction took ");
		logString.append(Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000)));
		logString.append("ms\n");

		InputStream edge_is = cl.getResourceAsStream("graphs/" + graph
				+ "/edges.txt");
		timeStart = System.nanoTime();
		StreamingOutput stream;
		try {
			stream = EdgeSet.getAsJSONStream(leafToCluster, edge_is);
		} catch (NumberFormatException | IOException e) {
			LOGGER.severe(e.getMessage());
			return Response.serverError().build();
		}
		logString.append("Edge interaction took ");
		logString.append(Integer
						.toString((int) ((System.nanoTime() - timeStart) / 1000000)));
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
			tree = treeCache.get(graph);
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
}
