package wei_chih.utility;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import wei_chih.service.Config;

/**
 *
 * @author Chienweichih
 */
public class MerkleTree implements Serializable {

    private static class Node implements Serializable {

        private final String fname;
        private String digest;
        private final Node parent;
        private List<Node> children;

        private Node(String fname, String digest, Node parent, List<Node> children) {
            this.fname = fname;
            this.digest = digest;
            this.parent = parent;
            this.children = children;
        }

        private Node(Node node, Node parent) {
            this.fname = node.fname;
            this.digest = node.digest;
            this.parent = parent;
            this.children = null;

            if (node.isDirectory()) {
                this.children = new ArrayList<>();
                for (Node n : node.children) {
                    this.children.add(new Node(n, this));
                }
            }
        }

        private boolean isDirectory() {
            return children != null;
        }

        /**
         * parameter path is substring of file path after root and start with
         * PATH_SEPARATOR
         */
        private static Node getNode(String path, Node root) {
            String pattern = Pattern.quote(Config.PATH_SEPARATOR);
            String[] splittedFileNames = path.split(pattern);

            Node target = root;
            switch (splittedFileNames.length) {
                case 0:
                    return target;
                case 1:
                    try {
                        throw new java.lang.IllegalAccessException("PATH NOT START WITH PATH_SEPARATOR");
                    } catch (IllegalAccessException ex) {
                        Logger.getLogger(MerkleTree.class.getName()).log(Level.SEVERE, null, ex);
                        return null;
                    }
            }

            boolean isAllMatch = false;
            for (int index = 1; index < splittedFileNames.length; ++index) {
                boolean isTokenMatch = false;
                for (Node node : target.children) {
                    if (0 == node.fname.compareTo(splittedFileNames[index])) {
                        isAllMatch = true;
                        isTokenMatch = true;
                        target = node;
                        break;
                    }
                }

                if (isAllMatch && isTokenMatch == false) {
                    try {
                        throw new java.lang.IllegalAccessException("PATH NOT MATCH");
                    } catch (IllegalAccessException ex) {
                        Logger.getLogger(MerkleTree.class.getName()).log(Level.SEVERE, null, ex);
                        return null;
                    }
                }
            }
            return target;
        }
    }

    private final Node root;

    public MerkleTree(MerkleTree merkleTree) {
        this.root = new Node(merkleTree.root, null);
    }

    public MerkleTree(File rootPath) {
        String filePath = rootPath.getAbsolutePath();
        char lastWord = filePath.toUpperCase().charAt(filePath.length() - 1);
        String dataDirPath = "merkletree" + File.separator + lastWord + ".merkletree";

        if (new File(dataDirPath).exists()) {
            MerkleTree merkleTree = (MerkleTree) Utils.Deserialize(dataDirPath);
            this.root = new Node(merkleTree.root, null);
        } else {
            this.root = create(rootPath, null);
        }
    }

    private Node create(File file, Node parent) {
        Node node = new Node(file.getName(), null, parent, null);

        if (file.isFile()) {
            node.digest = Utils.digest(file, Config.DIGEST_ALGORITHM);
        } else {
            node.children = new ArrayList<>();
            String folderDigest = "";

            for (File f : sortedFiles(file.listFiles())) {
                Node newNode = create(f, node);
                node.children.add(newNode);
                folderDigest += newNode.digest;
            }
            node.digest = Utils.digest(Utils.Str2Hex(folderDigest), Config.DIGEST_ALGORITHM);
        }

        return node;
    }

    public void update(String fname, String digest) {
        update(Node.getNode(fname, root), digest);
    }

    private void update(Node node, String digest) {
        node.digest = digest;

        while (node.parent != null) {
            node = node.parent;
            String newDigest = "";

            for (Node n : node.children) {
                newDigest += n.digest;
            }
            node.digest = Utils.digest(Utils.Str2Hex(newDigest), Config.DIGEST_ALGORITHM);
        }
    }

    public void delete(String fname) {
        Node node = Node.getNode(fname, root);
        fname = node.fname;
        node = node.parent;

        int index = 0;
        for (Node n : node.children) {
            if (0 == n.fname.compareTo(fname)) {
                break;
            }
            ++index;
        }
        node.children.remove(index);
        update(node.children.get(0), node.children.get(0).digest);
    }

    public String getRootHash() {
        return root.digest;
    }

    public String getDigest(String path) {
        return Node.getNode(path, root).digest;
    }

    private static List<File> sortedFiles(File[] unSortedFiles) {
        if (unSortedFiles == null) {
            throw new java.lang.NullPointerException();
        }
        List<File> files = Arrays.asList(unSortedFiles);
        Collections.sort(files, (File lhs, File rhs) -> {
            return lhs.getName().compareTo(rhs.getName());
        });
        return files;
    }

    private void print() {
        Queue<Node> queue = new LinkedList<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            System.out.println(node.fname + " " + node.digest);
            if (node.isDirectory()) {
                for (Node n : node.children) {
                    queue.add(n);
                }
            }
        }
    }

    private static String getRoothashFromHashedFiles(Node rootNode) {
        String rootHash = "";

        for (Node n : rootNode.children) {
            if (n.isDirectory()) {
                rootHash += getRoothashFromHashedFiles(n);
            } else {
                rootHash += n.digest;
            }
        }

        return Utils.digest(Utils.Str2Hex(rootHash), Config.DIGEST_ALGORITHM);
    }

    public static void main(String[] args) {

        String pathPrefix = (0 == args[0].compareTo("client")) ? Config.CLIENT_ACCOUNT_PATH
                : Config.SERVER_ACCOUNT_PATH;

        HashMap<String, String> filePath = new HashMap<>();
        filePath.put("A", pathPrefix + "Account A");
        filePath.put("B", pathPrefix + "Account B");
        filePath.put("C", pathPrefix + "Account C");

        for (Entry<String, String> entry : filePath.entrySet()) {
            long time = System.nanoTime();
            MerkleTree merkleTree = new MerkleTree(new File(entry.getValue()));
            time = System.nanoTime() - time;
            System.out.printf("Generate Merkle Tree %s Cost: %.5f s\n", entry.getKey(), time / 1e9);

            for (int i = 0; i < 5; ++i) {
                time = System.nanoTime();
                getRoothashFromHashedFiles(merkleTree.root);
                time = System.nanoTime() - time;
                System.out.printf("Get Roothash From Merkle Tree %s Hashed Files Cost: %.5f s\n", entry.getKey(), time / 1e9);
            }

            time = System.nanoTime();
            Utils.Serialize(new File(entry.getKey() + ".merkletree"), merkleTree);
            time = System.nanoTime() - time;
            System.out.printf("Serialize Merkle Tree %s Cost: %.5f s\n", entry.getKey(), time / 1e9);

            time = System.nanoTime();
            Utils.Deserialize(entry.getKey() + ".merkletree");
            time = System.nanoTime() - time;
            System.out.printf("Deserialize Merkle Tree %s Cost: %.5f s\n", entry.getKey(), time / 1e9);
        }
    }
}
