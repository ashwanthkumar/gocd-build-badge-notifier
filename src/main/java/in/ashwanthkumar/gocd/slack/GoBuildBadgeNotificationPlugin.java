package in.ashwanthkumar.gocd.slack;

import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.Unirest;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static java.util.Arrays.asList;

@Extension
public class GoBuildBadgeNotificationPlugin implements GoPlugin {
    private static Logger LOGGER = Logger.getLoggerFor(GoBuildBadgeNotificationPlugin.class);

    public static final String EXTENSION_TYPE = "notification";
    private static final List<String> goSupportedVersions = asList("1.0");

    public static final String REQUEST_NOTIFICATIONS_INTERESTED_IN = "notifications-interested-in";
    public static final String REQUEST_STAGE_STATUS = "stage-status";

    public static final String PLUGIN_SETTINGS_GET_CONFIGURATION = "go.plugin-settings.get-configuration";
    public static final String PLUGIN_SETTINGS_GET_VIEW = "go.plugin-settings.get-view";
    public static final String PLUGIN_SETTINGS_SERVER_BASE_URL = "server_base_url";
    public static final String PLUGIN_SETTINGS_VALIDATE_CONFIGURATION = "go.plugin-settings.validate-configuration";
    public static final String GET_PLUGIN_SETTINGS = "go.processor.plugin-settings.get";

