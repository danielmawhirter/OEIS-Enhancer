package edu.rutgers.dimacs.reu.utility;
//utility, tree structure containing objects

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class TreeNode<T> {

	private T object = null;
	private TreeNode<T> parent = null;
	private ArrayList<TreeNode<T>> children = null;

	public TreeNode(T userObject) {
		this.object = userObject;
		this.children = new ArrayList<TreeNode<T>>();
	}

	public T getObject() {
		return this.object;
	}
	
	public void setObject(T object) {
		this.object = object;
	}

	@Override
	public String toString() {
		return this.object.toString();
	}

	public void addChild(TreeNode<T> node) {
		node.parent = this;
		this.children.add(node);
	}
	
	public void removeChild(TreeNode<T> node) {
		this.children.remove(node);
	}
	
	public void clearChildren() {
		this.children.clear();
	}

	public int getChildCount() {
		return this.children.size();
	}

	public ArrayList<TreeNode<T>> getChildren() {
		return this.children;
	}

	public Set<T> getLeafObjects() {
		Set<T> leaves = new HashSet<>();
		LinkedList<TreeNode<T>> queue = new LinkedList<>();
		queue.push(this);
		while (queue.size() > 0) {
			TreeNode<T> current = queue.pop();
			if (current.getChildCount() == 0)
				leaves.add(current.getObject());
			else
				for (TreeNode<T> child : current.getChildren())
					queue.push(child);
		}
		return leaves;
	}

	public TreeNode<T> getParent() {
		return this.parent;
	}

	public static TreeNode<String> buildTree(String file_path) {
		TreeNode<String> root = null;
		try (BufferedReader br = new BufferedReader(new FileReader(file_path))) {
			root = buildTreeRecursive(br);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return root;
	}

	private static TreeNode<String> buildTreeRecursive(BufferedReader br)
			throws IOException {
		String[] line = br.readLine().split(" ");
		TreeNode<String> node = new TreeNode<>(line[0]);
		for (int i = 0; i < Integer.parseInt(line[1]); i++) {
			node.addChild(buildTreeRecursive(br));
		}
		return node;
	}

	public static void outputTree(TreeNode<?> root, String outfile) {
		try {
			PrintWriter pw = new PrintWriter(outfile, "UTF-8");
			outputTreeStream(root, pw);
			pw.flush();
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void outputTreeStream(TreeNode<?> root, PrintWriter pw) {
		int children = root.getChildCount();
		pw.println(root.getObject().toString() + " "
				+ Integer.toString(children));
		for (TreeNode<?> child : root.getChildren()) {
			outputTreeStream(child, pw);
		}
	}
	
	public static void outputTreeJSON(TreeNode<?> root, String outfile) {
		try {
			PrintWriter pw = new PrintWriter(outfile, "UTF-8");
			outputTreeJSONStream(root, pw);
			pw.flush();
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void outputTreeJSONStream(TreeNode<?> root, PrintWriter pw) {
		pw.write("{\n\"name\":\"");
		pw.write(root.toString());
		pw.write("\"");
		int childCount = root.getChildCount();
		boolean hasChildren = childCount > 0;
		if(hasChildren) {
			pw.write(",\"children\": [");
		}
		for (TreeNode<?> child : root.getChildren()) {
			outputTreeJSONStream(child, pw);
			if(--childCount > 0) pw.write(",");
		}
		if(hasChildren) 
			pw.write("]\n");
		pw.write("}");
	}
	
	@Override
	public boolean equals(Object o) {
		return o instanceof TreeNode&& ((TreeNode<?>)o).object.equals(this.object);
	}

}
