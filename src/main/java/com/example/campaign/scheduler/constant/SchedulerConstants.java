package com.example.campaign.scheduler.constant;

public final class SchedulerConstants {

    private SchedulerConstants() {}

    // Job Group Names
    public static final String GROUP_PRELOAD = "CAMPAIGN_PRELOAD";
    public static final String GROUP_FIRE    = "CAMPAIGN_FIRE";

    // JobDataMap Keys
    public static final String KEY_CAMPAIGN_ID = "campaignId";

    // Job Identity Prefix
    public static final String PRELOAD_JOB_PREFIX  = "preload-job-";
    public static final String FIRE_JOB_PREFIX     = "fire-job-";
    public static final String PRELOAD_TRIG_PREFIX = "preload-trigger-";
    public static final String FIRE_TRIG_PREFIX    = "fire-trigger-";

    // Redis Key Templates
    public static final String REDIS_CONTACTS_KEY = "campaign:%s:contacts";   // LIST
    public static final String REDIS_MESSAGE_KEY  = "campaign:%s:message";    // STRING (JSON)
    public static final String REDIS_STATUS_KEY   = "campaign:%s:status";     // STRING
    public static final String REDIS_STATS_KEY    = "campaign:%s:stats";      // HASH

    // Timing
    public static final long PRELOAD_OFFSET_MINUTES = 5L; // FireJob se 5 min pehle

}
