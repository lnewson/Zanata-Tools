package org.jboss.pressgang.ccms.zanata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.provider.TopicProvider;
import org.jboss.pressgang.ccms.rest.v1.query.RESTTopicQueryBuilderV1;
import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.pressgang.ccms.wrapper.CSInfoNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.CSNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.TopicWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedContentSpecWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.CollectionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utilities {
    private static final Logger log = LoggerFactory.getLogger(Utilities.class);
    private static final int MAX_DOWNLOAD_SIZE = 400;

    /**
     * Get the Zanata IDs that represent a Collection of Content Specs and their Topics.
     *
     * @param providerFactory
     * @param translatedContentSpec
     * @return The Set of Zanata IDs that represent the content specs and topics.
     */
    public static Set<String> getZanataIds(final DataProviderFactory providerFactory,
            final TranslatedContentSpecWrapper translatedContentSpec) {
        final TopicProvider topicProvider = providerFactory.getProvider(TopicProvider.class);
        final Set<String> zanataIds = new HashSet<String>();

        // Get the zanata ids
        zanataIds.add(translatedContentSpec.getZanataId());

        final CollectionWrapper<TranslatedCSNodeWrapper> translatedCSNodes = translatedContentSpec.getTranslatedNodes();
        for (final TranslatedCSNodeWrapper translatedCSNode : translatedCSNodes.getItems()) {
            final CSNodeWrapper csNode = translatedCSNode.getCSNode();
            // Make sure the node is a topic
            if (EntityUtilities.isNodeATopic(csNode)) {
                final TranslatedTopicWrapper pushedTopic = getTranslatedTopic(topicProvider, csNode.getEntityId(),
                        csNode.getEntityRevision(), translatedCSNode);

                // If a pushed topic was found then add it
                if (pushedTopic != null) {
                    zanataIds.add(pushedTopic.getZanataId());
                }
            }

            // Add the info topic if one exists
            if (csNode.getInfoTopicNode() != null) {
                final CSInfoNodeWrapper csNodeInfo = csNode.getInfoTopicNode();
                final TranslatedTopicWrapper pushedTopic = getTranslatedTopic(topicProvider, csNodeInfo.getTopicId(),
                        csNodeInfo.getTopicRevision(), translatedCSNode);

                // If a pushed topic was found then add it
                if (pushedTopic != null) {
                    zanataIds.add(pushedTopic.getZanataId());
                }
            }
        }

        return zanataIds;
    }

    protected static TranslatedTopicWrapper getTranslatedTopic(final TopicProvider topicProvider, final Integer topicId,
            final Integer topicRevision, final TranslatedCSNodeWrapper translatedCSNode) {
        final TopicWrapper topic = topicProvider.getTopic(topicId, topicRevision);

        // Try and see if it was pushed with a condition
        TranslatedTopicWrapper pushedTopic = EntityUtilities.returnPushedTranslatedTopic(topic, translatedCSNode);
        // If pushed topic is null then it means no condition was used
        if (pushedTopic == null) {
            pushedTopic = EntityUtilities.returnPushedTranslatedTopic(topic);
        }

        return pushedTopic;
    }

    /**
     * Download all the topics that are to be used during processing from the
     * parsed Content Specification.
     */
    public static CollectionWrapper<TopicWrapper> downloadAllTopics(final DataProviderFactory providerFactory, final ContentSpec contentSpec) {
        final TopicProvider topicProvider = providerFactory.getProvider(TopicProvider.class);
        final List<SpecTopic> specTopics = contentSpec.getSpecTopics();
        final List<Integer> topicIds = new ArrayList<Integer>();
        final List<Pair<Integer, Integer>> revisionTopicIds = new ArrayList<Pair<Integer, Integer>>();
        CollectionWrapper<TopicWrapper> topics = topicProvider.newTopicCollection();

        // populate the topicIds and revisionTopicIds
        for (final SpecTopic specTopic : specTopics) {
            if (specTopic.getRevision() == null) {
                topicIds.add(specTopic.getDBId());
            } else {
                revisionTopicIds.add(new Pair<Integer, Integer>(specTopic.getDBId(), specTopic.getRevision()));
            }
        }

        // Check if a maximum revision was specified for processing
        if (!topicIds.isEmpty()) {
            // Download the list of topics in one go to reduce I/O overhead
            log.info("Attempting to download all the latest topics...");
            final RESTTopicQueryBuilderV1 queryBuilder = new RESTTopicQueryBuilderV1();
            if (topicIds.size() > MAX_DOWNLOAD_SIZE) {
                int start = 0;
                while (start < topicIds.size()) {
                    final List<Integer> subList = topicIds.subList(start, Math.min(start + MAX_DOWNLOAD_SIZE, topicIds.size()));
                    queryBuilder.setTopicIds(subList);
                    final CollectionWrapper<TopicWrapper> tempTopics = topicProvider.getTopicsWithQuery(queryBuilder.getQuery());

                    for (final TopicWrapper topic : tempTopics.getItems()) {
                        topics.addItem(topic);
                    }

                    start += MAX_DOWNLOAD_SIZE;
                }
            } else {
                queryBuilder.setTopicIds(topicIds);
                topics = topicProvider.getTopicsWithQuery(queryBuilder.getQuery());
            }
        }

        if (!revisionTopicIds.isEmpty()) {
            downloadRevisionTopics(topicProvider, revisionTopicIds, topics);
        }

        return topics;
    }

    /**
     * Download the Topics from the REST API that specify a revision.
     *
     * @param referencedRevisionTopicIds The Set of topic ids and revision to download.
     */
    protected static void downloadRevisionTopics(final TopicProvider topicProvider,
            final List<Pair<Integer, Integer>> referencedRevisionTopicIds, final CollectionWrapper<TopicWrapper> topics) {
        log.info("Attempting to download all the revision topics...");

        final int showPercent = 10;
        final float total = referencedRevisionTopicIds.size();
        float current = 0;
        int lastPercent = 0;

        for (final Pair<Integer, Integer> topicToRevision : referencedRevisionTopicIds) {
            // If we want to update the revisions then we should get the latest topic and not the revision
            topics.addItem(topicProvider.getTopic(topicToRevision.getFirst(), topicToRevision.getSecond()));

            ++current;
            final int percent = Math.round(current / total * 100);
            if (percent - lastPercent >= showPercent) {
                lastPercent = percent;
                log.info(String.format("\tDownloading revision topics %d%% Done", percent));
            }
        }
    }
}
