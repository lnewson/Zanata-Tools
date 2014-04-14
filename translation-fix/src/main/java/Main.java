import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.pressgang.ccms.zanata.DeletionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(DeletionTool.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            return;
        }

        final DeletionTool deletionTool = new DeletionTool();

        final String processingType = args[0];
        final List<String> ids = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));
        if (processingType.equals("-t") || processingType.equals("--topic")) {
            // Topic Processing
            final List<Integer> topicIds = new ArrayList<Integer>();
            for (final String id : ids) {
                topicIds.add(Integer.parseInt(id));
            }

            boolean success = deletionTool.deleteAndCopyTransForTopics(topicIds);
            if (success) {
                log.info("Successfully completed processing topics");
            } else {
                log.error("Failed to process topics");
            }
        } else if (processingType.equals("--topic-revisions")) {
            // Topic Revision Processing
            final List<Pair<Integer, Integer>> topicIds = new ArrayList<Pair<Integer, Integer>>();
            for (final String id : ids) {
                String[] vars = id.split("-", 2);
                final Integer topicId = Integer.parseInt(vars[0]);
                final Integer topicRevision = Integer.parseInt(vars[1]);
                topicIds.add(new Pair<Integer, Integer>(topicId, topicRevision));
            }

            boolean success = deletionTool.deleteAndCopyTransForRevisionTopics(topicIds);
            if (success) {
                log.info("Successfully completed processing revision topics");
            } else {
                log.error("Failed to process revision topics");
            }
        } else {
            // Content Spec Processing
            for (final String contentSpecIdString : ids) {
                final Integer contentSpecId;
                final Integer contentSpecRevision;
                if (contentSpecIdString.contains("-")) {
                    String[] vars = contentSpecIdString.split("-", 2);
                    contentSpecId = Integer.parseInt(vars[0]);
                    contentSpecRevision = Integer.parseInt(vars[1]);
                } else {
                    contentSpecId = Integer.parseInt(contentSpecIdString);
                    contentSpecRevision = null;
                }

                boolean success = deletionTool.deleteAndCopyTransForContentSpec(contentSpecId, contentSpecRevision);
                if (success) {
                    log.info("Successfully completed processing content spec " + contentSpecIdString);
                } else {
                    log.error("Failed to process content spec " + contentSpecIdString);
                }
                // Add a blank line to the logs to separate content specs
                log.info("");
            }
        }
    }
}
