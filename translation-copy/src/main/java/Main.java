import com.beust.jcommander.JCommander;
import org.jboss.pressgang.ccms.zanata.TranslationCopyTool;

public class Main {
    public static void main(String[] args) {
        final TranslationCopyTool command = new TranslationCopyTool();
        final JCommander jCommander = new JCommander(command);
        jCommander.setProgramName("pressgang-translation-copy");

        jCommander.parse(args);
        command.process();
    }
}
