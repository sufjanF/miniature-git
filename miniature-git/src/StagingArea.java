package gitlet;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * The StagingArea class manages the staging and removal of files for commits.
 * It keeps track of files that are staged for addition or removal.
 * Implements Serializable to allow for saving the staging area state.
 */
public class StagingArea implements Serializable {

    /** A map of files staged for addition, mapping filenames to their respective file hashes. */
    private Map<String, String> stagedFiles;

    /** A map of files staged for removal, mapping filenames to null. */
    private Map<String, String> removedFiles;

    /**
     * Constructs an empty StagingArea.
     * Initializes the stagedFiles and removedFiles maps.
     */
    public StagingArea() {
        stagedFiles = new HashMap<>();
        removedFiles = new HashMap<>();
    }

    /**
     * Adds a file to the staging area for addition.
     *
     * @param filename the name of the file to be added
     * @param fileHash the hash of the file contents
     */
    public void addFile(String filename, String fileHash) {
        stagedFiles.put(filename, fileHash);
    }

    /**
     * Stages a file for removal by adding it to the removedFiles map.
     *
     * @param filename the name of the file to be removed
     */
    public void removeFile(String filename) {
        removedFiles.put(filename, null);
    }

    /**
     * Removes a file from the removedFiles map, effectively undoing its staged removal.
     *
     * @param filename the name of the file to be removed from the removedFiles map
     */
    public void removeFromRemoved(String filename) {
        removedFiles.remove(filename);
    }

    /**
     * Removes a file from the stagedFiles map, effectively undoing its staged addition.
     *
     * @param filename the name of the file to be removed from the stagedFiles map
     */
    public void removeFromStaged(String filename) {
        stagedFiles.remove(filename);
    }

    /**
     * Checks if the staging area is empty (i.e., no files are staged for addition or removal).
     *
     * @return true if both stagedFiles and removedFiles are empty, false otherwise
     */
    public boolean isEmpty() {
        return stagedFiles.isEmpty() && removedFiles.isEmpty();
    }

    /**
     * Returns a copy of the map containing the files staged for addition.
     *
     * @return a new HashMap containing the staged files and their respective hashes
     */
    public Map<String, String> getStagedFiles() {
        return new HashMap<>(stagedFiles);
    }

    /**
     * Checks if a specific file is staged for addition.
     *
     * @param filename the name of the file to check
     * @return true if the file is staged for addition, false otherwise
     */
    public Boolean isStaged(String filename) {
        return stagedFiles.containsKey(filename);
    }

    /**
     * Returns a copy of the map containing the files staged for removal.
     *
     * @return a new HashMap containing the removed files
     */
    public Map<String, String> getRemovedFiles() {
        return new HashMap<>(removedFiles);
    }

    /**
     * Clears the staging area by removing all staged files and files staged for removal.
     */
    public void clear() {
        stagedFiles.clear();
        removedFiles.clear();
    }
}
