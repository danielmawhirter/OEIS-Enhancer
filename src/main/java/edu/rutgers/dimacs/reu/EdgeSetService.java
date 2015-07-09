package edu.rutgers.dimacs.reu;

//Service, handles queries for edges among subset of antichain

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.PathParam;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.CacheLoader;

import edu.rutgers.dimacs.reu.utility.HierarchyTree;
import static javax.ejb.LockType.READ;

@Path("incidentEdges")
@Singleton
@Lock(READ)
public class EdgeSetService {
	private ClassLoader cl;
	LoadingCache<String, HierarchyTree> cache;
	private final static Logger LOGGER = Logger.getLogger(PathService.class
			.getName());

	public EdgeSetService() {
		super();
		cache = CacheBuilder.newBuilder().maximumSize(10)
				.expireAfterAccess(5, TimeUnit.MINUTES)
				.build(new CacheLoader<String, HierarchyTree>() {
					@Override
					public HierarchyTree load(String graph) throws IOException {
						InputStream tree_is = cl.getResourceAsStream("graphs/"
								+ graph + "/tree.txt");
						return new HierarchyTree(tree_is);
					}
				});

		cl = this.getClass().getClassLoader();
		LOGGER.info("EdgeSetService instanciated");
	}

	// toy/twelve-five-eight
	@GET
	@Path("{graph}/{query}")
	@Produces(MediaType.APPLICATION_JSON)
	public String getEdges(@PathParam("graph") String graph,
			@PathParam("query") String query) {

		String logString = "Query: " + graph + "/" + query + "\n";
		
		long timeStart;
		HashMap<String, String> leafToCluster;

		timeStart = System.nanoTime();
		HierarchyTree tree = null;
		try {
			tree = cache.get(graph);
		} catch (ExecutionException e) {
			LOGGER.severe(e.toString());
			return "{\"error\": \"cache error\"}";
		}
		String[] split = query.split("-");
		logString += Integer.toString(split.length) + " vertices in query\n";
		leafToCluster = new HashMap<>();
		for (String s : split) {
			for (String d : tree.getLeaves(s)) {
				leafToCluster.put(d, s);
			}
		}
		logString += "Tree interaction took "
				+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000))
				+ "ms\n";

		InputStream edge_is = cl.getResourceAsStream("graphs/" + graph
				+ "/edges.txt");
		timeStart = System.nanoTime();
		String response = null;
		try {
			response = EdgeSet.getAsJSON(leafToCluster, edge_is);
		} catch (NumberFormatException | IOException e) {
			LOGGER.severe(e.toString());
			return "{\"error\": \"could not read edge set\"}";
		}
		logString += "Edge interaction took "
				+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000))
				+ "ms\n";
		logString += "Query success, response size (chars): "
				+ Integer.toString(response.length());
		LOGGER.info(logString);
		return response;
	}
}
