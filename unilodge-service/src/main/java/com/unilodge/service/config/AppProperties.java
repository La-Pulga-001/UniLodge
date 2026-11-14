package com.unilodge.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Strongly typed application settings bound from application.yml. */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Mail mail = new Mail();
    private final Upload upload = new Upload();
    private final Bootstrap bootstrap = new Bootstrap();
    private final Geocoding geocoding = new Geocoding();
    private final Cors cors = new Cors();

    public Jwt getJwt() {
        return jwt;
    }

    public Mail getMail() {
        return mail;
    }

    public Upload getUpload() {
        return upload;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public Geocoding getGeocoding() {
        return geocoding;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Jwt {
        /** Raw secret; a 256-bit key is derived from it. */
        private String secret = "dev-only-change-me-unilodge-secret-key-0123456789";
        private long ttlHours = 168;
        private String issuer = "unilodge";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTtlHours() {
            return ttlHours;
        }

        public void setTtlHours(long ttlHours) {
            this.ttlHours = ttlHours;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Mail {
        /** "console" logs messages locally; "smtp" uses the configured mail server. */
        private String mode = "console";
        private String from = "no-reply@unilodge.local";
        private String appUrl = "http://localhost:8080";
        /** Development only: echo OTP codes back in API responses. */
        private boolean exposeOtp = false;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getAppUrl() {
            return appUrl;
        }

        public void setAppUrl(String appUrl) {
            this.appUrl = appUrl;
        }

        public boolean isExposeOtp() {
            return exposeOtp;
        }

        public void setExposeOtp(boolean exposeOtp) {
            this.exposeOtp = exposeOtp;
        }

        public boolean isConsoleMode() {
            return !"smtp".equalsIgnoreCase(mode);
        }
    }

    public static class Upload {
        private String dir = "./uploads";
        private long maxImageBytes = 5L * 1024 * 1024;
        private long maxAvatarBytes = 2L * 1024 * 1024;
        private int maxImagesPerProperty = 10;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public long getMaxImageBytes() {
            return maxImageBytes;
        }

        public void setMaxImageBytes(long maxImageBytes) {
            this.maxImageBytes = maxImageBytes;
        }

        public long getMaxAvatarBytes() {
            return maxAvatarBytes;
        }

        public void setMaxAvatarBytes(long maxAvatarBytes) {
            this.maxAvatarBytes = maxAvatarBytes;
        }

        public int getMaxImagesPerProperty() {
            return maxImagesPerProperty;
        }

        public void setMaxImagesPerProperty(int maxImagesPerProperty) {
            this.maxImagesPerProperty = maxImagesPerProperty;
        }
    }

    public static class Bootstrap {
        /** Original administrators seeded on first boot, comma separated. */
        private List<String> adminEmails = new ArrayList<>(List.of(
                "fblesselikem@gmail.com",
                "ayensusamuel866@gmail.com",
                "deiyeshua05@gmail.com"
        ));
        /** Seed demo accounts, listings, posts and comments. */
        private boolean demoData = false;

        public List<String> getAdminEmails() {
            return adminEmails;
        }

        public void setAdminEmails(List<String> adminEmails) {
            this.adminEmails = adminEmails;
        }

        public boolean isDemoData() {
            return demoData;
        }

        public void setDemoData(boolean demoData) {
            this.demoData = demoData;
        }
    }

    public static class Geocoding {
        private boolean enabled = true;
        private String baseUrl = "https://nominatim.openstreetmap.org";
        private String userAgent = "UniLodge/1.0 (student housing platform; contact: support@unilodge.local)";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
