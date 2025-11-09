package com.example.eventgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "generator")
public class GeneratorProperties {
    private String ingestUrl = "http://localhost:8080/ingest/event";
    private int ratePerSecond = 1;
    private boolean seedDatabase = true;

    // DB
    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    public String getIngestUrl() {
        return ingestUrl;
    }

    public void setIngestUrl(String ingestUrl) {
        this.ingestUrl = ingestUrl;
    }

    public int getRatePerSecond() {
        return ratePerSecond;
    }

    public void setRatePerSecond(int ratePerSecond) {
        this.ratePerSecond = ratePerSecond;
    }

    public boolean isSeedDatabase() {
        return seedDatabase;
    }

    public void setSeedDatabase(boolean seedDatabase) {
        this.seedDatabase = seedDatabase;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }
}



