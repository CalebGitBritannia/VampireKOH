package com.clanjhoo.vampire.entity;

import co.aikar.commands.MessageType;
import com.clanjhoo.dbhandler.annotations.DataField;
import com.clanjhoo.dbhandler.annotations.Entity;
import com.clanjhoo.dbhandler.annotations.NotNullField;
import com.clanjhoo.dbhandler.annotations.PrimaryKey;
import com.clanjhoo.vampire.InfectionReason;
import com.clanjhoo.vampire.compat.WorldGuardCompat;
import com.clanjhoo.vampire.keyproviders.*;
import com.clanjhoo.vampire.VampireRevamp;
import com.clanjhoo.vampire.config.PluginConfig;
import com.clanjhoo.vampire.config.RadiationEffectConfig;
import com.clanjhoo.vampire.config.StateEffectConfig;
import com.clanjhoo.vampire.event.InfectionChangeEvent;
import com.clanjhoo.vampire.event.VampireTypeChangeEvent;
import com.clanjhoo.vampire.util.*;
import me.libraryaddict.disguise.DisguiseAPI;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;

@Entity(table = "vampire_data")
public class VPlayer {
    // -------------------------------------------- //
    // PERSISTENT FIELDS
    // -------------------------------------------- //

    /**
     * PERSISTENT: UniqueID of this player
     */
    @PrimaryKey
    private UUID uuid;
    /**
     * PERSISTENT: whether or not the player is a vampire
     */
    @NotNullField
    @DataField(sqltype = "BOOL")
    private boolean vampire = false;
    /**
     * PERSISTENT: whether or not the player is a nosferatu (higher tier)
     */
    @NotNullField
    @DataField(sqltype = "BOOL")
    private boolean nosferatu = false;
    /**
     * PERSISTENT: degree of infection [0, 1]. 0 means no infection, 1 turns the player into a vampire
     */
    @NotNullField
    private double infection = 0;
    /**
     * PERSISTENT: how was this player infected
     */
    @DataField(sqltype = "VARCHAR(16)")
    private String reason;
    /**
     * PERSISTENT: UUID of the player who infected this player
     */
    private UUID makerUUID;
    /**
     * PERSISTENT: whether or not the vampire is trying to infect others in combat on purpose or not
     */
    @NotNullField
    @DataField(sqltype = "BOOL")
    private boolean intending = false;
    /**
     * PERSISTENT: whether or not to apply night vision effects to a vampire
     */
    @DataField(sqltype = "BOOL")
    private boolean usingNightVision = false;

    /**
     * TRANSIENT: set of regions this vampire is allowed to enter (worldguard enabled)
     */
    private transient Set<String> allowedRegions = new ConcurrentSkipListSet<>();
    /**
     * TRANSIENT: whether or not the vampire is in bloodlust mode
     */
    private transient boolean bloodlusting = false;
    /**
     * TRANSIENT: Had enabled flight
     */
    private transient boolean hadFlight = false;
    /**
     * TRANSIENT: the irradiation for the player.
     */
    private transient double rad = 0;
    /**
     * TRANSIENT: the temperature of the player [0, 1]
     */
    private transient double temp = 0;
    /**
     * TRANSIENT: the food accumulator
     */
    private transient double foodRem = 0;
    /**
     * TRANSIENT: timestamp of the last time the player received damage
     */
    private transient long lastDamageMillis = 0;
    /**
     * TRANSIENT: timestamp of the last time the player used shriek
     */
    private transient long lastShriekMillis = 0;
    /**
     * TRANSIENT: milliseconds to wait before repeating shriek message
     */
    private transient long lastShriekWaitMessageMillis = 0;
    /**
     * TRANSIENT: milliseconds to wait before restoring the truce
     */
    private transient long truceBreakMillisLeft = 0;
    /**
     * TRANSIENT: the player who offered the blood in a trade
     */
    private transient VPlayer tradeOfferedFrom = null;
    /**
     * TRANSIENT: the amount of blood offered in a trade
     */
    private transient double tradeOfferedAmount = 0;
    /**
     * TRANSIENT: timestamp of the moment the trade started
     */
    private transient long tradeOfferedAtMillis = 0;
    /**
     * TRANSIENT FX: smoke
     */
    private transient long fxSmokeMillis = 0;
    /**
     * TRANSIENT FX: ender
     */
    private transient long fxEnderMillis = 0;

    @NotNullField
    @DataField(sqltype = "VARCHAR")
    private transient int testNumber;


    // -------------------------------------------- //
    // CONSTRUCTORS
    // -------------------------------------------- //

    public VPlayer() {
        this.vampire = false;
        this.nosferatu = false; // You can't be nosferatu without being vampire
        this.infection = 0;
        this.reason = null;
        this.makerUUID = null;
        this.intending = false;
        this.usingNightVision = false;
    }

    public void invite(String regionName) {
        allowedRegions.add(regionName);
    }

    public void expel(String regionName) {
        allowedRegions.remove(regionName);
    }

    // -------------------------------------------- //
    // Getters and setters
    // -------------------------------------------- //

    public boolean isVampire() {
        return this.vampire;
    }

    // Shortcut
    public boolean isHuman() {
        return !this.isVampire();
    }

