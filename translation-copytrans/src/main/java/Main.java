import com.beust.jcommander.JCommander;
import org.jboss.pressgang.ccms.zanata.CopyTransTool;

public class Main {
    public static void main(String[] args) {
        final CopyTransTool command = new CopyTransTool();
        final JCommander jCommander = new JCommander(command);
        jCommander.setProgramName("pressgang-translation-copytrans");

        jCommander.parse(args);
        command.process();
    }
}
