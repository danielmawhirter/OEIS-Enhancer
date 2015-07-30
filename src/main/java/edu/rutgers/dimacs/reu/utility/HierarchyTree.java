package edu.rutgers.dimacs.reu.utility;

//utility, wrapper over TreeNode

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.io.BufferedWriter;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import javax.ws.rs.core.StreamingOutput;

public class HierarchyTree {
	public TreeNode<String> root = null;
	private Map<String, TreeNode<String>> lookup = null;

	public HierarchyTree(String file_path) {
		lookup = new HashMap<>();
		root = buildTree(new File(file_path));
	}

	public HierarchyTree(File file) {
		lookup = new TreeMap<>();
		root = buildTree(file);
	}

	public HierarchyTree(InputStream is) throws IOException {
		lookup = new TreeMap<>();
		root = buildTree(is);
	}

	public Set<String> getLeaves(String id) {
		TreeNode<String> node = lookup.get(id);
		if (null == node) {
			System.out.println(id + " does not appear in tree");
			return null;
		} else
			return node.getLeafObjects();
	}
	
	public TreeNode<String> getNode(String id) {
		return lookup.get(id);
	}

	public boolean isEmpty() {
		return lookup.size() == 0;
	}
	
	public HierarchyTree mergeIn(HierarchyTree that) {
		TreeNode<String> newRoot = new TreeNode<>("ROOT");
		newRoot.addChild(this.root);
		newRoot.addChild(that.root);
		lookup.putAll(that.lookup);
		lookup.put(newRoot.getObject(), newRoot);
		this.root = newRoot;
		that.root = newRoot;
		return this;
	}

	private TreeNode<String> buildTree(File file) {
		TreeNode<String> root = null;
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			root = buildTreeRecursive(br);
			br.close();
		} catch (IOException e) {
			e.printStackTrace(System.out);
		}
		return root;
	}

	private TreeNode<String> buildTree(InputStream is) throws IOException {
		if(null == is) {
			return null;
		}
		TreeNode<String> root = null;
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		root = buildTreeRecursive(br);
		is.close();
		br.close();
		return root;
	}

	private TreeNode<String> buildTreeRecursive(BufferedReader br)
			throws IOException {
		String[] line = br.readLine().split(" ");
		TreeNode<String> node = new TreeNode<>(line[0]);
		this.lookup.put(line[0], node);
		for (int i = 0; i < Integer.parseInt(line[1]); i++) {
			node.addChild(buildTreeRecursive(br));
		}
		return node;
	}

	public void outputToFile(String file_path) throws FileNotFoundException, UnsupportedEncodingException {
		TreeNode.outputTree(this.root, file_path);
	}

	public StreamingOutput streamJSON() {
		return new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				TreeNode.outputTreeJSONStream(root, writer);
				writer.flush();
				writer.close();
			}
		};
	}

}