    public void setVampire(boolean val) {
        if (this.vampire != val) {
            VampireTypeChangeEvent event = new VampireTypeChangeEvent(val, this);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.vampire = val;
                this.setNosferatu(false);
                if (!val) {
                    this.setMaker(null);
                    this.setReason(null);
                }
                this.setBloodlusting(false);
                this.setIntending(false);
                this.setUsingNightVision(false);
                this.setInfection(0);
                this.setTestNumber(14);

                Player player = Bukkit.getPlayer(uuid);
                if (VampireRevamp.getInstance().permissionGroupEnabled())
                    VampireRevamp.getInstance().setVampireGroup(player, val);
                if (player != null) {
                    PluginConfig conf = VampireRevamp.getVampireConfig();
                    if (this.vampire) {
                        VampireRevamp.sendMessage(player,
                                MessageType.INFO,
                                VampirismMessageKeys.TURNED_VAMPIRE);
                        this.runFxShriek();
                        this.runFxSmokeBurst();
                        this.runFxSmoke();

                        conf.potionEffects.human.removePotionEffects(player);
                        // player.setSleepingIgnored(true);
                    } else {
                        VampireRevamp.sendMessage(player,
                                MessageType.INFO,
                                VampirismMessageKeys.CURED_VAMPIRE);
                        this.runFxEnder();

                        // player.setSleepingIgnored(false);
                        conf.potionEffects.nosferatu.removePotionEffects(player);
                        conf.potionEffects.vampire.removePotionEffects(player);
                    }

                    this.update();
                }
            }
        }
        else {
            this.setInfection(0);
        }
    }

    public void setNosferatu(boolean val) {
        this.nosferatu = val;
    }

    public boolean isNosferatu() {
        return this.isVampire() && this.nosferatu;
    }

    public double getInfection() {
        return this.infection;
    }

    public boolean isInfected() {
        return this.infection > 0D;
    }

    public int getTestNumber() {
        return this.testNumber;
    }

    public void setTestNumber(int number) {
        this.testNumber = number;
    }

    public void setInfection(double val) {
        if (this.infection != val) {
            // Call event
            InfectionChangeEvent event = new InfectionChangeEvent(val, this);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                val = event.getInfection();

                if (val >= 1D) {
                    this.setVampire(true);
                } else if (val <= 0D) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        if (this.infection > 0D && !this.isVampire()) {
                            VampireRevamp.sendMessage(player,
                                    MessageType.INFO,
                                    InfectionMessageKeys.CURED);
                        }
                        this.infection = 0D;
                        VampireRevamp.getVampireConfig().potionEffects.infected.removePotionEffects(player);
                    }
                } else {
                    this.infection = val;
                }
                this.update();
            }
        }
    }

    public void addInfection(double val) {
        this.setInfection(this.getInfection() + val);
    }

    public void addInfection(double val, InfectionReason reason, VPlayer maker) {
        Player player = Bukkit.getPlayer(uuid);
        if (!vampire) {
            this.setReason(reason);
            this.setMakerUUID(maker == null ? null : maker.getUuid());

            String parent = null;
            if (reason.isMaker()) {
                parent = getMakerName();
            }
            if (parent == null || parent.isEmpty()) {
                parent = "someone";
            }

            // plugin.log(this.getReasonDesc(false));
            if (reason.isNoticeable())
                VampireRevamp.sendMessage(player,
                        MessageType.INFO,
                        reason.getDescKey(),
                        "{player}", VampireRevamp.getMessage(player, GrammarMessageKeys.YOU),
                        "{to_be_past}", VampireRevamp.getMessage(player, GrammarMessageKeys.TO_BE_2ND_PAST),
                        "{parent}", parent);
            this.addInfection(val);
        }
    }

    // healthy and unhealthy - Fake field shortcuts
    public boolean isHealthy() {
        return !this.isVampire() && !this.isInfected();
    }

    public boolean isUnhealthy() {
        return !this.isHealthy();
    }

    public InfectionReason getReason() {
        return InfectionReason.fromName(reason);
    }

    public void setReason(InfectionReason reason) {
        this.reason = reason == null ? null : reason.name();
    }

    public UUID getMakerUUID() {
        return this.makerUUID;
    }

    public void setMakerUUID(UUID makerUUID) {
        this.makerUUID = makerUUID;
    }

    public String getMakerName() {
        return Bukkit.getOfflinePlayer(this.makerUUID).getName();
    }

    public void setMaker(VPlayer val) {
        this.setMakerUUID(val == null ? null : val.getUuid());
    }

    public boolean isIntending() {
        return this.intending;
    }

    public void setIntending(boolean val) {
        this.intending = val;
        Player p = Bukkit.getPlayer(uuid);

        if (p == null) {
            VampireRevamp.log(Level.WARNING, "An offline player is trying to infect on intend!");
            return;
        }
        if (!VampireRevamp.getVampireConfig().vampire.intend.enabled) {
            String intendAction = VampireRevamp.getMessage(p, GrammarMessageKeys.INTEND);
            VampireRevamp.sendMessage(p,
                    MessageType.ERROR,
                    CommandMessageKeys.DISABLED_ACTION,
                    "{action}", intendAction);
            return;
        }

        String on = VampireRevamp.getMessage(p, GrammarMessageKeys.ON);
        String off = VampireRevamp.getMessage(p, GrammarMessageKeys.OFF);

        VampireRevamp.sendMessage(p,
                MessageType.INFO,
                CommandMessageKeys.SHOW_INTENT,
                "{enabled}", isIntending() ? on : off,
                "{percent}", String.format("%.1f", combatInfectRisk() * 100));
    }

    public boolean isBloodlusting() {
        return this.bloodlusting;
    }

    public void setBloodlusting(boolean val) {
        VampireRevamp.debugLog(Level.INFO, "Changing bloodlust");
        Player me = Bukkit.getPlayer(uuid);
        if (me == null) {
            VampireRevamp.log(Level.WARNING, "An offline player is trying to bloodlust!");
            return;
        }
        PluginConfig conf = VampireRevamp.getVampireConfig();

        String bloodlustAction = VampireRevamp.getMessage(me, GrammarMessageKeys.BLOODLUST);
        bloodlustAction = bloodlustAction.substring(0, 1).toUpperCase() + bloodlustAction.substring(1);

        if (this.bloodlusting == val) {
            // No real change - just view the info.
            VampireRevamp.debugLog(Level.INFO, "This is not a change!");
            String on = VampireRevamp.getMessage(me, GrammarMessageKeys.ON);
            String off = VampireRevamp.getMessage(me, GrammarMessageKeys.OFF);
            VampireRevamp.sendMessage(me,
                    MessageType.INFO,
                    GrammarMessageKeys.X_IS_Y,
                    "{key}", bloodlustAction,
                    "{value}", val ? on : off);
            return;
        }

        if (val) { // Enabling bloodlust
            // There are a few rules to when you can turn it on:
            VampireRevamp.debugLog(Level.INFO, "Za warudo has changed!");
            if (!this.isVampire()) {
                VampireRevamp.debugLog(Level.INFO, "Non non non again!");
                String vampireType = VampireRevamp.getMessage(me, GrammarMessageKeys.VAMPIRE_TYPE);
                VampireRevamp.sendMessage(me,
                        MessageType.ERROR,
                        GrammarMessageKeys.ONLY_TYPE_CAN_ACTION,
                        "{vampire_type}", vampireType,
                        "{action}", bloodlustAction);
            } else if (this.getFood() != null && this.getFood() < conf.vampire.bloodlust.minFood) {
                VampireRevamp.debugLog(Level.INFO, "Too hungry!");
                VampireRevamp.sendMessage(me,
                        MessageType.ERROR,
                        SkillMessageKeys.BLOODLUST_LOW_FOOD);
            } else if (conf.vampire.bloodlust.checkGamemode &&
                            (me.getGameMode() == GameMode.CREATIVE ||
                             me.getGameMode() == GameMode.SPECTATOR)) { // or offline :P but offline players wont see the message
                VampireRevamp.debugLog(Level.INFO, "Le bad gamemode!");
                VampireRevamp.sendMessage(me,
                        MessageType.ERROR,
                        SkillMessageKeys.BLOODLUST_GAMEMODE_CHECK);
            } else if (!conf.vampire.bloodlust.enabled) {
                VampireRevamp.debugLog(Level.INFO, "Bloodlust config disabled!");
                VampireRevamp.sendMessage(me,
                        MessageType.ERROR,
                        CommandMessageKeys.DISABLED_ACTION,
                        "{action}", bloodlustAction);
            } else {
                this.bloodlusting = true;
                VampireRevamp.debugLog(Level.INFO, "enabling bloodlust");
                this.update();
                VampireRevamp.debugLog(Level.INFO, "updated!");
                String on = VampireRevamp.getMessage(me, GrammarMessageKeys.ON);
                VampireRevamp.sendMessage(me,
                        MessageType.INFO,
                        CommandMessageKeys.SHOW_BLOODLUST,
                        "{bloodlust}", bloodlustAction,
                        "{enabled}", on,
                        "{percent}", String.format("%.1f", this.combatDamageFactor() * 100));
                VampireRevamp.debugLog(Level.INFO, "sent message to " + me);
            }
        }
        else { // Disabling bloodlust
            this.bloodlusting = false;
            conf.potionEffects.bloodlust.removePotionEffects(me);
            this.update();
            String off = VampireRevamp.getMessage(me, GrammarMessageKeys.OFF);
            VampireRevamp.sendMessage(me,
                    MessageType.INFO,
                    CommandMessageKeys.SHOW_BLOODLUST,
                    "{bloodlust}", bloodlustAction,
                    "{enabled}", off,
                    "{percent}", String.format("%.1f", this.combatDamageFactor() * 100));
        }
    }

    public boolean isUsingNightVision() {
        return this.usingNightVision;
    }

    public void setUsingNightVision(boolean val) {
        Player me = Bukkit.getPlayer(uuid);
        if (me == null) {
            VampireRevamp.log(Level.WARNING, "An offline player is trying to use nightvision!");
            return;
        }

        // If an actual change is being made ...
        if (this.usingNightVision != val) {
            PluginConfig conf = VampireRevamp.getVampireConfig();
            // ... do change stuff ...
            if (conf.vampire.nightvision.enabled) {
                this.usingNightVision = val;

                // ... remove the nightvision potion effects ...
                conf.potionEffects.nightvision.removePotionEffects(me);

                // ... trigger a potion effect update ...
                this.update();

                String onString = val ?
                        VampireRevamp.getMessage(me, GrammarMessageKeys.ON) :
                        VampireRevamp.getMessage(me, GrammarMessageKeys.OFF);

                VampireRevamp.sendMessage(me,
                        MessageType.INFO,
                        CommandMessageKeys.SHOW_NIGHTVISION,
                        "{enabled}", onString);
            }
            else {
                String nightvisionAction = VampireRevamp.getMessage(me, GrammarMessageKeys.NIGHTVISION);
                VampireRevamp.sendMessage(me,
                        MessageType.ERROR,
                        CommandMessageKeys.DISABLED_ACTION,
                        "{action}", nightvisionAction);
            }
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public double getRad() {
        return this.rad;
    }

    public void setRad(double rad) {
        this.rad = rad;
    }

    public double getTemp() {
        return this.temp;
    }

    public void setTemp(double val) {
        this.temp = MathUtil.limitNumber(val, 0D, 1D);
    }

    public void addTemp(double val) {
        this.setTemp(this.getTemp() + val);
    }

    public Double getFood() {
        Double food = null;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            food = this.foodRem + player.getFoodLevel();
        }
        return food;
    }

    public int addFood(double amount) {
        Integer added = null;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            this.foodRem += amount;
            int diff = (int) this.foodRem;
            this.foodRem -= diff;
            int current = player.getFoodLevel();
            int next = current + diff;
            player.setFoodLevel(next);
            added = next - current;
        }
        return added;
    }

    public long getLastDamageMillis() {
        return this.lastDamageMillis;
    }

    public void setLastDamageMillis(long lastDamageMillis) {
        this.lastDamageMillis = lastDamageMillis;
    }

    public long getLastShriekMillis() {
        return this.lastShriekMillis;
    }

    public void setLastShriekMillis(long lastShriekMillis) {
        this.lastShriekMillis = lastShriekMillis;
    }

    public long getLastShriekWaitMessageMillis() {
        return this.lastShriekWaitMessageMillis;
    }

    public void setLastShriekWaitMessageMillis(long lastShriekWaitMessageMillis) {
        this.lastShriekWaitMessageMillis = lastShriekWaitMessageMillis;
    }

    public long getFxSmokeMillis() {
        return this.fxSmokeMillis;
    }

    public void setFxSmokeMillis(long fxSmokeMillis) {
        this.fxSmokeMillis = fxSmokeMillis;
    }

    public void runFxSmoke() {
        this.fxSmokeMillis = 20L * 1000L;
    }

    public long getFxEnderMillis() {
        return this.fxEnderMillis;
    }

    public void getFxEnderMillis(long fxEnderMillis) {
        this.fxEnderMillis = fxEnderMillis;
    }

    public void runFxEnder() {
        this.fxEnderMillis = 10L * 1000L;
    }

    // FX: Shriek
    public void runFxShriek() {
        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            Location location = me.getLocation();
            World world = location.getWorld();
            world.playEffect(location, Effect.GHAST_SHRIEK, 0);
        }
    }

    // FX: SmokeBurst
    public void runFxSmokeBurst() {
        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            double dcount = VampireRevamp.getVampireConfig().specialEffects.smokeBurstCount;
            long lcount = MathUtil.probabilityRound(dcount);
            for (long i = lcount; i > 0; i--) FxUtil.smoke(me);
        }
    }

    // FX: EnderBurst
    public void runFxEnderBurst() {
        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            double dcount = VampireRevamp.getVampireConfig().specialEffects.enderBurstCount;
            long lcount = MathUtil.probabilityRound(dcount);
            for (long i = lcount; i > 0; i--) FxUtil.ender(me, 0);
        }
    }

    // FX: FlameBurst
    public void runFxFlameBurst() {
        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            double dcount = VampireRevamp.getVampireConfig().specialEffects.flameBurstCount;
            long lcount = MathUtil.probabilityRound(dcount);
            for (long i = lcount; i > 0; i--) FxUtil.flame(me);
        }
    }

    // -------------------------------------------- //
    // SHRIEK
    // -------------------------------------------- //

    public void shriek() {
        // You must be online to shriek
        Player me = Bukkit.getPlayer(uuid);
        if (me == null) {
            VampireRevamp.log(Level.WARNING, "An offline player is trying to shriek!");
            return;
        }

        if (!VampireRevamp.getVampireConfig().vampire.intend.enabled) {
            String shriekAction = VampireRevamp.getMessage(me, GrammarMessageKeys.SHRIEK);
            VampireRevamp.sendMessage(me,
                    MessageType.ERROR,
                    CommandMessageKeys.DISABLED_ACTION,
                    "{action}", shriekAction);
            return;
        }

        // You must be a vampire to shriek
        if (this.isVampire()) {
            PluginConfig conf = VampireRevamp.getVampireConfig();
            long now = System.currentTimeMillis();

            long millisSinceLastShriekWaitMessage = now - this.lastShriekWaitMessageMillis;
            if (millisSinceLastShriekWaitMessage >= conf.vampire.shriek.waitMessageCooldownMillis) {
                long millisSinceLastShriek = now - this.lastShriekMillis;
                long millisToWait = conf.vampire.shriek.cooldownMillis - millisSinceLastShriek;

                if (millisToWait > 0) {
                    long secondsToWait = (long) Math.ceil(millisToWait / 1000D);
                    VampireRevamp.sendMessage(me,
                            MessageType.ERROR,
                            SkillMessageKeys.SHRIEK_WAIT,
                            "{seconds}", String.format("%d", secondsToWait));
                    this.lastShriekWaitMessageMillis = now;
                } else {
                    this.runFxShriek();
                    this.runFxSmokeBurst();
                    this.lastShriekMillis = now;
                }
            }
        } else {
            String vampireType = VampireRevamp.getMessage(me, GrammarMessageKeys.VAMPIRE_TYPE);
            String shriekAction = VampireRevamp.getMessage(me, GrammarMessageKeys.SHRIEK);
            VampireRevamp.sendMessage(me,
                    MessageType.ERROR,
                    GrammarMessageKeys.ONLY_TYPE_CAN_ACTION,
                    "{vampire_type}", vampireType,
                    "{action}", shriekAction);
        }
    }

    public void setBatusi(boolean activate) {
        if (activate)
            enableBatusi();
        else
            disableBatusi();
    }

    public boolean isBatusi() {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null)
            return false;
        return VampireRevamp.getInstance().batEnabled.getOrDefault(player.getUniqueId(), false);
    }

    private void enableBatusi() {
        Player me = Bukkit.getPlayer(uuid);
        if (me == null) {
            VampireRevamp.log(Level.WARNING, "An offline player is trying to batusi!");
            return;
        }

        VampireRevamp plugin = VampireRevamp.getInstance();
        PluginConfig conf = VampireRevamp.getVampireConfig();

        if (!conf.vampire.batusi.enabled) {
            String batusiAction = VampireRevamp.getMessage(me, GrammarMessageKeys.BATUSI);
            VampireRevamp.sendMessage(me,
                    MessageType.ERROR,
                    CommandMessageKeys.DISABLED_ACTION,
                    "{action}", batusiAction);
            return;
        }

        CommandMessageKeys messageKey = CommandMessageKeys.BATUSI_ALREADY_USED;
        if (!plugin.batEnabled.getOrDefault(me.getUniqueId(), false)) {
            VampireRevamp.debugLog(Level.INFO, "Enabling batusi!");
            EntityUtil.spawnBats(me, conf.vampire.batusi.numberOfBats);
            VampireRevamp.debugLog(Level.INFO, "Bats spawned!");
            messageKey = CommandMessageKeys.BATUSI_TOGGLED_ON;
            this.hadFlight = me.getAllowFlight();
            plugin.batEnabled.put(me.getUniqueId(), true);
        }
        if (plugin.isDisguiseEnabled) {
            DisguiseUtil.disguiseBat(me);
            VampireRevamp.debugLog(Level.INFO, "Disguised enabled!");
        }
        if (conf.vampire.batusi.enableFlight) {
            me.setAllowFlight(true);
            me.setFlying(true);
            VampireRevamp.debugLog(Level.INFO, "Flight enabled!");
        }
        VampireRevamp.sendMessage(me, MessageType.INFO, messageKey);
        VampireRevamp.debugLog(Level.INFO, "Batusi message sent!");
    }

    private void disableBatusi() {
        VampireRevamp plugin = VampireRevamp.getInstance();
        Player sender = Bukkit.getPlayer(uuid);

        if (sender == null || !plugin.batEnabled.getOrDefault(sender.getUniqueId(), false))
            return;

        try {
            EntityUtil.despawnBats(sender);
            if (plugin.isDisguiseEnabled)
                DisguiseAPI.undisguiseToAll(sender);
            if (VampireRevamp.getVampireConfig().vampire.batusi.enableFlight) {
                sender.setAllowFlight(this.hadFlight && sender.getAllowFlight());
                sender.setFlying(sender.getAllowFlight() && sender.isFlying());
            }
            plugin.batEnabled.put(sender.getUniqueId(), false);
            VampireRevamp.sendMessage(sender,
                    MessageType.INFO,
                    CommandMessageKeys.BATUSI_TOGGLED_OFF);
        }
        catch (Exception ex) {
            VampireRevamp.sendMessage(sender,
                    MessageType.INFO,
                    CommandMessageKeys.BATUSI_ERROR);
            plugin.getLogger().log(Level.WARNING, "Error while removing bat cloud!: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // -------------------------------------------- //
    // UPDATE
    // -------------------------------------------- //

    public void update() {
        VampireRevamp.debugLog(Level.INFO, "Updating...");
        Player player = Bukkit.getPlayer(uuid);
        PluginConfig conf = VampireRevamp.getVampireConfig();

        if (player != null) {
            VampireRevamp.debugLog(Level.INFO, "Not null...");
            if (!conf.general.isBlacklisted(player.getWorld())) {
                VampireRevamp.debugLog(Level.INFO, "Not blacklisted!");
                //this.updatePermissions();
                this.updatePotionEffects();
            }
            else {
                VampireRevamp.debugLog(Level.INFO, "Another blacklist!");
                this.setBloodlusting(false);
                this.setUsingNightVision(false);
                this.setIntending(false);
                this.setBatusi(false);
                this.setRad(0);
                this.setTemp(0);
            }
        }
        VampireRevamp.debugLog(Level.INFO, "Updated");
    }

    public boolean canHaveNosferatuEffects() {
        PluginConfig conf = VampireRevamp.getVampireConfig();
        return this.isNosferatu() &&
                (!conf.radiation.removeBuffs.enabled ||
                 !conf.radiation.removeBuffs.affectNosferatu ||
                 this.getTemp() <= conf.radiation.removeBuffs.temperature);
    }

    public boolean canHaveVampireEffects() {
        PluginConfig conf = VampireRevamp.getVampireConfig();
        return this.isVampire() &&
                (!conf.radiation.removeBuffs.enabled ||
                        this.getTemp() <= conf.radiation.removeBuffs.temperature);
    }

    // -------------------------------------------- //
    // UPDATE > POTION EFFECTS
    // -------------------------------------------- //

    public void updatePotionEffects() {
        // VampireRevamp.debugLog(Level.INFO, "Updating potion effects...");
        // Find the player and their conf
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || player.isDead()) {
            return;
        }
        PluginConfig conf = VampireRevamp.getVampireConfig();
        final int targetDuration = conf.potionEffects.seconds * 20;

        // TODO: I made this dirty fix for lower tps.
        // TODO: The real solution is to tick based on millis and not ticks.

        List<StateEffectConfig> effectConfs = new LinkedList<>();
        effectConfs.add(conf.potionEffects.vampire);
        effectConfs.add(conf.potionEffects.bloodlust);
        effectConfs.add(conf.potionEffects.human);
        effectConfs.add(conf.potionEffects.infected);
        effectConfs.add(conf.potionEffects.nightvision);
        effectConfs.add(conf.potionEffects.nosferatu);
        Collections.sort(effectConfs);

        for (StateEffectConfig effectConf : effectConfs) {
            //VampireRevamp.debugLog(Level.INFO, "Group: " + effectConf.toString());
            if (effectConf.passesChecks.apply(this)) {
                //VampireRevamp.debugLog(Level.INFO, "Passes!");
                effectConf.addPotionEffects(player, targetDuration);
            }
        }

        if (isVampire()) {
            List<RadiationEffectConfig> debuffConfs = conf.radiation.effects;

            for (RadiationEffectConfig debuffConf : debuffConfs) {
                if (this.getTemp() >= debuffConf.temperature &&
                        ((debuffConf.affectNosferatu && this.isNosferatu()) ||
                        !debuffConf.affectNosferatu && !this.isNosferatu())) {
                    player.addPotionEffect(new PotionEffect(debuffConf.type, debuffConf.ticks, debuffConf.strength, true, false));
                }
            }
        }
    }

    // -------------------------------------------- //
    // TICK
    // -------------------------------------------- //

    public boolean isWearingRing(@NotNull Player player) {
        PluginConfig conf = VampireRevamp.getVampireConfig();
        if (conf.radiation.radiationRingEnabled)
            return RingUtil.isSunRing(player.getInventory().getItemInOffHand());
        return false;
    }

    public void tick(long millis) {
        Player player = Bukkit.getPlayer(uuid);
        PluginConfig conf = VampireRevamp.getVampireConfig();
        if (player != null && player.getGameMode() != GameMode.SPECTATOR && !conf.general.isBlacklisted(player.getWorld())) {
            if (!isWearingRing(player)) {
                this.tickRadTemp(millis);
            }
            this.tickInfection(millis);
            this.tickRegen(millis);
            this.tickBloodlust(millis);
            this.tickPotionEffects(millis);
            this.tickEffects(millis);
            this.tickTruce(millis);
        }
    }

    public void tickRadTemp(long millis) {
        // Update rad and temp
        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            PluginConfig conf = VampireRevamp.getVampireConfig();
            if (me.getGameMode() != GameMode.CREATIVE && this.isVampire() && !me.isDead()) {
                double irradiation = 0;
                boolean irradiationEnabled = true;

                if (VampireRevamp.isWorldGuardEnabled()) {
                    WorldGuardCompat wg = VampireRevamp.getWorldGuardCompat();
                    irradiationEnabled = wg.isIrradiationEnabled(me, me.getLocation());
                }

                if (irradiationEnabled) {
                    irradiation = SunUtil.calcPlayerIrradiation(me);
                }

                this.rad = conf.radiation.baseRadiation + irradiation;
                double tempDelta = conf.radiation.tempPerRadAndMilli * this.rad * millis;
                this.addTemp(tempDelta);
            }
            else {
                this.rad = 0;
                this.temp = 0;
            }
        }
    }

    public void tickInfection(long millis) {
        Player me = Bukkit.getPlayer(uuid);
        if (this.isInfected() && me != null) {
            int indexOld = this.infectionGetMessageIndex();
            PluginConfig conf = VampireRevamp.getVampireConfig();
            this.addInfection(millis * conf.infection.amountPerMilli);
            int indexNew = this.infectionGetMessageIndex();

            if (!this.isVampire() && indexOld != indexNew) {
                if (conf.infection.progressDamage != 0)
                    me.damage(conf.infection.progressDamage);
                if (conf.infection.progressNauseaTicks > 0)
                    FxUtil.ensure(PotionEffectType.CONFUSION, me, conf.infection.progressNauseaTicks);

                VampireRevamp.sendMessage(me,
                        MessageType.INFO,
                        InfectionMessageKeys.getFeeling(indexNew));
                VampireRevamp.sendMessage(me,
                        MessageType.INFO,
                        InfectionMessageKeys.getHint(MathUtil.random.nextInt(InfectionMessageKeys.getMaxHint())));
            }
        }
    }

    public int infectionGetMessageIndex() {
        return (int) ((InfectionMessageKeys.getMaxFeeling() + 1) * this.getInfection()) - 1;
    }

    public void tickRegen(long millis) {
        Player me = Bukkit.getPlayer(uuid);
        if (me == null) {
            return;
        }
        PluginConfig conf = VampireRevamp.getVampireConfig();
        boolean enabled = (this.isVampire() && conf.vampire.regen.enabled) ||
                          (this.isNosferatu() && conf.vampire.regenNosferatu.enabled);
        boolean buffsActive = !conf.radiation.removeBuffs.enabled ||
                                this.getTemp() < conf.radiation.removeBuffs.temperature ||
                                (this.isNosferatu() && !conf.radiation.removeBuffs.affectNosferatu);
        double minFood = this.isNosferatu() ? conf.vampire.regenNosferatu.minFood : conf.vampire.regen.minFood;
        int delayMillis = this.isNosferatu() ? conf.vampire.regenNosferatu.delayMillis : conf.vampire.regen.delayMillis;
        double foodPerMilli = this.isNosferatu() ? conf.vampire.regenNosferatu.foodPerMilli : conf.vampire.regen.foodPerMilli;
        double healthPerFood = this.isNosferatu() ? conf.vampire.regenNosferatu.healthPerFood : conf.vampire.regen.healthPerFood;

        if (enabled
                && buffsActive
                && me.getGameMode() != GameMode.CREATIVE
                && !me.isDead()
                && me.getHealth() < (int) me.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()
                && this.getFood() >= minFood) {
            long millisSinceLastDamage = System.currentTimeMillis() - this.lastDamageMillis;
            if (millisSinceLastDamage >= delayMillis) {
                double foodDiff = this.addFood(-foodPerMilli * millis);
                double healthTarget = me.getHealth() - foodDiff * healthPerFood;

                healthTarget = Math.min(healthTarget, me.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                healthTarget = Math.max(healthTarget, 0D);

                me.setHealth(healthTarget);
            }
        }
    }

    public void tickBloodlust(long millis) {
        Player me = Bukkit.getPlayer(uuid);
        if (me == null) {
            return;
        }
        PluginConfig conf = VampireRevamp.getVampireConfig();
        if (this.isVampire() && this.isBloodlusting()
                && !me.isDead()
                && me.getGameMode() != GameMode.CREATIVE) {
            this.addFood(millis * conf.vampire.bloodlust.foodPerMilli);
            if (this.getFood() < conf.vampire.bloodlust.minFood)
                this.setBloodlusting(false);
        }
    }

    public void tickPotionEffects(long millis) {
        // TODO: Will update too often!?
        this.updatePotionEffects();
    }

    public void tickEffects(long millis) {
        Player me = Bukkit.getPlayer(uuid);
        if (me != null && !me.isDead()
                && me.getGameMode() != GameMode.CREATIVE) {
            PluginConfig conf = VampireRevamp.getVampireConfig();

            // FX: Smoke
            if (this.fxSmokeMillis > 0) {
                this.fxSmokeMillis -= millis;
                double dcount = conf.specialEffects.smokePerMilli * millis;
                long lcount = MathUtil.probabilityRound(dcount);
                for (long i = lcount; i > 0; i--)
                    FxUtil.smoke(me);
            }

            // FX: Ender
            if (this.fxEnderMillis > 0) {
                this.fxEnderMillis -= millis;
                double dcount = conf.specialEffects.enderPerMilli * millis;
                long lcount = MathUtil.probabilityRound(dcount);
                for (long i = lcount; i > 0; i--)
                    FxUtil.ender(me, conf.specialEffects.enderRandomMaxLen);
            }

            // Vampire sun reactions
            if (this.isVampire()) {
                // Buffs
                if (conf.radiation.burn.enabled &&
                        (!this.isNosferatu() || conf.radiation.burn.affectNosferatu)) {
                    if (this.getTemp() > conf.radiation.burn.temperature)
                        FxUtil.ensureBurn(me, conf.radiation.burn.ticks);
                }

                // Fx
                double dsmokes = conf.radiation.smokesPerTempAndMilli * this.temp * millis;
                long lsmokes = MathUtil.probabilityRound(dsmokes);
                for (long i = lsmokes; i > 0; i--)
                    FxUtil.smoke(me);

                double dflames = conf.radiation.flamesPerTempAndMilli * this.temp * millis;
                long lflames = MathUtil.probabilityRound(dflames);
                for (long i = lflames; i > 0; i--)
                    FxUtil.flame(me);
            }
        }
    }

    // -------------------------------------------- //
    // TRADE
    // -------------------------------------------- //

    @SuppressWarnings("deprecation")
    public void tradeAccept() {
        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            VPlayer vyou = this.tradeOfferedFrom;
            Player you = vyou == null ? null : vyou.getPlayer();
            PluginConfig conf = VampireRevamp.getVampireConfig();

            // Any offer available?
            if (you == null || System.currentTimeMillis() - this.tradeOfferedAtMillis > conf.trade.offerToleranceMillis) {
                VampireRevamp.sendMessage(me,
                        MessageType.ERROR,
                        TradingMessageKeys.ACCEPT_NONE);
            } // Standing close enough?
            else if (!this.withinDistanceOf(vyou, conf.trade.offerMaxDistance)) {
                VampireRevamp.sendMessage(me,
                        MessageType.ERROR,
                        TradingMessageKeys.NOT_CLOSE,
                        "{player}", you.getDisplayName());
            } else {
                double amount = this.tradeOfferedAmount;

                // Enough blood?
                double enough = 0;
                if (conf.general.vampiresUseFoodAsBlood && vyou.isVampire()) {
                    // blood is only food for vampires
                    enough = vyou.getFood();
                } else {
                    // but blood is health for humans
                    enough = you.getHealth();
                }

                if (this.tradeOfferedAmount > enough) {
                    VampireRevamp.sendMessage(you,
                            MessageType.ERROR,
                            TradingMessageKeys.LACKING_OUT);
                    VampireRevamp.sendMessage(me,
                            MessageType.ERROR,
                            TradingMessageKeys.LACKING_IN,
                            "{player}", you.getDisplayName());
                } else {
                    // Transfer blood (food for vampires, life for humans)
                    if (conf.general.vampiresUseFoodAsBlood && vyou.isVampire()) {
                        vyou.addFood(-amount);
                    } else {
                        you.damage(amount);
                    }

                    this.addFood(amount * conf.trade.percentage);

                    // Risk infection/boost infection
                    if (vyou.isVampire() && !this.isVampire()) {
                        if (this.isInfected()) {
                            this.addInfection(0.01D);
                        }
                        else if (me.hasPermission("vampire.trade.contract")
                                && MathUtil.random.nextDouble() * 20 < amount) {
                            this.addInfection(0.05D, InfectionReason.TRADE, vyou);
                        }
                    }
                    // Trader Messages
                    VampireRevamp.sendMessage(you,
                            MessageType.INFO,
                            TradingMessageKeys.TRANSFER_OUT,
                            "{player}", me.getDisplayName(),
                            "{amount}", String.format("%.1f", amount));
                    VampireRevamp.sendMessage(me,
                            MessageType.INFO,
                            TradingMessageKeys.TRANSFER_IN,
                            "{player}", you.getDisplayName(),
                            "{amount}", String.format("%.1f", amount));

                    // Who noticed?
                    Location tradeLocation = me.getLocation();
                    World tradeWorld = tradeLocation.getWorld();
                    Location l1 = me.getEyeLocation();
                    Location l2 = you.getEyeLocation();
                    for (Player player : tradeWorld.getPlayers()) {
                        if (player.getLocation().distance(tradeLocation) <= conf.trade.visualDistance) {
                            player.playEffect(l1, Effect.POTION_BREAK, 5);
                            player.playEffect(l2, Effect.POTION_BREAK, 5);
                            if (!player.equals(me) && !player.equals(you)) {
                                VampireRevamp.sendMessage(player,
                                        MessageType.INFO,
                                        TradingMessageKeys.SEEN,
                                        "{player}", me.getDisplayName(),
                                        "{source}", you.getDisplayName());
                            }
                        }
                    }

                    // Reset trade memory
                    this.tradeOfferedFrom = null;
                    this.tradeOfferedAtMillis = 0;
                    this.tradeOfferedAmount = 0;
                }
            }
        }
    }

    public void tradeOffer(Player sender, VPlayer vyou, double amount) {
        Player you = vyou.getPlayer();
        Player me = Bukkit.getPlayer(uuid);
        if (you != null && me != null) {
            PluginConfig conf = VampireRevamp.getVampireConfig();
            if (!this.withinDistanceOf(vyou, conf.trade.offerMaxDistance)) {
                VampireRevamp.sendMessage(me,
                        MessageType.INFO,
                        TradingMessageKeys.NOT_CLOSE,
                        "{player}", you.getDisplayName());
            } else if (me.equals(you)) {
                VampireRevamp.sendMessage(me,
                        MessageType.INFO,
                        TradingMessageKeys.SELF);
                FxUtil.ensure(PotionEffectType.CONFUSION, me, 12 * 20);
            } else {
                vyou.tradeOfferedFrom = this;
                vyou.tradeOfferedAtMillis = System.currentTimeMillis();
                vyou.tradeOfferedAmount = amount;

                VampireRevamp.sendMessage(me,
                        MessageType.INFO,
                        TradingMessageKeys.OFFER_OUT,
                        "{player}", you.getDisplayName(),
                        "{amount}", String.format("%.1f", amount));

                VampireRevamp.sendMessage(you,
                        MessageType.INFO,
                        TradingMessageKeys.OFFER_IN,
                        "{player}", me.getDisplayName(),
                        "{amount}", String.format("%.1f", amount));

                VampireRevamp.sendMessage(you,
                        MessageType.INFO,
                        TradingMessageKeys.ACCEPT_HELP);
            }
        }
        else {
            VampireRevamp.sendMessage(sender,
                    MessageType.ERROR,
                    CommandMessageKeys.DATA_NOT_FOUND);
        }
    }

    public boolean withinDistanceOf(VPlayer vyou, double maxDistance) {
        boolean res = false;
        Player me = Bukkit.getPlayer(uuid);
        Player you = vyou.getPlayer();
        if (you != null && me != null && !me.isDead() && !you.isDead()) {
            Location l1 = me.getLocation();
            Location l2 = you.getLocation();
            if (l1.getWorld().equals(l2.getWorld()) && l1.distance(l2) <= maxDistance)
                res = true;
        }
        return res;
    }

    // -------------------------------------------- //
    // TRUCE
    // -------------------------------------------- //

    public void tickTruce(long millis) {
        if (this.isVampire() && this.truceIsBroken()) {

            this.truceBreakMillisLeftAlter(-millis);

            if (!this.truceIsBroken()) {
                this.truceRestore();
            }
        }
    }

    public boolean truceIsBroken() {
        return this.truceBreakMillisLeft != 0;
    }

    public void truceBreak() {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            if (!this.truceIsBroken()) {
                VampireRevamp.sendMessage(player,
                        MessageType.INFO,
                        VampirismMessageKeys.TRUCE_BROKEN);
            }
            this.truceBreakMillisLeftSet(VampireRevamp.getVampireConfig().truce.breakMillis);
        }
    }

    public void truceRestore() {
        this.truceBreakMillisLeftSet(0);

        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            VampireRevamp.sendMessage(me,
                    MessageType.INFO,
                    VampirismMessageKeys.TRUCE_RESTORED);

            // Untarget the player.
            for (LivingEntity entity : me.getWorld().getLivingEntities()) {
                if (VampireRevamp.getVampireConfig().truce.entityTypes.contains(entity.getType())
                        && entity instanceof Creature) {
                    Creature creature = (Creature) entity;

                    LivingEntity target = (LivingEntity) creature.getTarget();
                    if (me.equals(target))
                        creature.setTarget(null);
                }
            }
        }
    }

    public long truceBreakMillisLeftGet() {
        return this.truceBreakMillisLeft;
    }

    private void truceBreakMillisLeftSet(long ticks) {
        if (ticks < 0) {
            this.truceBreakMillisLeft = 0;
        } else {
            this.truceBreakMillisLeft = ticks;
        }
    }

    private void truceBreakMillisLeftAlter(long delta) {
        this.truceBreakMillisLeftSet(this.truceBreakMillisLeftGet() + delta);
    }

    // -------------------------------------------- //
    // COMBAT
    // -------------------------------------------- //

    public double combatDamageFactor() {
        double damageFactor = 0D;
        Player me = Bukkit.getPlayer(uuid);

        if (me != null) {
            PluginConfig conf = VampireRevamp.getVampireConfig();
            if (this.isBloodlusting())
                damageFactor = conf.vampire.bloodlust.damageFactor;
            else
                damageFactor = conf.vampire.damageFactor;
        }

        return damageFactor;
    }

    public double combatInfectRisk() {
        double infectRisk = 0D;

        Player me = Bukkit.getPlayer(uuid);
        if (me != null) {
            if (this.isVampire()) {
                PluginConfig conf = VampireRevamp.getVampireConfig();
                if (this.isIntending())
                    infectRisk = conf.vampire.intend.infectionChance;
                else
                    infectRisk = conf.infection.chance;
            }
        }

        return infectRisk;
    }
}
