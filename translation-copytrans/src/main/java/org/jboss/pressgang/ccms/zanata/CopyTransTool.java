package org.jboss.pressgang.ccms.zanata;

import java.util.List;
import java.util.Set;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.provider.RESTProviderFactory;
import org.jboss.pressgang.ccms.provider.RESTTopicProvider;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.wrapper.TranslatedContentSpecWrapper;
import org.jboss.resteasy.spi.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyTransTool {
    private static final Logger log = LoggerFactory.getLogger(CopyTransTool.class);
    /**
     * The Default amount of time that should be waited between Zanata REST API Calls.
     */
    private static final Double DEFAULT_ZANATA_CALL_INTERVAL = 0.2;

    private static final String PRESSGANG_SERVER = System.getProperty(CommonConstants.PRESS_GANG_REST_SERVER_SYSTEM_PROPERTY);
    private static final String ZANATA_SERVER = System.getProperty(ZanataConstants.ZANATA_SERVER_PROPERTY);
    private static final String ZANATA_TOKEN = System.getProperty(ZanataConstants.ZANATA_TOKEN_PROPERTY);
    private static final String ZANATA_USERNAME = System.getProperty(ZanataConstants.ZANATA_USERNAME_PROPERTY);
    private static final String ZANATA_PROJECT = System.getProperty(ZanataConstants.ZANATA_PROJECT_PROPERTY);
    private static final String ZANATA_VERSION = System.getProperty(ZanataConstants.ZANATA_PROJECT_VERSION_PROPERTY);
    private static final String MIN_ZANATA_CALL_INTERVAL = System.getProperty(ZanataConstants.MIN_ZANATA_CALL_INTERNAL_PROPERTY);

    @Parameter
    private List<String> ids = Lists.newArrayList();

    @Parameter(names = "--disable-ssl-cert")
    private Boolean disableSSLCert = false;

    private ZanataInterface zanataInterface;
    private RESTProviderFactory providerFactory;
    private double zanataRESTCallInterval;

    public void process() {
        // Check we have a zanata and pressgang server setup
        if (!checkEnvironment()) {
            System.exit(-1);
        }

        // Initialise the factories
        init();

        // Check to make sure we have a content spec to copy translations to
        if (!validateContentSpecIds()) {
            System.exit(-1);
        }

        for (final String id : ids) {
            // Get the content spec id/revision
            final String[] zanataNameSplit = id.replace("CS", "").split("-");
            final Integer contentSpecId = Integer.parseInt(zanataNameSplit[0]);
            final Integer contentSpecRevision = zanataNameSplit.length > 1 ? Integer.parseInt(zanataNameSplit[1]) : null;

            // Get the pushed translation for the specified id/revision
            final TranslatedContentSpecWrapper pushedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(providerFactory,
                    contentSpecId, contentSpecRevision);

            if (pushedContentSpec != null) {
                // Get the Zanata Ids we need to push to
                log.info("Getting the Zanata document ids for content spec {}", pushedContentSpec.getZanataId());
                final Set<String> zanataIds = Utilities.getZanataIds(providerFactory, pushedContentSpec);

                // Run copytrans
                runCopyTransForZanataSourceDocuments(zanataIds);
            } else {
                log.error("No translations available for content spec {}", contentSpecId);
            }
        }
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

        // Some sanity checking
        if (PRESSGANG_SERVER == null || PRESSGANG_SERVER.trim().isEmpty() || ZANATA_SERVER == null || ZANATA_SERVER.trim().isEmpty() ||
                ZANATA_TOKEN == null || ZANATA_TOKEN.trim().isEmpty() || ZANATA_USERNAME == null || ZANATA_USERNAME.trim().isEmpty() ||
                ZANATA_PROJECT == null || ZANATA_PROJECT.trim().isEmpty() || ZANATA_VERSION == null || ZANATA_VERSION.trim().isEmpty()) {
            log.error(
                    "The " + CommonConstants.PRESS_GANG_REST_SERVER_SYSTEM_PROPERTY + ", " + ZanataConstants.ZANATA_SERVER_PROPERTY + ", " +
                            ZanataConstants.ZANATA_TOKEN_PROPERTY + ", " + ZanataConstants.ZANATA_USERNAME_PROPERTY + ", " +
                            ZanataConstants.ZANATA_SERVER_PROPERTY + " and " + ZanataConstants.ZANATA_PROJECT_VERSION_PROPERTY + " " +
                            "system properties need to be defined.");
            return false;
        }

        return true;
    }

    private void init() {
        // Initialise the PressGang provider factory
        providerFactory = RESTProviderFactory.create(PRESSGANG_SERVER);
        providerFactory.getProvider(RESTTopicProvider.class).setExpandTranslations(true);

        // Parse the specified time from the System Variables. If no time is set or is invalid then use the default value
        try {
            zanataRESTCallInterval = Double.parseDouble(MIN_ZANATA_CALL_INTERVAL);
        } catch (NumberFormatException ex) {
            zanataRESTCallInterval = DEFAULT_ZANATA_CALL_INTERVAL;
        } catch (NullPointerException ex) {
            zanataRESTCallInterval = DEFAULT_ZANATA_CALL_INTERVAL;
        }
        log.info("Rate Limiting: " + zanataRESTCallInterval + " seconds per REST call");
        try {
            zanataInterface = new ZanataInterface(zanataRESTCallInterval, disableSSLCert);
        } catch (UnauthorizedException e) {
            log.error("Invalid Zanata credentials!");
        }
    }

    protected boolean validateContentSpecIds() {
        if (ids.isEmpty()) {
            log.error("No IDs specified!");
            return false;
        } else {
            return true;
        }
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
                log.error("Failed running CopyTrans on {}", zanataId);
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
