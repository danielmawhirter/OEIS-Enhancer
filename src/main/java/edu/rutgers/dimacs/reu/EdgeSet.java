package edu.rutgers.dimacs.reu;

//utility, produces json edge set induced on subset of antichain

import java.util.Map;
import java.util.TreeMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

public class EdgeSet {
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

	public static class EdgeCounter {
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
				return "{\"id\":\"" + src + "--" + dest + "\",\"source_name\":\"" + src
						+ "\", \"target_name\":\"" + dest + "\",\"value\":"
						+ count
						+ ((forward && !reverse) ? ",\"forward\":true" : "")
						+ ((reverse && !forward) ? ",\"reverse\":true" : "")
						+ "}";
			}
		}
	}
}
