package org.jboss.pressgang.ccms.zanata;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.redhat.contentspec.processor.ContentSpecParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.pressgang.ccms.rest.v1.client.PressGangCCMSProxyFactoryV1;
import org.jboss.pressgang.ccms.rest.v1.collections.RESTTopicCollectionV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTStringConstantV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTTopicV1;
import org.jboss.pressgang.ccms.rest.v1.exceptions.InternalProcessingException;
import org.jboss.pressgang.ccms.rest.v1.exceptions.InvalidParameterException;
import org.jboss.pressgang.ccms.rest.v1.expansion.ExpandDataDetails;
import org.jboss.pressgang.ccms.rest.v1.expansion.ExpandDataTrunk;
import org.jboss.pressgang.ccms.rest.v1.query.RESTTopicQueryBuilderV1;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.common.VersionUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.CopyTransStatus;
import org.zanata.rest.dto.VersionInfo;

public class DeletionTool {
    private static final Logger log = LoggerFactory.getLogger(DeletionTool.class);
    /**
     * The Default amount of time that should be waited between Zanata REST API Calls.
     */
    private static final Double DEFAULT_ZANATA_CALL_INTERVAL = 0.2;

    /**
     * Jackson object mapper
     */
    private final static ObjectMapper mapper = new ObjectMapper();

    /* Get the system properties */
    private static final String PRESSGANG_SERVER = System.getProperty(CommonConstants.SKYNET_SERVER_SYSTEM_PROPERTY);
    private static final String ZANATA_SERVER = System.getProperty(ZanataConstants.ZANATA_SERVER_PROPERTY);
    private static final String ZANATA_TOKEN = System.getProperty(ZanataConstants.ZANATA_TOKEN_PROPERTY);
    private static final String ZANATA_USERNAME = System.getProperty(ZanataConstants.ZANATA_USERNAME_PROPERTY);
    private static final String ZANATA_PROJECT = System.getProperty(ZanataConstants.ZANATA_PROJECT_PROPERTY);
    private static final String ZANATA_VERSION = System.getProperty(ZanataConstants.ZANATA_PROJECT_VERSION_PROPERTY);
    private static final String MIN_ZANATA_CALL_INTERVAL = System.getProperty(ZanataConstants.MIN_ZANATA_CALL_INTERNAL_PROPERTY);

    /**
     * The minimum amount of time in seconds between calls to the Zanata REST API
     */
    private Long zanataRESTCallInterval;
    private Long lastRESTCallTime = 0L;
    private final ZanataProxyFactory proxyFactory;
    private final IFixedTranslatedDocResource translatedDocResource;
    private final IFixedCopyTransResource copyTransResource;
    private final PressGangCCMSProxyFactoryV1 pressGangProxyFactory;
    private final List<LocaleId> locales;

    public DeletionTool() throws URISyntaxException, InvalidParameterException, InternalProcessingException {
        if (!checkEnvironment()) {
            throw new IllegalStateException("The system variables have not been set for PressGang and Zanata.");
        }

        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());

        pressGangProxyFactory = PressGangCCMSProxyFactoryV1.create(PRESSGANG_SERVER);

        final VersionInfo versionInfo = new VersionInfo();
        versionInfo.setVersionNo(VersionUtilities.getAPIVersion(LocaleId.class));
        versionInfo.setBuildTimeStamp(VersionUtilities.getAPIBuildTimestamp(LocaleId.class));
        proxyFactory = new ZanataProxyFactory(new URI(ZANATA_SERVER), ZANATA_USERNAME, ZANATA_TOKEN, versionInfo);

        translatedDocResource = proxyFactory.getFixedTranslatedDocResource(ZANATA_PROJECT, ZANATA_VERSION);
        copyTransResource = proxyFactory.getFixedCopyTransResource();

        /* Parse the specified time from the System Variables. If no time is set or is invalid then use the default value */
        double zanataRESTCallInterval;
        try {
            zanataRESTCallInterval = Double.parseDouble(MIN_ZANATA_CALL_INTERVAL);
        } catch (NumberFormatException ex) {
            zanataRESTCallInterval = DEFAULT_ZANATA_CALL_INTERVAL;
        } catch (NullPointerException ex) {
            zanataRESTCallInterval = DEFAULT_ZANATA_CALL_INTERVAL;
        }

