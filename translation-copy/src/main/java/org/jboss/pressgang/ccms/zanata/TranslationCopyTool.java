package org.jboss.pressgang.ccms.zanata;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.beust.jcommander.IVariableArity;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.provider.RESTProviderFactory;
import org.jboss.pressgang.ccms.provider.RESTTopicProvider;
import org.jboss.pressgang.ccms.provider.ServerSettingsProvider;
import org.jboss.pressgang.ccms.provider.exception.NotFoundException;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.wrapper.LocaleWrapper;
import org.jboss.pressgang.ccms.wrapper.ServerSettingsWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedContentSpecWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.CollectionWrapper;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.common.LocaleId;
import org.zanata.rest.client.ITranslatedDocResource;
import org.zanata.rest.dto.resource.TranslationsResource;

public class TranslationCopyTool implements IVariableArity {
    private static final Logger log = LoggerFactory.getLogger(TranslationCopyTool.class);
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

    @Parameter(names = "--langs", description = "A comma separated list of zanata locales to copy translations for")
    private String locales;

    @Parameter(names = "--old-contentspecs", variableArity = true,
            description = "A list of old content spec zanata ids to get the " + "translations from")
    private List<String> oldContentSpecs = Lists.newLinkedList();

    @Parameter(names = "--overwrite", description = "Overwrite any existing translations")
    private Boolean overwrite = false;

    @Parameter(names = "--disable-ssl-cert",
            description = "Disable the SSL Certificate verification. Note: Extreme care should be taken " + "using this option.")
    private Boolean disableSSLCert = false;

    private ZanataInterface zanataInterface;
    private RESTProviderFactory providerFactory;
    private double zanataRESTCallInterval;
    private List<LocaleId> localeIds = new ArrayList<LocaleId>();

    public void process() {
        // Check we have a zanata and pressgang server setup
        if (!checkEnvironment()) {
            System.exit(-1);
        }

        // Initialise the factories
        init();

        // validate the languages used
        log.info("Locales: " + CollectionUtilities.toSeperatedString(localeIds, ", "));
        Utilities.validateLanguages(providerFactory.getProvider(ServerSettingsProvider.class).getServerSettings(),
                locales.split("\\s*,\\s*"));

        // Check to make sure we have a content spec to copy translations to
        if (!validateContentSpecId()) {
            System.exit(-1);
        }

        // Get the content spec id/revision
        final String[] zanataNameSplit = ids.get(0).replace("CS", "").split("-");
        final Integer contentSpecId = Integer.parseInt(zanataNameSplit[0]);
        final Integer contentSpecRevision = zanataNameSplit.length > 1 ? Integer.parseInt(zanataNameSplit[1]) : null;

        // Get the pushed translation for the specified id/revision
        final TranslatedContentSpecWrapper pushedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(providerFactory,
                contentSpecId, contentSpecRevision);

        if (pushedContentSpec != null) {
            // If the old content spec list is empty, then get the previous pushed content spec
            if (oldContentSpecs.isEmpty()) {
                try {
                    final TranslatedContentSpecWrapper translatedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(
                            providerFactory, contentSpecId, pushedContentSpec.getContentSpecRevision() - 1);
                    if (translatedContentSpec != null) {
                        oldContentSpecs.add(translatedContentSpec.getZanataId());
                    }
                } catch (NotFoundException e) {
                    log.error("The content spec has no previous translations available");
                    System.exit(-1);
                }
            }

            // Get the Zanata Ids we need to push to
            log.info("Getting the Zanata document ids for content spec {}", pushedContentSpec.getZanataId());
            final Set<String> zanataIds = Utilities.getZanataIds(providerFactory, pushedContentSpec, true);

            // Collect all the old topic ids
            final Map<String, String> mappedZanataIds = collectOldIds(zanataIds, pushedContentSpec);

            // Copy the translations
            copyTranslations(mappedZanataIds, localeIds);
        } else {
            log.error("No translations available for content spec {}", contentSpecId);
        }
    }

