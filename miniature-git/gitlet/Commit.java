package gitlet;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static gitlet.Utils.*;

/**
 * The Commit class represents a single commit in the version control system.
 * A commit records the state of the project files, the commit message,
 * the parent commit(s), and a unique ID generated for the commit.
 */
public class Commit implements Serializable {

    /** The message associated with this Commit. */
    private String message;

    /** The timestamp when this Commit was created. */
    private Date timestamp;

    /** The ID of the parent Commit. */
    private String parent;

    /** The ID of the second parent Commit (used in merges). */
    private String secondParent;

    /** A map of file names to their corresponding file hashes in this Commit. */
    private Map<String, String> files;

    /** The unique ID for this Commit, generated based on its contents. */
    private String id;

    /**
     * Constructs a new Commit with the given parameters.
     *
     * @param message      The commit message.
     * @param parent       The ID of the parent commit.
     * @param secondParent The ID of the second parent commit (null if not a merge).
     * @param files        A map of file names to their corresponding file hashes.
     */
    public Commit(String message, String parent, String secondParent, Map<String, String> files) {
        this.message = message;
        // Special timestamp for the initial commit
        if (message.equals("initial commit")) {
            this.timestamp = new Date(0);
        } else {
            this.timestamp = new Date();
        }
        this.parent = parent;
        this.secondParent = secondParent;
        this.files = new HashMap<>(files);
        this.id = generateId();
    }

    /**
     * Generates a unique ID for the commit using its contents.
     *
     * @return The unique ID for this commit.
     */
    public String generateId() {
        return sha1(message,
                timestamp.toString(),
                serialize(this));
    }

    /**
     * Returns the unique ID of this commit.
     *
     * @return The commit ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the message associated with this commit.
     *
     * @return The commit message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the ID of the parent commit.
     *
     * @return The parent commit ID.
     */
    public String getParent() {
        return parent;
    }

    /**
     * Returns the ID of the second parent commit (used in merges).
     *
     * @return The second parent commit ID.
     */
    public String getSecondParent() {
        return secondParent;
    }

    /**
     * Returns the hash of a specific file in this commit.
     *
     * @param certainFile The name of the file.
     * @return The hash of the file.
     */
    public String getFileHash(String certainFile) {
        return files.get(certainFile);
    }

    /**
     * Returns the map of file names to their corresponding file hashes.
     *
     * @return A map of file names to file hashes.
     */
    public Map<String, String> getFiles() {
        return files;
    }

    /**
     * Formats the timestamp of this commit into a readable string.
     *
     * @return The formatted timestamp string.
     */
    public String formatDate() {
        SimpleDateFormat formatDate = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        return formatDate.format(timestamp);
    }
}
