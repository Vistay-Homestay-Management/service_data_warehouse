package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dim_channel", schema = "app")
public class DimChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_key", nullable = false, updatable = false)
    private Short channelKey;

    @Column(name = "channel_code", nullable = false, unique = true, length = 40)
    private String channelCode;

    @Column(name = "channel_name", nullable = false, length = 100)
    private String channelName;

    @Column(name = "channel_group", nullable = false, length = 24)
    private String channelGroup;

    @Column(name = "ota", nullable = false)
    private boolean ota;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected DimChannel() {
    }

    public DimChannel(String channelCode, String channelName, String channelGroup, boolean ota) {
        this.channelCode = channelCode;
        this.channelName = channelName;
        this.channelGroup = channelGroup;
        this.ota = ota;
        this.active = true;
    }

    public Short getChannelKey() {
        return channelKey;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getChannelGroup() {
        return channelGroup;
    }

    public boolean isOta() {
        return ota;
    }

    public boolean isActive() {
        return active;
    }
}