    public static final String LABEL_BUILD_BADGE_SERVER_URL = "Build Badge Server URL";

    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;
    private GoApplicationAccessor goApplicationAccessor;

    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        this.goApplicationAccessor = goApplicationAccessor;
        LOGGER.info("Initialized " + getClass().getName());
    }

    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        String requestName = goPluginApiRequest.requestName();
        LOGGER.info("Handling Request - " + requestName);
        switch (requestName) {
            case PLUGIN_SETTINGS_GET_CONFIGURATION:
                return handleGetPluginSettingsConfiguration();
            case PLUGIN_SETTINGS_GET_VIEW:
                try {
                    return handleGetPluginSettingsView();
                } catch (IOException e) {
                    return renderJSON(500, String.format("Failed to find template: %s", e.getMessage()));
                }
            case PLUGIN_SETTINGS_VALIDATE_CONFIGURATION:
                return handleValidatePluginSettingsConfiguration(goPluginApiRequest);
            case REQUEST_NOTIFICATIONS_INTERESTED_IN:
                return handleNotificationsInterestedIn();
            case REQUEST_STAGE_STATUS:
                return handleStageNotification(goPluginApiRequest);
        }
        return null;
    }

    private GoPluginApiResponse handleValidatePluginSettingsConfiguration(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> responseMap = (Map<String, Object>) fromJSON(goPluginApiRequest.requestBody());
        final Map<String, String> configuration = keyValuePairs(responseMap, "plugin-settings");
        List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();

        validate(response, new FieldValidator() {
            @Override
            public void validate(Map<String, Object> fieldValidation) {
                validateRequiredField(configuration, fieldValidation, PLUGIN_SETTINGS_SERVER_BASE_URL, LABEL_BUILD_BADGE_SERVER_URL);
            }
        });

        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleGetPluginSettingsView() throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/plugin-settings.template.html"), "UTF-8"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }


    private void validate(List<Map<String, Object>> response, FieldValidator fieldValidator) {
        Map<String, Object> fieldValidation = new HashMap<String, Object>();
        fieldValidator.validate(fieldValidation);
        if (!fieldValidation.isEmpty()) {
            response.add(fieldValidation);
        }
    }

    private void validateRequiredField(Map<String, String> configuration, Map<String, Object> fieldMap, String key, String name) {
        if (configuration.get(key) == null || configuration.get(key).isEmpty()) {
            fieldMap.put("key", key);
            fieldMap.put("message", String.format("'%s' is a required field", name));
        }
    }

    private GoPluginApiResponse handleGetPluginSettingsConfiguration() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put(PLUGIN_SETTINGS_SERVER_BASE_URL, createField(LABEL_BUILD_BADGE_SERVER_URL, null, true, false, "0"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private Map<String, Object> createField(String displayName, String defaultValue, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<String, Object>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    private Map<String, String> keyValuePairs(Map<String, Object> map, String mainKey) {
        Map<String, String> keyValuePairs = new HashMap<String, String>();
        Map<String, Object> fieldsMap = (Map<String, Object>) map.get(mainKey);
        for (String field : fieldsMap.keySet()) {
            Map<String, Object> fieldProperties = (Map<String, Object>) fieldsMap.get(field);
            String value = (String) fieldProperties.get("value");
            keyValuePairs.put(field, value);
        }
        return keyValuePairs;
    }

    public String getBadgeServerUrlFromSettings() {
        Map<String, Object> requestMap = new HashMap<String, Object>();
        requestMap.put("plugin-id", "build.badge");
        GoApiResponse response = goApplicationAccessor.submit(createGoApiRequest(GET_PLUGIN_SETTINGS, toJSON(requestMap)));
        if (response.responseBody() == null || response.responseBody().trim().isEmpty()) {
            throw new RuntimeException("plugin is not configured. please provide plugin settings.");
        }
        Map<String, String> responseBodyMap = (Map<String, String>) fromJSON(response.responseBody());
        return responseBodyMap.get(PLUGIN_SETTINGS_SERVER_BASE_URL);
    }

    private GoApiRequest createGoApiRequest(final String api, final String responseBody) {
        return new GoApiRequest() {
            @Override
            public String api() {
                return api;
            }

            @Override
            public String apiVersion() {
                return "1.0";
            }

            @Override
            public GoPluginIdentifier pluginIdentifier() {
                return getGoPluginIdentifier();
            }

            @Override
            public Map<String, String> requestParameters() {
                return null;
            }

            @Override
            public Map<String, String> requestHeaders() {
                return null;
            }

            @Override
            public String requestBody() {
                return responseBody;
            }
        };
    }


    public GoPluginIdentifier pluginIdentifier() {
        return getGoPluginIdentifier();
    }

    private GoPluginIdentifier getGoPluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_TYPE, goSupportedVersions);
    }

    private GoPluginApiResponse handleNotificationsInterestedIn() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("notifications", Arrays.asList(REQUEST_STAGE_STATUS));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleStageNotification(GoPluginApiRequest goPluginApiRequest) {
        GoNotificationMessage message = parseNotificationMessage(goPluginApiRequest);
        LOGGER.info(toJSON(message));
        int responseCode = SUCCESS_RESPONSE_CODE;
        String badgeServerUrl = getBadgeServerUrlFromSettings();

        Map<String, Object> response = new HashMap<String, Object>();
        List<String> messages = new ArrayList<String>();
        try {
            response.put("status", "success");
            LOGGER.info(message.fullyQualifiedJobName() + " has " + message.getStageState() + "/" + message.getStageResult());

            if(!message.getStageResult().equalsIgnoreCase("UNKNOWN")) {
                Map<String, String> payload = new HashMap<String, String>();
                payload.put("pipeline", message.getPipelineName());
                payload.put("status", message.getStageResult());
                String urlToPost = buildURL(badgeServerUrl);
                LOGGER.info("Doing payload POST to (" + urlToPost + ") with " + toJSON(payload));
                String notificationResponse = Unirest.post(urlToPost)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .body(toJSON(payload))
                        .asJson()
                        .getBody().toString();
                LOGGER.info("Response from Badge Server - " + notificationResponse);
            } else {
                LOGGER.info("Not sending UNKNOWN pipeline result to build badge");
            }
        } catch (Exception e) {
            LOGGER.error("Error handling status message", e);
            responseCode = INTERNAL_ERROR_RESPONSE_CODE;
            response.put("status", "failure");
            if (!isEmpty(e.getMessage())) {
                messages.add(e.getMessage());
            }
        }

        if (!messages.isEmpty()) {
            response.put("messages", messages);
        }
        return renderJSON(responseCode, response);
    }

    private String buildURL(String badgeServerUrl) {
        return URI.create(badgeServerUrl + "/status").normalize().toString();
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private GoNotificationMessage parseNotificationMessage(GoPluginApiRequest goPluginApiRequest) {
        return new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), GoNotificationMessage.class);
    }

    private Object fromJSON(String json) {
        return new GsonBuilder().create().fromJson(json, Object.class);
    }

    private String toJSON(Object object) {
        return new GsonBuilder().create().toJson(object);
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }
}
