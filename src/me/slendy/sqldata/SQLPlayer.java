package me.slendy.sqldata;

import java.util.UUID;

/**
 * ************************************************************************
 * Copyright Slendy (c) 2017. All Right Reserved.
 * Any code contained within this document, and any associated APIs with similar branding
 * are the sole property of Slendy. Distribution, reproduction, taking snippets, or
 * claiming any contents as your own will break the terms of the license, and void any
 * agreements with you, the third party.
 * Thanks
 * ************************************************************************
 */
public class SQLPlayer {

    private UUID _uuid;

    private long _loginTime;

    private long _previousOnTime;

    private int _kills;

    private int _deaths;

    UUID getUuid() {
        return _uuid;
    }

    long getLoginTime() {
        return _loginTime;
    }

    long getPreviousOnTime() {
        return _previousOnTime;
    }

    public void setPreviousOnTime(long previousOnTime) {
        _previousOnTime = previousOnTime;
    }

    int getKills() {
        return _kills;
    }

    void incrementKills() {
        _kills+=1;
    }

    int getDeaths() {
        return _deaths;
    }

    void incrementDeaths() {
        _deaths += 1;
    }

    SQLPlayer(UUID p, long loginTime, long previousOnTime, int kills, int deaths){

        _uuid = p;
        _loginTime = loginTime;
        _previousOnTime = previousOnTime;
        _kills = kills;
        _deaths = deaths;
    }



}