        this.zanataRESTCallInterval = (long) (zanataRESTCallInterval * 1000);

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
        log.info("Default Locale: " + CommonConstants.DEFAULT_LOCALE);
        log.info("Rate Limiting: " + MIN_ZANATA_CALL_INTERVAL + " seconds per REST call");

        /* Some sanity checking */
        if (PRESSGANG_SERVER == null || PRESSGANG_SERVER.trim().isEmpty() || ZANATA_SERVER == null || ZANATA_SERVER.trim().isEmpty() ||
                ZANATA_TOKEN == null || ZANATA_TOKEN.trim().isEmpty() || ZANATA_USERNAME == null || ZANATA_USERNAME.trim().isEmpty() ||
                ZANATA_PROJECT == null || ZANATA_PROJECT.trim().isEmpty() || ZANATA_VERSION == null || ZANATA_VERSION.trim().isEmpty()) {
            log.error("The " + CommonConstants.SKYNET_SERVER_SYSTEM_PROPERTY + ", " + ZanataConstants.ZANATA_SERVER_PROPERTY + ", " +
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
     * @throws InvalidParameterException
     * @throws InternalProcessingException
     */
    private List<LocaleId> getLocales() throws InvalidParameterException, InternalProcessingException {

        /* Get the Locale constants */
        final RESTStringConstantV1 localeConstant = pressGangProxyFactory.getRESTClient().getJSONStringConstant(
                CommonConstants.LOCALES_STRING_CONSTANT_ID, "");
        final List<String> locales = CollectionUtilities.replaceStrings(CollectionUtilities.sortAndReturn(
                CollectionUtilities.toArrayList(localeConstant.getValue().split("[\\s\r\n]*,[\\s\r\n]*"))), "_", "-");
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
        final RESTTopicV1 contentspec;
        if (revision != null) {
            contentspec = pressGangProxyFactory.getRESTClient().getJSONTopicRevision(contentSpecId, revision, "");
        } else {
            contentspec = pressGangProxyFactory.getRESTClient().getJSONTopic(contentSpecId, "");
        }

        final ContentSpecParser parser = new ContentSpecParser(PRESSGANG_SERVER);
        parser.parse(contentspec.getXml());

        // Create the query to get the latest topics
        final RESTTopicQueryBuilderV1 topicQueryBuilder = new RESTTopicQueryBuilderV1();
        topicQueryBuilder.setTopicIds(parser.getReferencedLatestTopicIds());

        // Create the expand string
        final ExpandDataTrunk expand = new ExpandDataTrunk();
        final ExpandDataTrunk expandTopics = new ExpandDataTrunk(new ExpandDataDetails("topics"));
        expand.setBranches(Arrays.asList(expandTopics));
        final String expandString = mapper.writeValueAsString(expand);

        // Get the latest topics
        RESTTopicCollectionV1 topics = pressGangProxyFactory.getRESTClient().getJSONTopicsWithQuery(topicQueryBuilder.buildQueryPath(),
                expandString);

        // Get the revision topics
        final List<Pair<Integer, Integer>> topicIds = parser.getReferencedRevisionTopicIds();
        for (final Pair<Integer, Integer> revTopicId : topicIds) {
            final RESTTopicV1 revTopic = pressGangProxyFactory.getRESTClient().getJSONTopicRevision(revTopicId.getFirst(),
                    revTopicId.getSecond(), "");
            topics.addItem(revTopic);
        }

        // Add the content spec itself to be deleted
        topics.addItem(contentspec);

        return deleteAndCopyTransForTopics(topics, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topicIds The collection of topics to be deleted and re-populated.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForTopics(
            final List<Integer> topicIds) throws InvalidParameterException, InternalProcessingException, IOException {
        return deleteAndCopyTransForTopics(topicIds, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topicIds The collection of topics to be deleted and re-populated.
     * @param locales  The locales of the translated documents to be deleted.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForTopics(final List<Integer> topicIds,
            final List<LocaleId> locales) throws InvalidParameterException, InternalProcessingException, IOException {
        final RESTTopicQueryBuilderV1 topicQueryBuilder = new RESTTopicQueryBuilderV1();
        topicQueryBuilder.setTopicIds(topicIds);

        final ExpandDataTrunk expand = new ExpandDataTrunk();
        final ExpandDataTrunk expandTopics = new ExpandDataTrunk(new ExpandDataDetails("topics"));
        expand.setBranches(Arrays.asList(expandTopics));
        final String expandString = mapper.writeValueAsString(expand);

        RESTTopicCollectionV1 topics = pressGangProxyFactory.getRESTClient().getJSONTopicsWithQuery(topicQueryBuilder.buildQueryPath(),
                expandString);

        return deleteAndCopyTransForTopics(topics, locales);
    }

    /**
     * Deletes all translated documents and runs copytrans to re-populate the translated data.
     *
     * @param topicIds The collection of topics to be deleted and re-populated.
     * @return True if the topics were processed successfully otherwise false.
     */
    public boolean deleteAndCopyTransForRevisionTopics(
            final List<Pair<Integer, Integer>> topicIds) throws InvalidParameterException, InternalProcessingException, IOException {
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
            final List<LocaleId> locales) throws InvalidParameterException, InternalProcessingException, IOException {

        final RESTTopicCollectionV1 topics = new RESTTopicCollectionV1();
        for (final Pair<Integer, Integer> revTopicId : topicIds) {
            final RESTTopicV1 revTopic = pressGangProxyFactory.getRESTClient().getJSONTopicRevision(revTopicId.getFirst(),
                    revTopicId.getSecond(), "");
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
    public boolean deleteAndCopyTransForTopics(final RESTTopicCollectionV1 topics, final List<LocaleId> locales) {
        if (topics == null || topics.getItems() == null || topics.getItems().isEmpty()) {
            log.error("No topics to delete and run copy trans for.");
            return false;
        }

        // Create the set of topic zanata ids
        final Set<String> topicZanataIds = new HashSet<String>();
        for (final RESTTopicV1 topic : topics.returnItems()) {
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
        ClientResponse<String> response = null;
        try {
            response = translatedDocResource.deleteTranslations(zanataId, localeId, ZANATA_USERNAME, ZANATA_TOKEN);

            final Response.Status status = Response.Status.fromStatusCode(response.getStatus());

            if (status == Response.Status.OK) {
                return true;
            } else {
                log.error(response.getEntity());
            }
        } catch (Exception e) {
            log.error("Failed to delete " + zanataId + " " + localeId.toString(), e);
        } finally {
            if (response != null) {
                response.releaseConnection();
            }

            performZanataRESTCallWaiting();
        }

        return false;
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

        ClientResponse<String> response = null;
        try {
            copyTransResource.startCopyTrans(ZANATA_PROJECT, ZANATA_VERSION, zanataId, ZANATA_USERNAME, ZANATA_TOKEN);

            while (!isCopyTransCompleteForSourceDocument(zanataId)) {
                // Sleep for 1 second
                Thread.sleep(1000);
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to run copyTrans for " + zanataId, e);
        } finally {
            if (response != null) {
                response.releaseConnection();
            }

            performZanataRESTCallWaiting();
        }

        return false;
    }

    /**
     * Check if copy trans has finished processing a source document.
     *
     * @param zanataId The Source Document id.
     * @return True if the source document has finished processing otherwise false.
     */
    protected boolean isCopyTransCompleteForSourceDocument(final String zanataId) {
        CopyTransStatus status = copyTransResource.getCopyTransStatus(ZANATA_PROJECT, ZANATA_VERSION, zanataId, ZANATA_USERNAME,
                ZANATA_TOKEN);
        return status.getPercentageComplete() >= 100;
    }

    /**
     * Sleep for a small amount of time to allow zanata to process other data between requests if the time between calls is less
     * than the wait interval specified.
     */
    private void performZanataRESTCallWaiting() {
        /* No need to wait when the call interval is nothing */
        if (zanataRESTCallInterval <= 0) return;

        long currentTime = System.currentTimeMillis();
        /* Check if the current time is less than the last call plus the minimum wait time */
        if (currentTime < (lastRESTCallTime + zanataRESTCallInterval)) {
            try {
                Thread.sleep(zanataRESTCallInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        /* Set the current time to the last call time. */
        lastRESTCallTime = System.currentTimeMillis();
    }
}
