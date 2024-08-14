package gitlet;

/**
 * The Main class for the Gitlet version-control system.
 * This class parses and executes the commands provided by the user.
 *
 * @author Sufjan Fana
 */
public class Main {

    /**
     * The main method which acts as the entry point for the Gitlet program.
     * It checks the command line arguments and calls the appropriate methods in the Repository class.
     *
     * @param args the command line arguments provided by the user
     */
    public static void main(String[] args) {
        // Check if no command is provided
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }

        // The first argument is the command
        String firstArg = args[0];
        switch (firstArg) {
            case "init":
                lengthCheck(args, 1);
                Repository.init();
                break;
            case "add":
                lengthCheck(args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                lengthCheck(args, 2);
                Repository.commit(args[1]);
                break;
            case "rm":
                lengthCheck(args, 2);
                Repository.rm(args[1]);
                break;
            case "log":
                lengthCheck(args, 1);
                Repository.log();
                break;
            case "global-log":
                lengthCheck(args, 1);
                Repository.globalLog();
                break;
            case "find":
                lengthCheck(args, 2);
                Repository.find(args[1]);
                break;
            case "status":
                lengthCheck(args, 1);
                Repository.status();
                break;
            case "restore":
                // Checks for restore command with different argument formats
                if (args.length == 4 && "--".equals(args[2])) {
                    Repository.restore(args[1], args[3]);
                } else if (args.length == 3 && "--".equals(args[1])) {
                    Repository.restore(args[2]);
                } else {
                    System.out.println("Incorrect operands.");
                }
                break;
            case "branch":
                lengthCheck(args, 2);
                Repository.branch(args[1]);
                break;
            case "switch":
                lengthCheck(args, 2);
                Repository.switchBranch(args[1]);
                break;
            case "rm-branch":
                lengthCheck(args, 2);
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                lengthCheck(args, 2);
                Repository.reset(args[1]);
                break;
            case "merge":
                lengthCheck(args, 2);
                Repository.merge(args[1]);
                break;
            default:
                // If the command does not match any case
                System.out.println("No command with that name exists.");
                break;
        }
    }

    /**
     * A helper method that checks if the number of arguments matches the expected number.
     * If not, it prints an error message and exits the program.
     *
     * @param args the command line arguments provided by the user
     * @param expected the expected number of arguments for the command
     */
    private static void lengthCheck(String[] args, int expected) {
        if (args.length != expected) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }
}
