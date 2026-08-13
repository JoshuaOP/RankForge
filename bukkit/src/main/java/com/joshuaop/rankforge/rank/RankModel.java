package com.joshuaop.rankforge.rank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable model for a single rank entry.
 * Loaded from ranks.yml; use RankModel.Builder to construct.
 */
public final class RankModel {

    private final String              id;
    private final String              displayName;
    private final String              nextRankId;
    private final int                 slot;
    private final String              material;
    private final List<String>        lore;
    private final String              chatPrefix;
    private final List<String>        permissions;
    private final List<String>        commands;

    // ── Built-in Requirements ─────────────────────────────────────────────────
    private final double              requiredMoney;
    private final int                 requiredXpLevel;
    private final String              requiredPermission;
    private final long                requiredPlayTime;
    private final int                 requiredMobKills;
    private final int                 requiredBlockBreaks;
    private final String              requiredStatisticId;
    private final int                 requiredStatisticValue;
    private final List<String>        requiredQuests;
    private final List<String>        requiredWorlds;
    private final Map<String, Integer> requiredItems;

    private RankModel(Builder b) {
        this.id                      = b.id != null ? b.id : "";
        this.displayName             = b.displayName != null ? b.displayName : "";
        this.nextRankId              = b.nextRankId != null ? b.nextRankId : "";
        this.slot                    = b.slot;
        this.material                = b.material != null ? b.material : "GRAY_WOOL";
        this.lore                    = b.lore != null ? List.copyOf(b.lore) : List.of();
        this.chatPrefix              = b.chatPrefix != null ? b.chatPrefix : "";
        this.permissions             = b.permissions != null ? List.copyOf(b.permissions) : List.of();
        this.commands                = b.commands != null ? List.copyOf(b.commands) : List.of();
        this.requiredMoney           = b.requiredMoney;
        this.requiredXpLevel         = b.requiredXpLevel;
        this.requiredPermission      = b.requiredPermission != null ? b.requiredPermission : "";
        this.requiredPlayTime = b.requiredPlayTime;
        this.requiredMobKills        = b.requiredMobKills;
        this.requiredBlockBreaks     = b.requiredBlockBreaks;
        this.requiredStatisticId     = b.requiredStatisticId != null ? b.requiredStatisticId : "";
        this.requiredStatisticValue  = b.requiredStatisticValue;
        this.requiredQuests          = b.requiredQuests != null ? List.copyOf(b.requiredQuests) : List.of();
        this.requiredWorlds          = b.requiredWorlds != null ? List.copyOf(b.requiredWorlds) : List.of();
        this.requiredItems           = b.requiredItems != null ? Map.copyOf(b.requiredItems) : Map.of();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String              getId()                       { return id; }
    public String              getDisplayName()              { return displayName; }
    public String              getNextRankId()               { return nextRankId; }
    public int                 getSlot()                     { return slot; }
    public String              getMaterial()                 { return material; }
    public List<String>        getLore()                     { return lore; }
    public String              getChatPrefix()               { return chatPrefix; }
    public List<String>        getPermissions()              { return permissions; }
    public List<String>        getCommands()                 { return commands; }
    public double              getRequiredMoney()            { return requiredMoney; }
    public int                 getRequiredXpLevel()          { return requiredXpLevel; }
    public String              getRequiredPermission()       { return requiredPermission; }
    public long                getRequiredPlayTime()          { return requiredPlayTime; }
    public int                 getRequiredMobKills()         { return requiredMobKills; }
    public int                 getRequiredBlockBreaks()      { return requiredBlockBreaks; }
    public String              getRequiredStatisticId()      { return requiredStatisticId; }
    public int                 getRequiredStatisticValue()   { return requiredStatisticValue; }
    public List<String>        getRequiredQuests()           { return requiredQuests; }
    public List<String>        getRequiredWorlds()           { return requiredWorlds; }
    public Map<String, Integer> getRequiredItems()           { return requiredItems; }

    // ── Functional copies ─────────────────────────────────────────────────────

    public RankModel withSlot(int v)                             { return new Builder(this).slot(v).build(); }
    public RankModel withDisplayName(String v)                   { return new Builder(this).displayName(v).build(); }
    public RankModel withNextRankId(String v)                    { return new Builder(this).nextRankId(v).build(); }
    public RankModel withMaterial(String v)                      { return new Builder(this).material(v).build(); }
    public RankModel withRequiredMoney(double v)                 { return new Builder(this).requiredMoney(v).build(); }
    public RankModel withRequiredXpLevel(int v)                  { return new Builder(this).requiredXpLevel(v).build(); }
    public RankModel withRequiredPermission(String v)            { return new Builder(this).requiredPermission(v).build(); }
    public RankModel withRequiredPlayTime(long v)                { return new Builder(this).requiredPlayTime(v).build(); }
    public RankModel withRequiredMobKills(int v)                 { return new Builder(this).requiredMobKills(v).build(); }
    public RankModel withRequiredBlockBreaks(int v)              { return new Builder(this).requiredBlockBreaks(v).build(); }
    public RankModel withRequiredStatistic(String id, int value) { return new Builder(this).requiredStatisticId(id).requiredStatisticValue(value).build(); }
    public RankModel withRequiredQuests(List<String> v)          { return new Builder(this).requiredQuests(v).build(); }
    public RankModel withRequiredWorlds(List<String> v)          { return new Builder(this).requiredWorlds(v).build(); }
    public RankModel withRequiredItems(Map<String, Integer> v)   { return new Builder(this).requiredItems(v).build(); }
    public RankModel withChatPrefix(String v)                    { return new Builder(this).chatPrefix(v).build(); }
    public RankModel withLore(List<String> v)                    { return new Builder(this).lore(v).build(); }

    @Override
    public String toString() {
        return "RankModel{id='" + id + "', slot=" + slot + "}";
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String              id                      = "";
        private String              displayName             = "";
        private String              nextRankId              = "";
        private int                 slot                    = 11;
        private String              material                = "GRAY_WOOL";
        private List<String>        lore                    = new ArrayList<>();
        private String              chatPrefix              = "";
        private List<String>        permissions             = new ArrayList<>();
        private List<String>        commands                = new ArrayList<>();
        private double              requiredMoney           = 0;
        private int                 requiredXpLevel         = 0;
        private String              requiredPermission      = "";
        private long                requiredPlayTime = 0;
        private int                 requiredMobKills        = 0;
        private int                 requiredBlockBreaks     = 0;
        private String              requiredStatisticId     = "";
        private int                 requiredStatisticValue  = 0;
        private List<String>        requiredQuests          = new ArrayList<>();
        private List<String>        requiredWorlds          = new ArrayList<>();
        private Map<String, Integer> requiredItems          = new LinkedHashMap<>();

        public Builder(String id) { 
            this.id = id != null ? id : ""; 
        }

        public Builder(RankModel src) {
            if (src != null) {
                this.id                      = src.id;
                this.displayName             = src.displayName;
                this.nextRankId              = src.nextRankId;
                this.slot                    = src.slot;
                this.material                = src.material;
                this.lore                    = new ArrayList<>(src.lore);
                this.chatPrefix              = src.chatPrefix;
                this.permissions             = new ArrayList<>(src.permissions);
                this.commands                = new ArrayList<>(src.commands);
                this.requiredMoney           = src.requiredMoney;
                this.requiredXpLevel         = src.requiredXpLevel;
                this.requiredPermission      = src.requiredPermission;
                this.requiredPlayTime = src.requiredPlayTime;
                this.requiredMobKills        = src.requiredMobKills;
                this.requiredBlockBreaks     = src.requiredBlockBreaks;
                this.requiredStatisticId     = src.requiredStatisticId;
                this.requiredStatisticValue  = src.requiredStatisticValue;
                this.requiredQuests          = new ArrayList<>(src.requiredQuests);
                this.requiredWorlds          = new ArrayList<>(src.requiredWorlds);
                this.requiredItems           = new LinkedHashMap<>(src.requiredItems);
            }
        }

        public Builder displayName(String v)                  { this.displayName = v != null ? v : "";             return this; }
        public Builder nextRankId(String v)                   { this.nextRankId = v != null ? v : "";              return this; }
        public Builder slot(int v)                            { this.slot = v;                                     return this; }
        public Builder material(String v)                     { this.material = v != null ? v : "GRAY_WOOL";       return this; }
        public Builder lore(List<String> v)                   { this.lore = v != null ? new ArrayList<>(v) : new ArrayList<>(); return this; }
        public Builder chatPrefix(String v)                   { this.chatPrefix = v != null ? v : "";              return this; }
        public Builder permissions(List<String> v)            { this.permissions = v != null ? new ArrayList<>(v) : new ArrayList<>(); return this; }
        public Builder commands(List<String> v)               { this.commands = v != null ? new ArrayList<>(v) : new ArrayList<>(); return this; }
        public Builder requiredMoney(double v)                { this.requiredMoney = v;                            return this; }
        public Builder requiredXpLevel(int v)                 { this.requiredXpLevel = v;                         return this; }
        public Builder requiredPermission(String v)           { this.requiredPermission = v != null ? v : "";      return this; }
        public Builder requiredPlayTime(long v)               { this.requiredPlayTime = v;                       return this; }
        public Builder requiredMobKills(int v)                { this.requiredMobKills = v;                        return this; }
        public Builder requiredBlockBreaks(int v)             { this.requiredBlockBreaks = v;                     return this; }
        public Builder requiredStatisticId(String v)          { this.requiredStatisticId = v != null ? v : "";     return this; }
        public Builder requiredStatisticValue(int v)          { this.requiredStatisticValue = v;                  return this; }
        public Builder requiredQuests(List<String> v)         { this.requiredQuests = v != null ? new ArrayList<>(v) : new ArrayList<>(); return this; }
        public Builder requiredWorlds(List<String> v)         { this.requiredWorlds = v != null ? new ArrayList<>(v) : new ArrayList<>(); return this; }
        public Builder requiredItems(Map<String, Integer> v)  { this.requiredItems = v != null ? new LinkedHashMap<>(v) : new LinkedHashMap<>(); return this; }

        public RankModel build() { return new RankModel(this); }
    }
}
