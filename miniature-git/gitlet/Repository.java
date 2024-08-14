package gitlet;

import java.io.File;
import java.util.*;
import static gitlet.Utils.*;

/**
 * The Repository class manages all operations related to the Gitlet version-control system.
 * It handles initialization, staging, committing, branching, merging, and various other Git commands.
 */
public class Repository {

    /**
     * The current working directory.
     */
    private static final File CWD = new File(System.getProperty("user.dir"));

    /**
     * The Gitlet directory where all the version control data is stored.
     */
    private static final File GITLET_DIR = join(CWD, ".gitlet");

    /**
     * The file representing the current staging area.
     */
    private static final File STAGING_AREA_FILE = join(GITLET_DIR, "staging_area");

    /**
     * The directory where all commits are stored.
     */
    private static final File COMMITS_DIR = join(GITLET_DIR, "commits");

    /**
     * The directory where all blobs (file snapshots) are stored.
     */
    private static final File BLOBS_DIR = join(GITLET_DIR, "blobs");

    /**
     * The file representing the HEAD of the repository, pointing to the active branch.
     */
    private static final File HEAD_FILE = join(GITLET_DIR, "head");

    /**
     * The directory where all branches are stored.
     */
    private static final File BRANCHES_DIR = join(GITLET_DIR, "branches");

    /**
     * A constant representing the length of a full commit ID.
     */
    private static final int FULL_COMMIT_ID_LENGTH = 40;

    /**
     * Initializes a new Gitlet version-control system in the current directory.
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        GITLET_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        BRANCHES_DIR.mkdir();

        // Initialize the staging area
        StagingArea stagingArea = new StagingArea();
        writeObject(STAGING_AREA_FILE, stagingArea);

        // Create the initial commit
        Commit firstCommit = new Commit("initial commit", null, null, new HashMap<>());
        String commitId = firstCommit.getId();
        File commitFile = join(COMMITS_DIR, commitId);
        writeObject(commitFile, firstCommit);

        // Initialize the main branch
        File mainBranch = join(BRANCHES_DIR, "main");
        writeContents(mainBranch, commitId);

        // HEAD points to the branch of the current active commit
        writeContents(HEAD_FILE, "main");
    }

    /**
     * Stages a file for addition in the next commit.
     *
     * @param filename the name of the file to be staged
     */
    public static void add(String filename) {
        File newFile = join(CWD, filename);
        if (newFile.exists()) {
            String fileBlob = readContentsAsString(newFile);
            String blobHash = sha1(fileBlob);
            Commit head = getHeadCommit();
            StagingArea stage = getStage();

            // If file contents are already in the current commit, do nothing
            if (blobHash.equals(head.getFileHash(filename))) {
                stage.removeFromStaged(filename);
            } else {
                stage.addFile(filename, blobHash);
                File blobFile = join(BLOBS_DIR, blobHash);
                writeContents(blobFile, fileBlob);
            }
            // Remove from staged-to-remove no matter what
            stage.removeFromRemoved(filename);
            saveStage(stage);
        } else {
            System.out.println("File does not exist.");
        }
    }

    /**
     * Creates a new commit with the provided message, capturing the current state of the staging area.
     *
     * @param message the commit message
     */
    public static void commit(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.out.println("Please enter a commit message.");
            return;
        }
        StagingArea stage = getStage();
        if (stage.getStagedFiles().isEmpty() && stage.getRemovedFiles().isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }

        Commit headCommit = getHeadCommit();
        Map<String, String> commitFiles = new HashMap<>(headCommit.getFiles());

        // Remove files staged for removal
        for (String file : stage.getRemovedFiles().keySet()) {
            commitFiles.remove(file);
        }

        // Add files staged for addition
        for (String file : stage.getStagedFiles().keySet()) {
            String blobHash = stage.getStagedFiles().get(file);
            commitFiles.put(file, blobHash);
        }

