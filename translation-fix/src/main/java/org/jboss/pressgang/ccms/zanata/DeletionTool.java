package org.jboss.pressgang.ccms.zanata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.provider.RESTProviderFactory;
import org.jboss.pressgang.ccms.provider.ServerSettingsProvider;
import org.jboss.pressgang.ccms.provider.TopicProvider;
import org.jboss.pressgang.ccms.rest.v1.expansion.ExpandDataDetails;
import org.jboss.pressgang.ccms.rest.v1.expansion.ExpandDataTrunk;
import org.jboss.pressgang.ccms.rest.v1.query.RESTTopicQueryBuilderV1;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.pressgang.ccms.wrapper.ServerSettingsWrapper;
import org.jboss.pressgang.ccms.wrapper.TopicWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedContentSpecWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.CollectionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.common.LocaleId;

public class DeletionTool {
    private static final Logger log = LoggerFactory.getLogger(DeletionTool.class);
    /**
     * The Default amount of time that should be waited between Zanata REST API Calls.
     */
    private static final Double DEFAULT_ZANATA_CALL_INTERVAL = 0.2;
    private static int MAX_DOWNLOAD_SIZE = 400;

    /* Get the system properties */
    private static final String PRESSGANG_SERVER = System.getProperty(CommonConstants.PRESS_GANG_REST_SERVER_SYSTEM_PROPERTY);
    private static final String ZANATA_SERVER = System.getProperty(ZanataConstants.ZANATA_SERVER_PROPERTY);
    private static final String ZANATA_TOKEN = System.getProperty(ZanataConstants.ZANATA_TOKEN_PROPERTY);
    private static final String ZANATA_USERNAME = System.getProperty(ZanataConstants.ZANATA_USERNAME_PROPERTY);
    private static final String ZANATA_PROJECT = System.getProperty(ZanataConstants.ZANATA_PROJECT_PROPERTY);
    private static final String ZANATA_VERSION = System.getProperty(ZanataConstants.ZANATA_PROJECT_VERSION_PROPERTY);
    private static final String MIN_ZANATA_CALL_INTERVAL = System.getProperty(ZanataConstants.MIN_ZANATA_CALL_INTERNAL_PROPERTY);

    /**
     * The minimum amount of time in seconds between calls to the Zanata REST API
     */
    private final ZanataInterface zanataInterface;
    private final RESTProviderFactory providerFactory;
    private final List<LocaleId> locales;
    private final Set<String> ignoreZanataIds = new HashSet<String>();

    public DeletionTool() throws Exception {
        if (!checkEnvironment()) {
            throw new IllegalStateException("The system variables have not been set for PressGang and Zanata.");
        }

        providerFactory = RESTProviderFactory.create(PRESSGANG_SERVER);

        /* Parse the specified time from the System Variables. If no time is set or is invalid then use the default value */
        double zanataRESTCallInterval;
        try {
            zanataRESTCallInterval = Double.parseDouble(MIN_ZANATA_CALL_INTERVAL);
        } catch (NumberFormatException ex) {
            zanataRESTCallInterval = DEFAULT_ZANATA_CALL_INTERVAL;
        } catch (NullPointerException ex) {
            zanataRESTCallInterval = DEFAULT_ZANATA_CALL_INTERVAL;
        }

        zanataInterface = new ZanataInterface(zanataRESTCallInterval);

        locales = getLocales();
    }

    /**
     * @return true if all environment variables were set, false otherwise
     */
    private boolean checkEnvironment() {
        log.info("PressGang REST: " + PRESSGANG_SERVER);
        log.info("Zanata Server: " + ZANATA_SERVER);
        log.info("Zanata Username: " + ZANATA_USERNAME);
        log.info("Zanata Token: " + ZANATA_TOKEN);
        log.info("Zanata Project: " + ZANATA_PROJECT);
        log.info("Zanata Project Version: " + ZANATA_VERSION);
        log.info("Rate Limiting: " + MIN_ZANATA_CALL_INTERVAL + " seconds per REST call");

        /* Some sanity checking */
        if (PRESSGANG_SERVER == null || PRESSGANG_SERVER.trim().isEmpty() || ZANATA_SERVER == null || ZANATA_SERVER.trim().isEmpty() ||
                ZANATA_TOKEN == null || ZANATA_TOKEN.trim().isEmpty() || ZANATA_USERNAME == null || ZANATA_USERNAME.trim().isEmpty() ||
                ZANATA_PROJECT == null || ZANATA_PROJECT.trim().isEmpty() || ZANATA_VERSION == null || ZANATA_VERSION.trim().isEmpty()) {
            log.error("The " + CommonConstants.PRESS_GANG_REST_SERVER_SYSTEM_PROPERTY + ", " + ZanataConstants.ZANATA_SERVER_PROPERTY + ", " +
                    "" + ZanataConstants.ZANATA_TOKEN_PROPERTY + ", " + ZanataConstants.ZANATA_USERNAME_PROPERTY + ", " +
                    "" + ZanataConstants.ZANATA_SERVER_PROPERTY + " and " + ZanataConstants.ZANATA_PROJECT_VERSION_PROPERTY + " " +
                    "system properties need to be defined.");
            return false;
        }

        return true;
    }

