package edu.rutgers.dimacs.reu;
//get info about a node

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.PathParam;

import static javax.ejb.LockType.READ;


@Path("nodeInfo")
@Singleton
@Lock(READ)
public class NodeService {
  
  public NodeService() {
    super();
    System.out.println("NodeService instanciated");
  }
  
  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public String getString(@PathParam("id") int id) {
    String json = "{ \"name\": \"cluster\", \"children\": [ {\"name\": \"AgglomerativeCluster\", \"size\": 3938} ] }";
    /*try {
      BufferedReader br = new BufferedReader(new FileReader(Global.RESOURCE_PATH + "graph.json"));
      json = br.readLine();
      br.close();
    } catch (IOException e) {
      e.printStackTrace(System.out);
    }*/
    return json;
  }
  
}
