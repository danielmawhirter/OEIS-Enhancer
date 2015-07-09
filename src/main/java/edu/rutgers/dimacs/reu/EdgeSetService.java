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
  
  public EdgeSetService() {
    super();
    
    cache = CacheBuilder.newBuilder().maximumSize(10)
    		.expireAfterAccess(5, TimeUnit.MINUTES)
    		.build(new CacheLoader<String, HierarchyTree>() {
		@Override
		public HierarchyTree load(String graph) throws Exception {
			InputStream tree_is = cl.getResourceAsStream("graphs/" + graph + "/tree.txt");
			return new HierarchyTree(tree_is);
		}
    });
    
    cl = this.getClass().getClassLoader();
    System.out.println("EdgeSetService instanciated");
  }

  // toy/twelve-five-eight
  @GET
  @Path("{graph}/{query}")
  @Produces(MediaType.APPLICATION_JSON)
  public String getEdges(@PathParam("graph") String graph, @PathParam("query") String query) {
    System.out.println("Query: " + graph + "/" + query);
    long timeStart;
    HashMap<String, String> leafToCluster;
    
    timeStart = System.nanoTime();
    HierarchyTree tree = null;
	try {
		tree = cache.get(graph);
	} catch (ExecutionException e) {
		e.printStackTrace(System.out);
		return "{\"error\": \"cache error\"}";
	}
    if(tree.isEmpty()) return "{ \"error\": \"failed to read tree\" }";
    String[] split = query.split("-");
    System.out.println(Integer.toString(split.length) + " vertices in query");
    leafToCluster = new HashMap<>();
    for(String s : split) {
      for(String d : tree.getLeaves(s)) {
        leafToCluster.put(d, s);
      }
    }
    System.out.println("Tree interaction took " + Integer.toString((int)((System.nanoTime() - timeStart) / 1000000)) + "ms");
    
    InputStream edge_is = cl.getResourceAsStream("graphs/" + graph + "/edges.txt");
    timeStart = System.nanoTime();
    String response = EdgeSet.getAsJSON(leafToCluster, edge_is);
    System.out.println("Edge interaction took " + Integer.toString((int)((System.nanoTime() - timeStart) / 1000000)) + "ms");
    System.out.println("Query success, response size (chars): " + Integer.toString(response.length()));
    return response;
  }
}
