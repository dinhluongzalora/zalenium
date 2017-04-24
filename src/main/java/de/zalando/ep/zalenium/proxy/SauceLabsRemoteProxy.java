package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.servlet.CloudProxyHtmlRenderer;
import de.zalando.ep.zalenium.util.TestInformation;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    Almost all concepts and ideas for this part of the implementation are taken from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */

public class SauceLabsRemoteProxy extends CloudTestingRemoteProxy {

    private static final String SAUCE_LABS_ACCOUNT_INFO = "https://%s:%s@saucelabs.com/rest/v1/users/%s";
    private static final String SAUCE_LABS_USER_NAME = getEnv().getStringEnvVariable("SAUCE_USERNAME", "");
    private static final String SAUCE_LABS_ACCESS_KEY = getEnv().getStringEnvVariable("SAUCE_ACCESS_KEY", "");
    private static final String SAUCE_LABS_URL = "http://ondemand.saucelabs.com:80";
    private static final Logger LOGGER = Logger.getLogger(SauceLabsRemoteProxy.class.getName());
    private final HtmlRenderer renderer = new CloudProxyHtmlRenderer(this);

    public SauceLabsRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateSLCapabilities(request, String.format(SAUCE_LABS_ACCOUNT_INFO, SAUCE_LABS_USER_NAME,
                SAUCE_LABS_ACCESS_KEY, SAUCE_LABS_USER_NAME)), registry);
    }

    @VisibleForTesting
    static RegistrationRequest updateSLCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement slAccountInfo = getCommonProxyUtilities().readJSONFromUrl(url);
        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String userPasswordSuppress = String.format("%s:%s@", SAUCE_LABS_USER_NAME, SAUCE_LABS_ACCESS_KEY);
            String logMessage = String.format("[SL] Capabilities fetched from %s", url.replace(userPasswordSuppress, ""));
            int sauceLabsAccountConcurrency;
            if (slAccountInfo == null) {
                logMessage = String.format("[SL] Account max. concurrency was NOT fetched from %s",
                        url.replace(userPasswordSuppress, ""));
                sauceLabsAccountConcurrency = 1;
            } else {
                sauceLabsAccountConcurrency = slAccountInfo.getAsJsonObject().getAsJsonObject("concurrency_limit").
                        get("overall").getAsInt();
            }
            LOGGER.log(Level.INFO, logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, sauceLabsAccountConcurrency);
        } catch (Exception e) {
            registrationRequest = addCapabilitiesToRegistrationRequest(registrationRequest, 1);
            LOGGER.log(Level.SEVERE, e.toString(), e);
            getGa().trackException(e);
        }
        return registrationRequest;
    }

    private static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest, int concurrency) {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, concurrency);
        desiredCapabilities.setBrowserName("SauceLabs");
        desiredCapabilities.setPlatform(Platform.ANY);
        registrationRequest.getConfiguration().capabilities.add(desiredCapabilities);
        return registrationRequest;
    }

    @Override
    public HtmlRenderer getHtmlRender() {
        return this.renderer;
    }

    @Override
    public String getUserNameProperty() {
        return "username";
    }

    @Override
    public String getUserNameValue() {
        return SAUCE_LABS_USER_NAME;
    }

    @Override
    public String getAccessKeyProperty() {
        return "accessKey";
    }

    @Override
    public String getAccessKeyValue() {
        return SAUCE_LABS_ACCESS_KEY;
    }

    @Override
    public String getCloudTestingServiceUrl() {
        return SAUCE_LABS_URL;
    }

    @Override
    public boolean proxySupportsLatestAsCapability() {
        return true;
    }

    @Override
    public boolean convertVideoFileToMP4() {
        return true;
    }


    @Override
    public TestInformation getTestInformation(String seleniumSessionId) {
        // https://SL_USER:SL_KEY@saucelabs.com/rest/v1/SL_USER/jobs/SELENIUM_SESSION_ID
        String sauceLabsTestUrl = "https://%s:%s@saucelabs.com/rest/v1/%s/jobs/%s";
        sauceLabsTestUrl = String.format(sauceLabsTestUrl, getUserNameValue(), getAccessKeyValue(), getUserNameValue(),
                seleniumSessionId);
        String sauceLabsVideoUrl = sauceLabsTestUrl + "/assets/video.flv";
        String sauceLabsBrowserLogUrl = sauceLabsTestUrl + "/assets/log.json";
        String sauceLabsSeleniumLogUrl = sauceLabsTestUrl + "/assets/selenium-server.log";
        JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(sauceLabsTestUrl).getAsJsonObject();
        String testName = testData.get("name").isJsonNull() ? null : testData.get("name").getAsString();
        String browser = testData.get("browser").getAsString();
        String browserVersion = testData.get("browser_short_version").getAsString();
        String platform = testData.get("os").getAsString();
        List<String> logUrls = new ArrayList<>();
        logUrls.add(sauceLabsBrowserLogUrl);
        logUrls.add(sauceLabsSeleniumLogUrl);
        return new TestInformation(seleniumSessionId, testName, getProxyName(), browser, browserVersion, platform, "",
                getVideoFileExtension(), sauceLabsVideoUrl, logUrls);
    }

    @Override
    public String getVideoFileExtension() {
        return ".flv";
    }

    @Override
    public String getProxyName() {
        return "SauceLabs";
    }

    @Override
    public String getProxyClassName() {
        return SauceLabsRemoteProxy.class.getName();
    }


}
