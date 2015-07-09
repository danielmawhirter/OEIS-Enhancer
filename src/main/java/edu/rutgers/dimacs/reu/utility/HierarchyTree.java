package edu.rutgers.dimacs.reu.utility;
//utility, wrapper over TreeNode

import java.util.TreeMap;
import java.util.Set;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HierarchyTree {
  private TreeNode<String> root = null;
  private TreeMap<String, TreeNode<String>> lookup = null;
  
  public HierarchyTree(String file_path) {
    lookup = new TreeMap<>();
    root = buildTree(new File(file_path));
  }
  
  public HierarchyTree(File file) {
    lookup = new TreeMap<>();
    root = buildTree(file);
  }
  
  public HierarchyTree(InputStream is) {
    lookup = new TreeMap<>();
    root = buildTree(is);
  }
  
  public Set<String> getLeaves(String id) {
    TreeNode<String> node = lookup.get(id);
    if(null == node) {
      System.out.println(id + " does not appear in tree");
      return null;
    }
    else return node.getLeafObjects();
  }
  
  public boolean isEmpty() {
    return lookup.size() == 0;
  }
  
  private TreeNode<String> buildTree(File file) {
    TreeNode<String> root = null;
    try (BufferedReader br = new BufferedReader(
      new FileReader(file))) {
      root = buildTreeRecursive(br);
      br.close();
    } catch (IOException e) {
      e.printStackTrace(System.out);
    }
    return root;
  }
  
  private TreeNode<String> buildTree(InputStream is) {
    TreeNode<String> root = null;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
      root = buildTreeRecursive(br);
      is.close();
      br.close();
    } catch (IOException e) {
      e.printStackTrace(System.out);
    }
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
  
  public void outputToFile(String file_path) {
    TreeNode.outputTree(this.root, file_path);
  }
  
}
