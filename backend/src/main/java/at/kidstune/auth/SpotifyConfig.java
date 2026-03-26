package at.kidstune.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotify")
public class SpotifyConfig {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String accountsBaseUrl = "https://accounts.spotify.com";
    private String apiBaseUrl = "https://api.spotify.com";

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getAccountsBaseUrl() { return accountsBaseUrl; }
    public void setAccountsBaseUrl(String accountsBaseUrl) { this.accountsBaseUrl = accountsBaseUrl; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
}