    /**
     * Get the locales from the server that should be used to delete translated documents.
     *
     * @return
     */
    private List<LocaleId> getLocales() {
        final ServerSettingsWrapper serverSettings = providerFactory.getProvider(ServerSettingsProvider.class).getServerSettings();

        // Get the Locale constants
        final List<String> locales = serverSettings.getLocales();
        final List<LocaleId> localeIds = new ArrayList<LocaleId>();
        for (final String locale : locales) {
            // Ignore the en-US locale
            if (!(locale.equals("en_US") || locale.equals("en-US"))) {
                localeIds.add(LocaleId.fromJavaName(locale));
            }
        }

        log.info("Locales: " + CollectionUtilities.toSeperatedString(localeIds, ", "));

        return localeIds;
    }

    public void addContentSpecToIgnoreList(Integer contentSpecId, Integer revision) throws Exception {
        final TranslatedContentSpecWrapper pushedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(providerFactory,
                contentSpecId, revision);

        // Create the set of topic zanata ids
        final Set<String> topicZanataIds = Utilities.getZanataIds(providerFactory, pushedContentSpec);

        // Add the Zanata Ids so that they will be ignored
        ignoreZanataIds.addAll(topicZanataIds);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data for a content spec.
     *
     * @param contentSpecId The ID of the content specification to be processed.
     * @param revision      The revision of the content specification to be processed.
     * @return True if the topics were processed successfully otherwise false.
     * @throws Exception Thrown if an error occurs while looking up a content spec, or parsing it's contents.
     */
    public boolean deleteAndCopyTransForContentSpec(Integer contentSpecId, Integer revision) throws Exception {
        return deleteAndCopyTransForContentSpec(contentSpecId, revision, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data for a content spec.
     *
     * @param contentSpecId The ID of the content specification to be processed.
     * @param revision      The revision of the content specification to be processed.
     * @param locales       The locales of the translated documents to be deleted.
     * @return True if the topics were processed successfully otherwise false.
     * @throws Exception Thrown if an error occurs while looking up a content spec, or parsing it's contents.
     */
    public boolean deleteAndCopyTransForContentSpec(Integer contentSpecId, Integer revision,
            final List<LocaleId> locales) throws Exception {
        final TranslatedContentSpecWrapper pushedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(providerFactory,
                contentSpecId, revision);

        // Create the set of topic zanata ids
        final Set<String> topicZanataIds = Utilities.getZanataIds(providerFactory, pushedContentSpec);
        return deleteAndCopyTransForTopics(topicZanataIds, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topicIds The collection of topics to be deleted and re-populated.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForTopics(final List<Integer> topicIds) throws IOException {
        return deleteAndCopyTransForTopics(topicIds, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topicIds The collection of topics to be deleted and re-populated.
     * @param locales  The locales of the translated documents to be deleted.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForTopics(final List<Integer> topicIds, final List<LocaleId> locales) throws IOException {
        final RESTTopicQueryBuilderV1 topicQueryBuilder = new RESTTopicQueryBuilderV1();
        topicQueryBuilder.setTopicIds(topicIds);

        final ExpandDataTrunk expand = new ExpandDataTrunk();
        final ExpandDataTrunk expandTopics = new ExpandDataTrunk(new ExpandDataDetails("topics"));
        expand.setBranches(Arrays.asList(expandTopics));

        final CollectionWrapper<TopicWrapper > topics = providerFactory.getProvider(TopicProvider.class)
                .getTopicsWithQuery(topicQueryBuilder.getQuery());

        return deleteAndCopyTransForTopics(topics, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topicIds The collection of topics to be deleted and re-populated.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForRevisionTopics(final List<Pair<Integer, Integer>> topicIds) throws IOException {
        return deleteAndCopyTransForRevisionTopics(topicIds, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topicIds The collection of topics to be deleted and re-populated.
     * @param locales  The locales of the translated documents to be deleted.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForRevisionTopics(final List<Pair<Integer, Integer>> topicIds,
            final List<LocaleId> locales) throws IOException {

        final TopicProvider topicProvider = providerFactory.getProvider(TopicProvider.class);
        final CollectionWrapper<TopicWrapper> topics = topicProvider.newTopicCollection();
        for (final Pair<Integer, Integer> revTopicId : topicIds) {
            final TopicWrapper revTopic = topicProvider.getTopic(revTopicId.getFirst(), revTopicId.getSecond());
            topics.addItem(revTopic);
        }

        return deleteAndCopyTransForTopics(topics, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topics  The collection of topics to be deleted and re-populated.
     * @param locales The locales of the translated documents to be deleted.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForTopics(final CollectionWrapper<TopicWrapper> topics, final List<LocaleId> locales) {
        if (topics == null || topics.getItems() == null || topics.getItems().isEmpty()) {
            log.error("No topics to delete and run copy trans for.");
            return false;
        }

        // Create the set of topic zanata ids
        final Set<String> topicZanataIds = new HashSet<String>();
        for (final TopicWrapper topic : topics.getItems()) {
            topicZanataIds.add(topic.getId() + "-" + topic.getRevision());
        }

        return deleteAndCopyTransForTopics(topicZanataIds, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param zanataIds The collection of Zanata Document Ids to be deleted and re-populated.
     * @param locales   The locales of the translated documents to be deleted.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForTopics(final Set<String> zanataIds, final List<LocaleId> locales) {

        // Delete all the translation documents from zanata
        final Set<String> processedZanataIds = new HashSet<String>();
        boolean error = false;
        if (!deleteZanataTranslatedDocuments(zanataIds, locales, processedZanataIds)) {
            error = true;
        }

        // run copy trans on the source documents to recreate the translated documents
        if (!runCopyTransForZanataSourceDocuments(processedZanataIds)) {
            error = true;
        }

        return !error;
    }

    /**
     * Delete the Translated Documents from Zanata for a set of topics and relevant locales.
     *
     * @param zanataIds          The collection of zanata document ids to delete the translations for.
     * @param locales            The locales of the translated documents that should be deleted.
     * @param processedZanataIds A collection of zanata ids to add successfully processed zanata document ids to.
     * @return True if all of the translated documents are successfully deleted otherwise false.
     */
    protected boolean deleteZanataTranslatedDocuments(final Set<String> zanataIds, final List<LocaleId> locales,
            final Set<String> processedZanataIds) {
        boolean error = false;
        for (final String zanataId : zanataIds) {
            // Check if the
            if (!ignoreZanataIds.contains(zanataId)) {
                boolean topicError = false;
                for (final LocaleId localeId : locales) {
                    if (!deleteZanataTranslatedDocument(zanataId, localeId)) {
                        topicError = true;
                    }
                }

                if (topicError) {
                    error = true;
                } else {
                    processedZanataIds.add(zanataId);
                }
            } else {
                log.info("Ignoring Zanata Translation " + zanataId);
            }
        }

        return !error;
    }

    /**
     * Delete a Translated Document from zanata for a specific document and locale.
     *
     * @param zanataId The id of the translated document to be deleted.
     * @param localeId The locale of the translated document to be deleted.
     * @return True if the document was deleted successfully, otherwise false.
     */
    protected boolean deleteZanataTranslatedDocument(final String zanataId, LocaleId localeId) {
        log.info("Deleting Zanata Translation " + zanataId + " " + localeId.toString());
        return zanataInterface.deleteTranslation(zanataId, localeId);
    }

    /**
     * Run CopyTrans against a set of topics.
     *
     * @param zanataIds The collection of topics to run copytrans for.
     * @return True if copytrans runs fine for all topics otherwise false.
     */
    protected boolean runCopyTransForZanataSourceDocuments(final Set<String> zanataIds) {
        boolean error = false;
        for (final String zanataId : zanataIds) {
            if (!runCopyTransForZanataSourceDocument(zanataId)) {
                error = true;
            }
        }

        return !error;
    }

    /**
     * Run copy trans against a Source Document in zanata and then wait for it to complete
     *
     * @param zanataId The id of the document to run copytrans for.
     * @return True if copytrans was run successfully, otherwise false.
     */
    protected boolean runCopyTransForZanataSourceDocument(final String zanataId) {
        log.info("Running Zanata CopyTrans for " + zanataId);
        return zanataInterface.runCopyTrans(zanataId, true);
    }
}
