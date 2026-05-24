package com.joshuaop.rankforge.rank;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable model for a single rank entry.
 * Loaded from ranks.yml; use RankModel.Builder to construct.
 */
public final class RankModel {

    private final String       id;
    private final String       displayName;
    private final String       nextRankId;
    private final int          slot;
    private final String       material;
    private final List<String> lore;
    private final double       requiredMoney;
    private final int          requiredXpLevel;
    private final String       requiredPermission;
    private final List<String> permissions;
    private final String       chatPrefix;
    private final List<String> commands;

    private RankModel(Builder b) {
        this.id                 = b.id;
        this.displayName        = b.displayName;
        this.nextRankId         = b.nextRankId;
        this.slot               = b.slot;
        this.material           = b.material;
        this.lore               = List.copyOf(b.lore);
        this.requiredMoney      = b.requiredMoney;
        this.requiredXpLevel    = b.requiredXpLevel;
        this.requiredPermission = b.requiredPermission;
        this.permissions        = List.copyOf(b.permissions);
        this.chatPrefix         = b.chatPrefix;
        this.commands           = List.copyOf(b.commands);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String       getId()                 { return id; }
    public String       getDisplayName()        { return displayName; }
    public String       getNextRankId()         { return nextRankId; }
    public int          getSlot()               { return slot; }
    public String       getMaterial()           { return material; }
    public List<String> getLore()               { return lore; }
    public double       getRequiredMoney()      { return requiredMoney; }
    public int          getRequiredXpLevel()    { return requiredXpLevel; }
    public String       getRequiredPermission() { return requiredPermission; }
    public List<String> getPermissions()        { return permissions; }
    public String       getChatPrefix()         { return chatPrefix; }
    public List<String> getCommands()           { return commands; }

    // ── Functional copies ─────────────────────────────────────────────────────

    public RankModel withSlot(int v)                  { return new Builder(this).slot(v).build(); }
    public RankModel withDisplayName(String v)        { return new Builder(this).displayName(v).build(); }
    public RankModel withNextRankId(String v)         { return new Builder(this).nextRankId(v).build(); }
    public RankModel withMaterial(String v)           { return new Builder(this).material(v).build(); }
    public RankModel withRequiredMoney(double v)      { return new Builder(this).requiredMoney(v).build(); }
    public RankModel withRequiredXpLevel(int v)       { return new Builder(this).requiredXpLevel(v).build(); }
    public RankModel withRequiredPermission(String v) { return new Builder(this).requiredPermission(v).build(); }
    public RankModel withChatPrefix(String v)         { return new Builder(this).chatPrefix(v).build(); }
    public RankModel withLore(List<String> v)         { return new Builder(this).lore(v).build(); }

    @Override
    public String toString() {
        return "RankModel{id='" + id + "', slot=" + slot + "}";
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String       id                 = "";
        private String       displayName        = "";
        private String       nextRankId         = "";
        private int          slot               = 11;
        private String       material           = "GRAY_WOOL";
        private List<String> lore               = new ArrayList<>();
        private double       requiredMoney      = 0;
        private int          requiredXpLevel    = 0;
        private String       requiredPermission = "";
        private List<String> permissions        = new ArrayList<>();
        private String       chatPrefix         = "";
        private List<String> commands           = new ArrayList<>();

        public Builder(String id) { this.id = id; }

        public Builder(RankModel src) {
            this.id                 = src.id;
            this.displayName        = src.displayName;
            this.nextRankId         = src.nextRankId;
            this.slot               = src.slot;
            this.material           = src.material;
            this.lore               = new ArrayList<>(src.lore);
            this.requiredMoney      = src.requiredMoney;
            this.requiredXpLevel    = src.requiredXpLevel;
            this.requiredPermission = src.requiredPermission;
            this.permissions        = new ArrayList<>(src.permissions);
            this.chatPrefix         = src.chatPrefix;
            this.commands           = new ArrayList<>(src.commands);
        }

        public Builder displayName(String v)        { this.displayName = v;                      return this; }
        public Builder nextRankId(String v)         { this.nextRankId = v;                       return this; }
        public Builder slot(int v)                  { this.slot = v;                             return this; }
        public Builder material(String v)           { this.material = v;                         return this; }
        public Builder lore(List<String> v)         { this.lore = new ArrayList<>(v);            return this; }
        public Builder requiredMoney(double v)      { this.requiredMoney = v;                    return this; }
        public Builder requiredXpLevel(int v)       { this.requiredXpLevel = v;                  return this; }
        public Builder requiredPermission(String v) { this.requiredPermission = v;               return this; }
        public Builder permissions(List<String> v)  { this.permissions = new ArrayList<>(v);     return this; }
        public Builder chatPrefix(String v)         { this.chatPrefix = v;                       return this; }
        public Builder commands(List<String> v)     { this.commands = new ArrayList<>(v);        return this; }

        public RankModel build() { return new RankModel(this); }
    }
}