    /**
     * Checks to make sure the required environment variables were set.
     *
     * @return true if all environment variables were set, otherwise false
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

    /**
     * Initialise the components required to copy the translations for a content spec.
     */
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
            System.exit(-1);
        }

        // If the locales are empty then use all the locales configured on the server
        if (locales == null) {
            final ServerSettingsWrapper serverSettings = providerFactory.getProvider(ServerSettingsProvider.class).getServerSettings();
            this.localeIds = initLocales(serverSettings.getLocales());
        } else {
            // Setup the zanata locale ids
            for (final String locale : locales.split("\\s*,\\s*")) {
                localeIds.add(LocaleId.fromJavaName(locale));
            }
        }
    }

    private List<LocaleId> initLocales(final CollectionWrapper<LocaleWrapper> locales) {
        final List<LocaleId> retValue = new ArrayList<LocaleId>();

        // Get the Locales
        for (final LocaleWrapper locale : locales.getItems()) {
            if (!locale.getValue().equals("en-US")) {
                retValue.add(LocaleId.fromJavaName(locale.getTranslationValue()));
            }
        }

        return retValue;
    }

    /**
     * Make sure the user input a content spec to copy translations for.
     *
     * @return True if the user entered information is enough, otherwise false.
     */
    protected boolean validateContentSpecId() {
        if (ids.isEmpty()) {
            log.error("No ID specified!");
            return false;
        } else if (ids.size() > 1) {
            log.error("Too many IDs specified!");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Collects all the old content spec Zanata Document Ids and maps them to their new Zanata Document Ids.
     *
     * @param contentSpecZanataIds The list of new content spec Zanata Document Ids.
     * @return A map of old zanata document ids to new document ids.
     */
    protected Map<String, String> collectOldIds(final Set<String> contentSpecZanataIds,
            final TranslatedContentSpecWrapper pushedContentSpec) {
        final Map<String, String> retValue = new LinkedHashMap<String, String>();

        // 1. Get all the Zanata Ids for the specified old content specs
        final Set<String> oldZanataIds = new LinkedHashSet<String>();
        if (!oldContentSpecs.isEmpty()) {
            for (final String oldContentSpecId : oldContentSpecs) {
                // Get the content spec id/revision
                final String[] zanataNameSplit = oldContentSpecId.replace("CS", "").split("-");
                final Integer contentSpecId = Integer.parseInt(zanataNameSplit[0]);
                final Integer contentSpecRevision = zanataNameSplit.length > 1 ? Integer.parseInt(zanataNameSplit[1]) : null;

                final TranslatedContentSpecWrapper translatedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(providerFactory,
                        contentSpecId, contentSpecRevision);

                if (translatedContentSpec != null) {
                    log.info("Getting the Zanata document ids for content spec {}", translatedContentSpec.getZanataId());
                    oldZanataIds.addAll(Utilities.getZanataIds(providerFactory, translatedContentSpec, false));
                } else {
                    log.error("Skipping content spec {} as their are no translations available", contentSpecId);
                }
            }
        } else {
            // This will happen when a spec isn't frozen
            log.info("Getting the old Zanata document ids for content spec {}", pushedContentSpec.getZanataId());
            oldZanataIds.addAll(Utilities.getZanataIds(providerFactory, pushedContentSpec, false));
        }

        // 2. Break the Zanata Ids up based on their id
        final Map<String, Set<String>> oldZanataIdMap = new HashMap<String, Set<String>>();
        for (final String oldZanataId : oldZanataIds) {
            final String[] zanataNameSplit = oldZanataId.split("-");
            final String id = oldZanataId.startsWith("CS") ? "CS" : zanataNameSplit[0];

            if (!oldZanataIdMap.containsKey(id)) {
                // Sort the ids, so the newest is the last element
                oldZanataIdMap.put(id, new TreeSet<String>(new ZanataIdSort()));
            }
            oldZanataIdMap.get(id).add(oldZanataId);
        }

        // 3. Map the old zanata ids to the new zanata ids
        for (final String newZanataId : contentSpecZanataIds) {
            final String[] zanataNameSplit = newZanataId.split("-");
            final String id = newZanataId.startsWith("CS") ? "CS" : zanataNameSplit[0];

            if (oldZanataIdMap.containsKey(id)) {
                final LinkedList<String> oldZanataIdsForId = new LinkedList<String>(oldZanataIdMap.get(id));
                if (id.equals("CS")) {
                    // For content specs copy from all sources, unless it is itself
                    for (final String oldZanataId : oldZanataIdsForId) {
                        if (!newZanataId.equals(oldZanataId)) {
                            retValue.put(oldZanataId, newZanataId);
                        } else {
                            log.warn("Skipping {} as there is no previous translations", newZanataId);
                        }
                    }
                } else {
                    // The topic might not have changed so check that the zanata ids are different
                    if (!newZanataId.equals(oldZanataIdsForId.getLast())) {
                        retValue.put(oldZanataIdsForId.getLast(), newZanataId);
                    }
                }
            } else {
                log.warn("Skipping {} as there is no previous translations", newZanataId);
            }
        }

        return retValue;
    }

    /**
     * Copy the translations from an old source into a new source for all the locales.
     *
     * @param mappedZanataIds The mapping of old zanata document ids to their new document ids.
     * @param localeIds       The locales to copy translations for.
     */
    protected void copyTranslations(final Map<String, String> mappedZanataIds, final List<LocaleId> localeIds) {
        for (final Map.Entry<String, String> entry : mappedZanataIds.entrySet()) {
            final String oldZanataId = entry.getKey();
            final String newZanataId = entry.getValue();
            log.info("Copying translations for {} from {}", new Object[]{newZanataId, oldZanataId});

            for (final LocaleId locale : localeIds) {
                copyTranslation(oldZanataId, newZanataId, locale);
            }

            log.info("Successfully copied translations for {} from {}", new Object[]{newZanataId, oldZanataId});
        }
    }

    /**
     * Copy a specific translation for a document from it's old file into the new file.
     *
     * @param oldZanataId The old Zanata Document Id.
     * @param newZanataId The new Zanata Document Id.
     * @param locale
     */
    protected void copyTranslation(final String oldZanataId, final String newZanataId, final LocaleId locale) {
        log.info("\tCopying translations for locale {}", locale.toString());
        // Get the old version
        final TranslationsResource translatedResource = zanataInterface.getTranslations(oldZanataId, locale);

        if (translatedResource != null) {
            // Push the old version to the new version
            if (!pushTranslation(newZanataId, locale, translatedResource)) {
                log.error("\tFailed to copy translations for locale {}", locale.toString());
            }
        } else {
            log.warn("\tSkipping locale {} as there were no previous translations", locale.toString());
        }
    }

    /**
     * Push a translation resource using the Zanata REST API.
     *
     * @param id       The Zanata Document Id to push to.
     * @param locale   The locale to push the translation for.
     * @param resource The {@link TranslationsResource} object that contains the translations for the source strings.
     * @return True if the translations were pushed successfully, otherwise false.
     */
    protected boolean pushTranslation(final String id, final LocaleId locale, final TranslationsResource resource) {
        ClientResponse<String> response = null;
        try {
            final ITranslatedDocResource client = zanataInterface.getProxyFactory().getTranslatedDocResource(ZANATA_PROJECT,
                    ZANATA_VERSION);
            response = client.putTranslations(id, locale, resource, null, overwrite ? "import" : "auto");

            final Response.Status status = Response.Status.fromStatusCode(response.getStatus());

            if (status == Response.Status.OK) {
                final String entity = response.getEntity();
                if (entity.trim().length() != 0) log.debug(entity);
                return true;
            } else {
                log.debug("REST call to putResource() did not complete successfully. HTTP response code was " + status.getStatusCode() +
                        ". Reason was " + status.getReasonPhrase());
            }
        } catch (final Exception ex) {
            log.debug("Failed to push the Zanata Translation", ex);
        } finally {
            /*
             * If you are using RESTEasy client framework, and returning a Response from your service method, you will
             * explicitly need to release the connection.
             */
            if (response != null) response.releaseConnection();

            // Sleep for a little so the servers aren't overloaded
            try {
                Thread.sleep((long) (zanataRESTCallInterval * 1000));
            } catch (Exception e) {

            }
        }

        return false;
    }

    @Override
    public int processVariableArity(String optionName, String[] options) {
        int i = 0;
        while (i < options.length && !options[i].startsWith("-")) {
            i++;
        }
        return i;
    }
}
