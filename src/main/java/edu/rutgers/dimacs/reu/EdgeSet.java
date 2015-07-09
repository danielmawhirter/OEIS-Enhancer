package edu.rutgers.dimacs.reu;
//utility, produces json edge set between subset of antichain

import java.util.Map;
import java.util.TreeMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class EdgeSet {
  public static String getAsJSON(Map<String, String> leafToCluster, InputStream is) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
      EdgeCounter counter = new EdgeCounter();
      String line;
      while((line = br.readLine()) != null) {
        String[] split = line.split(" ");
        String lineCluster = leafToCluster.get(split[0]);
        if(lineCluster != null) {
          for(int i = 0; i < Integer.parseInt(split[1]); i++) {
            String itemCluster = leafToCluster.get(split[2+i]);
            if(itemCluster != null && !lineCluster.equals(itemCluster)) {
              counter.add(lineCluster, itemCluster);
            }
          }
        }
      }
      is.close();
      br.close();
      System.out.println(Integer.toString(counter.getEdgeCount()) + " edges found");
      return counter.toString();
    } catch (IOException e) {
      e.printStackTrace(System.out);
      System.out.println("Query failed");
      return "{ \"error\": \"failed to read edgeset\" }";
    }
  }
  
  public static class EdgeCounter {
    private TreeMap<Edge, Integer> edges;
    public EdgeCounter() {
      edges = new TreeMap<>();
    }
    public int getEdgeCount() {
      return edges.size();
    }
    public void add(String one, String two) {
      Edge e = new Edge(one, two);
      Integer current = edges.get(e);
      if(current == null) current = 0;
      edges.put(e, current + 1);
    }
    @Override
    public String toString() {
      boolean first = true;
      String result = "[";
      for(Edge e : edges.keySet()) {
        if(first) first = false;
        else result += ",";
        result += "\n{ "+ e.toString() + ", \"value\":\"" + edges.get(e).toString() + "\" }";
      }
      return result + "\n]";
    }
    public static class Edge implements Comparable<Edge> {
      String src, dest;
      public Edge(String one, String two) {
        if(one.compareTo(two) < 0) {
          this.src = one;
          this.dest = two;
        } else {
          this.dest = one;
          this.src = two;
        }
      }
      @Override
      public boolean equals(Object o) {
        return o instanceof Edge && ((Edge)o).src.equals(this.src) && ((Edge)o).dest.equals(this.dest);
      }
      @Override
      public int compareTo(Edge e) {
        int compare = this.src.compareTo(e.src);
        if(compare != 0) return compare;
        return this.dest.compareTo(e.dest);
      }
      @Override
      public String toString() {
        return "\"id\":\"" + src + "--" + dest + "\", \"source_name\":\"" + src + "\", \"target_name\":\"" + dest + "\"";
      }
    }
  }
}
