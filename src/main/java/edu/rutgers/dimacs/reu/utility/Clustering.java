package edu.rutgers.dimacs.reu.utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class Clustering {
	public static Map<Integer, ArrayList<Integer>> generate_lmToPath(HashMap<Integer, HashSet<Integer>> ungraph) {
		HashMap<Integer, HashSet<Integer>> ccToSeqs = components(ungraph);
		Map<Integer, ArrayList<Integer>> local_lmToPath = new HashMap<>();
		for (Integer cc : ccToSeqs.keySet()) {
			HashSet<Integer> seqs = ccToSeqs.get(cc);
			HashSet<Integer> landmarks = new HashSet<Integer>();
			Integer center = getCenter(seqs, ungraph);
			landmarks.add(center);
			if(seqs.size() > 64) {
				while (landmarks.size() < Math.pow(Math.log(seqs.size()), 2)) {
					Integer toAdd = getLandmark(ungraph, landmarks);
					landmarks.add(toAdd);
				}
			}
			local_lmToPath.putAll(lmToPath(center, landmarks, ungraph));
		}
		return local_lmToPath;
	}
	
	private static HashMap<Integer, HashSet<Integer>> components(HashMap<Integer, HashSet<Integer>> ungraph) {
		ArrayList<Integer> idValue = new ArrayList<>(ungraph.keySet());
		Map<Integer, Integer> idToIndex = new HashMap<>();
		for(int i = 0; i < idValue.size(); i++) {
			idToIndex.put(idValue.get(i), i);
		}
		UF sets = new UF(idValue.size());
		for(Integer s : ungraph.keySet()) {
			for(Integer d : ungraph.get(s)) {
				sets.union(idToIndex.get(s), idToIndex.get(d));
			}
		}
		HashMap<Integer, HashSet<Integer>> ccs = new HashMap<>();
		for(int i = 0; i < idValue.size(); i++) {
			int cid = sets.find(i);
			ccs.putIfAbsent(cid, new HashSet<Integer>());
			ccs.get(cid).add(idValue.get(i));
		}
		return ccs;
	}
	
	public static Integer getCenter(HashSet<Integer> ccSeqs, HashMap<Integer, HashSet<Integer>> ungraph) {
		Integer start = 0;
		for (Integer i : ccSeqs) {
			if (ungraph.containsKey(i)) {
				start = i;
				break;
			}
		}
		HashMap<Integer, HashSet<Integer>> bfs_tree = BFS_TREE(ungraph, start);
		Integer lastPeeled = lastPeeled(bfs_tree, 2);

		HashMap<Integer, HashSet<Integer>> bfs_tree2 = BFS_TREE(ungraph, lastPeeled);

		return lastPeeled(bfs_tree2, 2);
	}
	
	public static Integer lastPeeled(HashMap<Integer, HashSet<Integer>> ungraph, int layers) {
		Integer lastPeeled = 0;
		for (int i = 0; i <= layers; i++) {
			while (!isPeeled(ungraph, i)) {
				ArrayList<Integer> toRemove = new ArrayList<>();
				for (Integer n : ungraph.keySet()) {
					if (ungraph.get(n).size() <= i) {
						toRemove.add(n);
					}
				}

				for (Integer n : toRemove) {
					lastPeeled = n;
					if(ungraph.containsKey(n)) {
						for(Integer x : ungraph.get(n)) {
							if(ungraph.containsKey(n)) {
								ungraph.get(x).remove(n);
							}
						}
						ungraph.remove(n);
					}
				}
			}
		}

		return lastPeeled;
	}
	
	public static boolean isPeeled(HashMap<Integer, HashSet<Integer>> ungraph, int round) {
		for(Integer i : ungraph.keySet()) {
			if(ungraph.get(i).size() <= round) {
				return false;
			}
		}
		return true;
	}

	public static Integer getLandmark(HashMap<Integer, HashSet<Integer>> ungraph, HashSet<Integer> landmarks) {
		Integer toAdd = null;

		//BFS
		Queue<Integer> queue = new LinkedList<Integer>();
		HashSet<Integer> visited = new HashSet<Integer>();
		for (Integer lmInt : landmarks) {
			queue.add(lmInt);
			visited.add(lmInt);
		}
		while (!queue.isEmpty()) {
			Integer u = queue.remove();
			//System.out.println("u:" + u.toString());
			toAdd = Integer.parseInt(u.toString());
			for (Integer v : ungraph.get(u)) {
				if (!visited.contains(v)) {
					visited.add(v);
					queue.add(v);
				}
			}
		}

		return toAdd;
	}
	
	public static HashMap<Integer, ArrayList<Integer>> lmToPath(Integer center, HashSet<Integer> landmarks, HashMap<Integer, HashSet<Integer>> ungraph) {
		// create a map from each landmark to its path to the center
		HashMap<Integer, ArrayList<Integer>> lmToPath = new HashMap<>();
		ArrayList<Integer> center_path = new ArrayList<>();
		center_path.add(center);
		lmToPath.put(center, center_path);

		for (Integer lm : landmarks) {
			// bfs until center is found
			Queue<Integer> queue = new LinkedList<>();
			HashSet<Integer> visited = new HashSet<>();
			HashMap<Integer, Integer> gnToParent = new HashMap<>();
			queue.add(lm);
			visited.add(lm);
			gnToParent.put(lm, lm);
			bfs: while (!queue.isEmpty()) {
				Integer u = queue.remove();
				for (Integer v : ungraph.get(u)) {
					if (!visited.contains(v)) {
						gnToParent.put(v, u);
						if (v.toString().equals(center.toString())) {
							// start tracing the parent path back to lm
							ArrayList<Integer> pathToCenter = new ArrayList<>();
							Integer curr = v;
							while (gnToParent.get(curr) != curr) {
								pathToCenter.add(curr);
								curr = gnToParent.get(curr);
							}
							pathToCenter.add(lm);
							lmToPath.put(lm, pathToCenter);
							break bfs;
						}
						queue.add(v);
						visited.add(v);
					}
				}
			}
		}

		return lmToPath;
	}
	
	public static HashMap<Integer, HashSet<Integer>> BFS_TREE(HashMap<Integer, HashSet<Integer>> ungraph, Integer center) {
		//BFS
		Queue<Integer> queue = new LinkedList<Integer>();
		HashMap<Integer, Integer> gnToParent = new HashMap<>();
		HashSet<Integer> visited = new HashSet<Integer>();
		queue.add(center);
		gnToParent.put(center, center);
		visited.add(center);

		while (!queue.isEmpty()) {
			Integer u = queue.remove();
			for (Integer v : ungraph.get(u)) {
				if (!visited.contains(v)) {
					visited.add(v);
					gnToParent.put(v, u);
					queue.add(v);
				}
			}
		}

		// build the tree from gnToParent
		HashMap<Integer, HashSet<Integer>> tree = new HashMap<>();
		for (Integer n : visited) {
			tree.put(n, new HashSet<Integer>());
		}

		for (Integer v : gnToParent.keySet()) {
			Integer u = gnToParent.get(v);
			if (v.equals(u)) {
				continue;
			}
			tree.get(v).add(u);
			tree.get(u).add(v);
		}

		return tree;

	}
	
}