        Commit newCommit = new Commit(message, headCommit.getId(), null, commitFiles);
        String commitId = newCommit.getId();
        File commitFile = join(COMMITS_DIR, commitId);
        writeObject(commitFile, newCommit);

        String currentBranch = getThisBranch();
        File branchFile = join(BRANCHES_DIR, currentBranch);
        writeContents(branchFile, commitId);

        writeContents(HEAD_FILE, currentBranch);

        // Clear the staging area after committing
        stage.clear();
        saveStage(stage);
    }

    /**
     * Removes a file from the staging area or schedules it for removal in the next commit.
     *
     * @param filename the name of the file to be removed
     */
    public static void rm(String filename) {
        StagingArea stage = getStage();
        Commit headCommit = getHeadCommit();
        boolean isTracked = headCommit.getFiles().containsKey(filename);

        // If the file is untracked and unstaged, do nothing
        if (!stage.isStaged(filename) && !isTracked) {
            System.out.println("No reason to remove the file.");
        } else {
            // If the file is staged, unstage it
            if (stage.isStaged(filename)) {
                stage.removeFromStaged(filename);
            }
            // If the file is tracked, stage it for removal and delete it
            if (isTracked) {
                stage.removeFile(filename);
                File doomedFile = join(CWD, filename);
                if (doomedFile.exists()) {
                    restrictedDelete(doomedFile);
                }
            }
            saveStage(stage);
        }
    }

    /**
     * Displays the commit history for the current branch.
     */
    public static void log() {
        Commit commit = getHeadCommit();

        while (commit != null) {
            logPrinter(commit);
            if (commit.getParent() == null) {
                break;
            }
            commit = readObject(join(COMMITS_DIR, commit.getParent()), Commit.class);
        }
    }

    /**
     * Displays the commit history for all commits across all branches.
     */
    public static void globalLog() {
        List<String> allFiles = plainFilenamesIn(COMMITS_DIR);
        if (allFiles == null) {
            return;
        }
        for (String loggedFile : allFiles) {
            File joinedFile = join(COMMITS_DIR, loggedFile);
            Commit tempCom = readObject(joinedFile, Commit.class);
            logPrinter(tempCom);
        }
    }

    /**
     * Prints the details of a given commit.
     *
     * @param printed the commit to be printed
     */
    private static void logPrinter(Commit printed) {
        System.out.println("===");
        System.out.println("commit " + printed.getId());
        if (printed.getSecondParent() != null) {
            System.out.print("Merge: ");
            System.out.print(printed.getParent().substring(0, 7));
            System.out.print(" ");
            System.out.println(printed.getSecondParent().substring(0, 7));
        }
        System.out.println("Date: " + printed.formatDate());
        System.out.println(printed.getMessage());
        System.out.println();
    }

    /**
     * Finds and prints all commit IDs with the specified commit message.
     *
     * @param message the commit message to search for
     */
    public static void find(String message) {
        List<String> commitFiles = plainFilenamesIn(COMMITS_DIR);
        Stack<Commit> foundCommits = new Stack<>();
        for (String id : commitFiles) {
            File currCommit = join(COMMITS_DIR, id);
            Commit commit = readObject(currCommit, Commit.class);
            if (commit.getMessage().equals(message)) {
                System.out.println(id);
                foundCommits.add(commit);
            }
        }
        if (foundCommits.isEmpty()) {
            System.out.println("Found no commit with that message.");
        }
    }

    /**
     * Displays the current status of the repository, including branches, staged files, and untracked files.
     */
    public static void status() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            return;
        }

        // Display branches
        System.out.println("=== Branches ===");
        List<String> branches = plainFilenamesIn(BRANCHES_DIR);
        if (branches != null) {
            branches.sort(String::compareTo);
            String headBranch = getThisBranch();
            for (String branch : branches) {
                if (branch.equals(headBranch)) {
                    System.out.println("*" + branch);
                } else {
                    System.out.println(branch);
                }
            }
        }
        System.out.println();

        StagingArea stage = getStage();

        // Display staged files
        System.out.println("=== Staged Files ===");
        Map<String, String> stagedFiles = stage.getStagedFiles();
        List<String> sortedStagedFiles = new ArrayList<>(stagedFiles.keySet());
        sortedStagedFiles.sort(String::compareTo);
        for (String file : sortedStagedFiles) {
            System.out.println(file);
        }
        System.out.println();

        // Display removed files
        System.out.println("=== Removed Files ===");
        Map<String, String> removedFilesMap = stage.getRemovedFiles();
        List<String> removedFiles = new ArrayList<>(removedFilesMap.keySet());
        removedFiles.sort(String::compareTo);
        for (String file : removedFiles) {
            System.out.println(file);
        }
        System.out.println();

        // Display modifications not staged for commit
        System.out.println("=== Modifications Not Staged For Commit ===");
        List<String> modifiedNotStaged = getModifiedNotStagedFiles();
        modifiedNotStaged.sort(String::compareTo);
        for (String file : modifiedNotStaged) {
            if (!removedFilesMap.containsKey(file.split(" ")[0])) {
                System.out.println(file);
            }
        }
        System.out.println();

        // Display untracked files
        System.out.println("=== Untracked Files ===");
        List<String> untrackedFiles = getUntrackedFiles();
        untrackedFiles.sort(String::compareTo);
        for (String file : untrackedFiles) {
            System.out.println(file);
        }
        System.out.println();
    }

    /**
     * Restores a file from the current commit.
     *
     * @param filename the name of the file to be restored
     */
    public static void restore(String filename) {
        Commit commit = getHeadCommit();
        if (commit != null) {
            restoreHelper(commit, filename);
        }
    }

    /**
     * Restores a file from a specific commit.
     *
     * @param id       the commit ID
     * @param filename the name of the file to be restored
     */
    public static void restore(String id, String filename) {
        String entireId = getEntireId(id);
        if (entireId == null) {
            System.out.println("No commit with that id exists.");
            return;
        }
        File file = join(COMMITS_DIR, entireId);
        if (file.exists()) {
            Commit commit = readObject(file, Commit.class);
            Map<String, String> commitFiles = commit.getFiles();
            if (commitFiles.containsKey(filename)) {
                restoreHelper(commit, filename);
            } else {
                System.out.println("File does not exist in that commit.");
            }
        }
    }

    /**
     * Retrieves the full commit ID based on a partial ID.
     *
     * @param id the partial commit ID
     * @return the full commit ID or null if not found
     */
    public static String getEntireId(String id) {
        List<String> commits = plainFilenamesIn(COMMITS_DIR);
        for (String fullId : commits) {
            if (fullId.startsWith(id)) {
                return fullId;
            }
        }
        return null;
    }

    /**
     * Helper method to restore a file from a commit.
     *
     * @param commit   the commit from which the file is restored
     * @param filename the name of the file to be restored
     */
    private static void restoreHelper(Commit commit, String filename) {
        String blobHash = commit.getFiles().get(filename);
        File blobFile = join(BLOBS_DIR, blobHash);

        String blobContents = readContentsAsString(blobFile);

        File restoreFile = join(CWD, filename);
        writeContents(restoreFile, blobContents);
    }

    /**
     * Creates a new branch with the given name.
     *
     * @param name the name of the new branch
     */
    public static void branch(String name) {
        File newBranch = join(BRANCHES_DIR, name);
        if (newBranch.exists()) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        // Write the current commit ID to the new branch
        writeContents(newBranch, getHeadCommit().getId());
    }

    /**
     * Switches to the branch with the given name.
     *
     * @param name the name of the branch to switch to
     */
    public static void switchBranch(String name) {
        File nextBranch = join(BRANCHES_DIR, name);
        // Failure cases: branch doesn't exist or the switch branch is the current branch
        if (!nextBranch.exists()) {
            System.out.println("No such branch exists.");
        } else if (getThisBranch().equals(name)) {
            System.out.println("No need to switch to the current branch.");
        } else {
            StagingArea stage = getStage();
            // Get all files from the branch to switch to
            String switchID = readContentsAsString(nextBranch);
            Commit switchCommit = readObject(join(COMMITS_DIR, switchID), Commit.class);
            Map<String, String> switchFiles = switchCommit.getFiles();
            Map<String, String> currFiles = getHeadCommit().getFiles();
            // Check if there is an untracked file in the current branch (failure case)
            List<String> untrackedFiles = getUntrackedFiles();
            for (String file : untrackedFiles) {
                if (switchFiles.containsKey(file)) {
                    System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                    return;
                }
            }
            // Overwrite versions of files that already exist (if they exist)
            for (String file : switchFiles.keySet()) {
                String blob = readContentsAsString(join(BLOBS_DIR, switchFiles.get(file)));
                File overFile = join(CWD, file);
                writeContents(overFile, blob);
            }
            // Delete files that are tracked in the current branch but not in the new branch
            for (String file : currFiles.keySet()) {
                if (!switchFiles.containsKey(file)) {
                    restrictedDelete(join(CWD, file));
                }
            }
            // Make the given branch the new HEAD branch
            writeContents(HEAD_FILE, name);
            // Clear the staging area and save
            stage.clear();
            saveStage(stage);
        }
    }

    /**
     * Removes the branch with the given name.
     *
     * @param name the name of the branch to be removed
     */
    public static void rmBranch(String name) {
        File doomedBranch = join(BRANCHES_DIR, name);
        if (!doomedBranch.exists()) {
            System.out.println("A branch with that name does not exist.");
        } else if (getThisBranch().equals(name)) {
            System.out.println("Cannot remove the current branch.");
        } else {
            doomedBranch.delete();
        }
    }

    /**
     * Resets the current branch to the commit with the given ID.
     *
     * @param commitId the ID of the commit to reset to
     */
    public static void reset(String commitId) {
        // Failure cases
        File commitFile = join(COMMITS_DIR, commitId);
        if (!commitFile.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }
        StagingArea stage = getStage();
        Commit commit = readObject(commitFile, Commit.class);
        List<String> untrackedFiles = getUntrackedFiles();
        for (String file : untrackedFiles) {
            if (commit.getFiles().containsKey(file)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        // Restore tracked files
        for (String file : commit.getFiles().keySet()) {
            String blobContents = readContentsAsString(join(BLOBS_DIR, commit.getFiles().get(file)));
            File restoreFile = join(CWD, file);
            writeContents(restoreFile, blobContents);
        }

        // Remove tracked files
        Commit headCommit = getHeadCommit();
        for (String file : headCommit.getFiles().keySet()) {
            if (!commit.getFiles().containsKey(file)) {
                restrictedDelete(join(CWD, file));
            }
        }

        // Clear the staging area
        stage.clear();
        saveStage(stage);

        // Move the HEAD pointer
        writeContents(join(BRANCHES_DIR, getThisBranch()), commitId);
    }

    // Helper methods

    /**
     * Retrieves the current staging area from the file system.
     *
     * @return the current staging area
     */
    private static StagingArea getStage() {
        return readObject(STAGING_AREA_FILE, StagingArea.class);
    }

    /**
     * Retrieves the name of the currently active branch.
     *
     * @return the name of the current branch
     */
    private static String getThisBranch() {
        return readContentsAsString(HEAD_FILE);
    }

    /**
     * Retrieves the commit at the head of the currently active branch.
     *
     * @return the head commit
     */
    private static Commit getHeadCommit() {
        String activeBranch = readContentsAsString(HEAD_FILE);
        File branchFile = join(BRANCHES_DIR, activeBranch);
        String commitID = readContentsAsString(branchFile);
        return readObject(join(COMMITS_DIR, commitID), Commit.class);
    }

    /**
     * Saves the current staging area to the file system.
     *
     * @param stage the staging area to be saved
     */
    private static void saveStage(StagingArea stage) {
        writeObject(STAGING_AREA_FILE, stage);
    }

    /**
     * Finds the full commit ID based on a partial ID, if it exists.
     *
     * @param currId the partial commit ID
     * @return the full commit ID if found, or the partial ID if not found
     */
    private static String findId(String currId) {
        if (currId.length() < FULL_COMMIT_ID_LENGTH) {
            List<String> files = plainFilenamesIn(COMMITS_DIR);
            if (files != null) {
                for (String f : files) {
                    if (f.startsWith(currId)) {
                        return f;
                    }
                }
            }
        }
        return currId;
    }

    /**
     * Retrieves a list of modified files that are not staged for commit.
     *
     * @return a list of modified files not staged for commit
     */
    private static List<String> getModifiedNotStagedFiles() {
        List<String> modifiedFiles = new ArrayList<>();
        Commit head = getHeadCommit();
        Map<String, String> headFiles = head.getFiles();
        StagingArea stage = getStage();

        for (String fileName : headFiles.keySet()) {
            File file = join(CWD, fileName);
            if (file.exists()) {
                String fileHash = sha1(readContentsAsString(file));
                if (!fileHash.equals(headFiles.get(fileName)) && !stage.getStagedFiles().containsKey(fileName)) {
                    modifiedFiles.add(fileName + " (modified)");
                }
            } else if (!stage.getRemovedFiles().containsKey(fileName)) {
                modifiedFiles.add(fileName + " (deleted)");
            }
        }

        for (String fileName : stage.getStagedFiles().keySet()) {
            File file = join(CWD, fileName);
            if (!file.exists()) {
                modifiedFiles.add(fileName + " (deleted)");
            } else {
                String fileHash = sha1(readContentsAsString(file));
                if (!fileHash.equals(stage.getStagedFiles().get(fileName))) {
                    modifiedFiles.add(fileName + " (modified)");
                }
            }
        }

        return modifiedFiles;
    }

    /**
     * Retrieves a list of untracked files in the current working directory.
     *
     * @return a list of untracked files
     */
    private static List<String> getUntrackedFiles() {
        List<String> untrackedFiles = new ArrayList<>();
        Commit head = getHeadCommit();
        Map<String, String> headFiles = head.getFiles();
        StagingArea stage = getStage();
        List<String> filesInCWD = plainFilenamesIn(CWD);

        if (filesInCWD != null) {
            for (String fileName : filesInCWD) {
                if (!headFiles.containsKey(fileName) && !stage.getStagedFiles().containsKey(fileName)) {
                    untrackedFiles.add(fileName);
                }
            }
        }
        return untrackedFiles;
    }

    /**
     * Merges the given branch into the current branch.
     *
     * @param branchName the name of the branch to be merged
     */
    public static void merge(String branchName) {
        StagingArea stage = getStage();
        if (!stage.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        File branchFile = join(BRANCHES_DIR, branchName);
        if (!branchFile.exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (branchName.equals(getThisBranch())) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }
        Commit otherCommit = readObject(join(COMMITS_DIR, readContentsAsString(branchFile)), Commit.class);
        Map<String, String> otherFiles = otherCommit.getFiles();
        Commit currCommit = getHeadCommit();
        Map<String, String> currFiles = currCommit.getFiles();
        List<String> untrackedFiles = getUntrackedFiles();
        for (String file : untrackedFiles) {
            if (otherFiles.containsKey(file) || (currFiles.containsKey(file) && !otherFiles.containsKey(file))) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }
        Commit splitPoint = getSplitPoint(currCommit, otherCommit);
        if (splitPoint.getId().equals(currCommit.getId())) {
            switchBranch(branchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        if (splitPoint.getId().equals(otherCommit.getId())) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }
        Set<String> uniqueFiles = new HashSet<>();
        uniqueFiles.addAll(otherFiles.keySet());
        uniqueFiles.addAll(currFiles.keySet());
        uniqueFiles.addAll(splitPoint.getFiles().keySet());
        for (String file : uniqueFiles) {
            boolean currChanged = false;
            boolean otherChanged = false;
            String splitBlob = splitPoint.getFiles().get(file);
            String currBlob = currFiles.get(file);
            String otherBlob = otherFiles.get(file);
            if (splitBlob != null) {
                currChanged = currBlob != null && !currBlob.equals(splitBlob);
                otherChanged = otherBlob != null && !otherBlob.equals(splitBlob);
            }
            if (currChanged && otherChanged) {
                String otherContent = otherBlob != null ? readContentsAsString(join(BLOBS_DIR, otherBlob)) : "";
                String currContent = currBlob != null ? readContentsAsString(join(BLOBS_DIR, currBlob)) : "";
                String conflicting = "<<<<<<< HEAD\n" + currContent + "=======\n" + otherContent + ">>>>>>>\n";
                writeContents(join(CWD, file), conflicting);
                stage.addFile(file, sha1(conflicting));
                System.out.println("Encountered a merge conflict.");
            } else if (splitBlob != null && currBlob != null && otherBlob == null) {
                stage.removeFile(file);
                restrictedDelete(join(CWD, file));
            } else if (splitBlob == null && currBlob == null && otherBlob != null) {
                File path = join(CWD, file);
                writeContents(path, readContentsAsString(join(BLOBS_DIR, otherBlob)));
                stage.addFile(file, otherBlob);
            } else if (splitBlob != null && !currChanged && otherChanged) {
                File path = join(CWD, file);
                writeContents(path, readContentsAsString(join(BLOBS_DIR, otherBlob)));
                stage.addFile(file, otherBlob);
            } else if (splitBlob != null && !currChanged && currBlob == null && otherBlob == null) {
                restrictedDelete(join(CWD, file));
            }
        }
        String mergeMessage = "Merged " + branchName + " into " + getThisBranch() + ".";
        Commit mergeCommit = new Commit(mergeMessage, currCommit.getId(), otherCommit.getId(), stage.getStagedFiles());
        writeObject(join(COMMITS_DIR, mergeCommit.getId()), mergeCommit);
        File mergeBranchFile = join(BRANCHES_DIR, getThisBranch());
        writeContents(mergeBranchFile, mergeCommit.getId());
        stage.clear();
        saveStage(stage);
    }

    /**
     * Finds the split point (common ancestor) between two branches for merging.
     *
     * @param currCommit  the current commit in the active branch
     * @param otherCommit the commit in the branch to be merged
     * @return the common ancestor commit (split point)
     */
    private static Commit getSplitPoint(Commit currCommit, Commit otherCommit) {
        Set<String> traversed = new HashSet<>();
        Queue<Commit> bfs = new LinkedList<>();
        bfs.add(otherCommit);
        bfs.add(currCommit);
        while (!bfs.isEmpty()) {
            Commit commit = bfs.poll();
            if (traversed.contains(commit.getId())) {
                return commit;
            }
            traversed.add(commit.getId());
            if (commit.getParent() != null && commit.getSecondParent() != null) {
                File parentFile = join(COMMITS_DIR, commit.getParent());
                bfs.add(readObject(parentFile, Commit.class));
                File secondFile = join(COMMITS_DIR, commit.getSecondParent());
                bfs.add(readObject(secondFile, Commit.class));
            } else if (commit.getParent() != null && commit.getSecondParent() == null) {
                File parentFile = join(COMMITS_DIR, commit.getParent());
                bfs.add(readObject(parentFile, Commit.class));
            }
        }
        return null;
    }
}
