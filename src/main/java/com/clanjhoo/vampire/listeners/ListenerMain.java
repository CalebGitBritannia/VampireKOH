package com.clanjhoo.vampire.listeners;

import co.aikar.commands.MessageType;
import com.clanjhoo.vampire.*;
import com.clanjhoo.vampire.keyproviders.HolyWaterMessageKeys;
import com.clanjhoo.vampire.keyproviders.SkillMessageKeys;
import com.clanjhoo.vampire.keyproviders.VampirismMessageKeys;
import com.clanjhoo.vampire.config.PluginConfig;
import com.clanjhoo.vampire.entity.UPlayer;
import com.clanjhoo.vampire.util.EventUtil;
import com.clanjhoo.vampire.util.FxUtil;
import com.clanjhoo.vampire.util.EntityUtil;
import com.clanjhoo.vampire.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.Serializable;
import java.util.logging.Level;

public class ListenerMain implements Listener {
    // -------------------------------------------- //
    // INSTANCE & CONSTRUCT
    // -------------------------------------------- //

    // -------------------------------------------- //
    // FX
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.NORMAL)
    public void fxOnDeath(EntityDeathEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If a vampire dies ...
        if (EntityUtil.isPlayer(event.getEntity())) {
            VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{event.getEntity().getUniqueId()}, (uplayer) -> {
                if (uplayer.isVampire()) {
                    // ... burns up with a violent scream ;,,;
                    uplayer.runFxShriek();
                    uplayer.runFxFlameBurst();
                    uplayer.runFxSmokeBurst();
                }
            }, () -> {} , true);
        }
    }

    // -------------------------------------------- //
    // MISC
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void blockDamage(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        Entity entity = event.getEntity();
        PluginConfig conf = VampireRevamp.getVampireConfig();

        if (conf.vampire.blockDamageFrom.contains(event.getCause())) {
            if (EntityUtil.isPlayer(entity)) {
                try {
                    UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{entity.getUniqueId()});
                    if (uplayer.isVampire()) {
                        event.setCancelled(true);
                    }
                }
                catch (AssertionError ignore) {}
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void blockRegainHealth(EntityRegainHealthEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        Entity entity = event.getEntity();
        PluginConfig conf = VampireRevamp.getVampireConfig();

        if (conf.vampire.blockHealthFrom.contains(event.getRegainReason())) {
            if (EntityUtil.isPlayer(entity)) {
                try {
                    UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{entity.getUniqueId()});
                    if (uplayer.isVampire()) {
                        event.setCancelled(true);
                    }
                }
                catch (AssertionError ignore) {}
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void blockFoodChange(FoodLevelChangeEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        Entity entity = event.getEntity();
        if (EntityUtil.isPlayer(entity)) {
            try {
                UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{entity.getUniqueId()});
                if (uplayer.isVampire()) {
                    event.setCancelled(true);
                    EntityUtil.sendHealthFoodUpdatePacket((Player) entity);
                }
            }
            catch (AssertionError ignore) {}
        }
    }

    // -------------------------------------------- //
    // UPDATE
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void updateOnJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        VampireRevamp.loadPlayerFromDB(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void updateOnQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        VampireRevamp.getPlayerCollection().save(new Serializable[]{player.getUniqueId()});
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void updateOnTeleport(PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        if (EntityUtil.isPlayer(player)) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(VampireRevamp.getInstance(), () -> {
                UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{player.getUniqueId()});
                uplayer.update();
            });
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void updateOnDeath(EntityDeathEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If a vampire dies ...
        LivingEntity entity = event.getEntity();

        if (EntityUtil.isPlayer(entity)) {
            VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{entity.getUniqueId()}, (uplayer) -> {
                if (uplayer.isVampire()) {
                    // Close down bloodlust.
                    uplayer.setRad(0);
                    uplayer.setBloodlusting(false);
                }
            }, () -> {}, true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void updateOnRespawn(PlayerRespawnEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getPlayer().getWorld()))
            return;
        // If a vampire respawns ...
        Player player = event.getPlayer();

        if (EntityUtil.isPlayer(player)) {
            UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{player.getUniqueId()});

            if (uplayer.isVampire()) {
                // ... modify food and health levels and force another speed-update.
                Bukkit.getScheduler().scheduleSyncDelayedTask(VampireRevamp.getInstance(), () -> {
                    PluginConfig conf = VampireRevamp.getVampireConfig();
                    player.setFoodLevel(conf.vampire.respawnFood);
                    player.setHealth((double) conf.vampire.respawnHealth);
                    EntityUtil.sendHealthFoodUpdatePacket(player);
                    uplayer.update();
                });
            }
        }
    }

    /*
    private void updateNameColor(Player player) {
        if (EntityUtil.isPlayer(player)) {
            PluginConfig conf = VampireRevamp.getVampireConfig();
            if (mconf.isUpdateNameColor()) {
                UPlayer uplayer = UPlayer.get(player);

                if (uplayer != null && uplayer.isVampire()) {
                    player.setDisplayName(mconf.getUpdateNameColorTo().toString() + ChatColor.stripColor(player.getDisplayName()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void updateNameColor(AsyncPlayerChatEvent event) {
        updateNameColor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void updateNameColor(PlayerJoinEvent event) {
        updateNameColor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void updateNameColor(PlayerTeleportEvent event) {
        updateNameColor(event.getPlayer());
    }
    */

    // -------------------------------------------- //
    // DROP SELF
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dropSelf(BlockBreakEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getPlayer().getWorld()))
            return;
        // If a non-creative player ...
        Player player = event.getPlayer();
        PluginConfig conf = VampireRevamp.getVampireConfig();
        if (EntityUtil.isPlayer(player) && player.getGameMode() != GameMode.CREATIVE) {
            // ... broke a self-dropping block ...
            Material material = event.getBlock().getType();
            if (conf.general.dropSelfMaterials.contains(material)) {
                // ... then we make it drop itself.
                event.setCancelled(true);
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(material, 1));
                event.getBlock().setType(Material.AIR);
            }
        }
    }

    // -------------------------------------------- //
    // BLOODLUST
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bloodlustSmokeTrail(PlayerMoveEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getPlayer().getWorld()))
            return;
        // If a noncreative player ...
        Player player = event.getPlayer();
        if (EntityUtil.isPlayer(player) && player.getGameMode() != GameMode.CREATIVE) {
            // ... moved between two blocks ...
            Block from = event.getFrom().getBlock();
            Block to = event.getTo().getBlock();
            if (!from.equals(to)) {
                UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{player.getUniqueId()});

                // ... and that player is a vampire ...
                // ... that has bloodlust on ...
                if (uplayer.isVampire() && uplayer.isBloodlusting()) {
                    // ... then spawn smoke trail.
                    PluginConfig conf = VampireRevamp.getVampireConfig();
                    Location one = event.getFrom().clone();
                    Location two = one.clone().add(0, 1, 0);
                    long count1 = MathUtil.probabilityRound(conf.vampire.bloodlust.smokes);
                    long count2 = MathUtil.probabilityRound(conf.vampire.bloodlust.smokes);
                    for (long i = count1; i > 0; i--) FxUtil.smoke(one);
                    for (long i = count2; i > 0; i--) FxUtil.smoke(two);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bloodlustGameModeToggle(PlayerGameModeChangeEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getPlayer().getWorld()))
            return;
        // If a player enters creative or spectator mode ...
        if (event.getNewGameMode() == GameMode.CREATIVE || event.getNewGameMode() == GameMode.SPECTATOR) {
            // ... turn of bloodlust ...
            Player player = event.getPlayer();
            if (EntityUtil.isPlayer(player)) {
                VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{player.getUniqueId()}, (uplayer) -> {
                    if (uplayer.isBloodlusting()) {
                        uplayer.setBloodlusting(false);
                    }
                }, () -> {}, true);
            }
        }
    }

    // -------------------------------------------- //
    // TRUCE
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void truceTarget(EntityTargetEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If a player is targeted...
        if (EntityUtil.isPlayer(event.getTarget())) {
            VampireRevamp.debugLog(Level.INFO, "Player targeted by " + event.getEntityType().name());
            Player player = (Player) event.getTarget();
            PluginConfig conf = VampireRevamp.getVampireConfig();

            // ... by creature that cares about the truce with vampires ...
            if (player != null && conf.truce.entityTypes.contains(event.getEntityType())) {
                VampireRevamp.debugLog(Level.INFO, "Entity in truce list");
                UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{player.getUniqueId()});
                // ... and that player is a vampire ...
                // ... that has not recently done something to break the truce...
                if (uplayer.isVampire() && !uplayer.truceIsBroken()) {
                    VampireRevamp.debugLog(Level.INFO, "Vampire detected, cancelling target");
                    /*
                    // ... then if the player is a ghast target nothing ...
                    if (event.getEntityType() == EntityType.GHAST) {
                        event.setTarget(null);
                    } else {
                        // ... otherwise cancel the event.
                        event.setCancelled(true);
                    }
                    */
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void truceDamage(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If this is a combat event ...
        if (EventUtil.isCombatEvent(event)) {
            // ... to a creature that cares about the truce with vampires...
            PluginConfig conf = VampireRevamp.getVampireConfig();
            if (conf.truce.entityTypes.contains(event.getEntityType())) {
                // ... and the liable damager is a vampire ...
                Entity damager = EventUtil.getLiableDamager(event);

                if (EntityUtil.isPlayer(damager)) {
                    if (conf.truce.checkGamemode && ((Player) damager).getGameMode() == GameMode.CREATIVE)
                        return;


                    VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{damager.getUniqueId()}, (vpdamager) -> {
                        if (vpdamager.isVampire()) {
                            // Then that vampire broke the truce.
                            vpdamager.truceBreak();
                        }
                    }, () -> {}, true);
                }
            }
        }
    }

    // -------------------------------------------- //
    // REGEN
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void regen(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If the damagee is a vampire ...
        Entity entity = event.getEntity();

        if (EntityUtil.isPlayer(entity)) {
            final long timestamp = System.currentTimeMillis();
            VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{entity.getUniqueId()}, (vampire) -> {
                if (vampire.isVampire()) {
                    // ... mark now as lastDamageMillis
                    vampire.setLastDamageMillis(timestamp);
                }
            }, () -> {}, false);
        }
    }

    // -------------------------------------------- //
    // COMBAT
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void combatVulnerability(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If this is a close combat event ...
        if (EventUtil.isCloseCombatEvent(event)) {
            // ... where the liable damager is a human entity ...
            Entity damagerEntity = EventUtil.getLiableDamager(event);
            // ... and the damagee is a player ...
            Entity entity = event.getEntity();

            if (damagerEntity instanceof HumanEntity && EntityUtil.isPlayer(entity)) {
                HumanEntity damager = (HumanEntity) damagerEntity;
                PluginConfig conf = VampireRevamp.getVampireConfig();

                // ... and the damagee is a vampire ...
                UPlayer vampire = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{entity.getUniqueId()});
                if (vampire.isVampire()) {
                    // ... and a wooden item was used ...
                    ItemStack item = EntityUtil.getWeapon(damager);
                    if (item != null && conf.vampire.holyItem.materials.contains(item.getType())) {
                        // ... Then modify damage!
                        EventUtil.setDamage(event, event.getDamage() * conf.vampire.holyItem.damageFactor);
                    }
                }
            }
        }
    }

    // -------------------------------------------- //
    // Batusi
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void disableBatusiCheck(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().vampire.batusi.disableOnHit
                && EntityUtil.isPlayer(event.getEntity())) {
            Player damagee = (Player) event.getEntity();
            VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{damagee.getUniqueId()}, (udamagee) -> {
                if (udamagee.isVampire() && udamagee.isBatusi()) {
                    udamagee.setBatusi(false);
                }
            }, () -> {}, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void combatStrength(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If this is a close combat event ...
        if (EventUtil.isCloseCombatEvent(event)) {
            // ... and the liable damager is a vampire ...
            Entity damager = EventUtil.getLiableDamager(event);
            if (EntityUtil.isPlayer(damager)) {
                UPlayer vampire = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{damager.getUniqueId()});

                if (vampire.isVampire()) {
                    PluginConfig conf = VampireRevamp.getVampireConfig();
                    // ... and this event isn't a forbidden mcmmo one ...
                    if (conf.compatibility.damageWithMcmmo
                            || !event.getClass().getName().equals("com.gmail.nossr50.events.fake.FakeEntityDamageByEntityEvent")) {
                        // ... Then modify damage!
                        EventUtil.scaleDamage(event, vampire.combatDamageFactor());
                    }
                }
            }
        }
    }

    // -------------------------------------------- //
    // INFECT PLAYERS
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void infection(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If this is a close combat event ...
        if (EventUtil.isCloseCombatEvent(event)) {
            // ... where there is one vampire and one non-vampire ...
            Entity damagee = event.getEntity();
            Entity damager = EventUtil.getLiableDamager(event);
            if (EntityUtil.isPlayer(damagee) && EntityUtil.isPlayer(damager)) {
                VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{damagee.getUniqueId()}, (vampiree) -> {
                    final UPlayer[] vampire = {vampiree};
                    VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{damager.getUniqueId()}, (human) -> {
                        if ((vampire[0].isVampire() && human.isHuman())
                                || (human.isVampire() && vampire[0].isHuman())) {
                            if (human.isVampire()) {
                                UPlayer aux = vampire[0];
                                vampire[0] = human;
                                human = aux;
                            }

                            // ... and the vampire is allowed to infect through combat ...
                            // ... and the human is allowed to contract through combat ...
                            // ... Then there is a risk for infection ...
                            if (Perm.COMBAT_INFECT.has(vampire[0].getPlayer())
                                    && Perm.COMBAT_CONTRACT.has(human.getPlayer())
                                    && MathUtil.random.nextDouble() < vampire[0].combatInfectRisk()) {
                                InfectionReason reason = vampire[0].isIntending() ? InfectionReason.COMBAT_INTENDED : InfectionReason.COMBAT_MISTAKE;
                                human.addInfection(0.01D, reason, vampire[0]);
                            }
                        }
                    }, () -> {}, true);
                }, () -> {}, true);

            }
        }
    }

    // -------------------------------------------- //
    // INFECT HORSES
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void infectHorse(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If this is a close combat event ...
        // ... and the vampires can infect horses...
        PluginConfig conf = VampireRevamp.getVampireConfig();
        if (EventUtil.isCloseCombatEvent(event) && conf.infection.canInfectHorses) {
            // ... where there is one vampire ...
            // ... and one is a living horse ...
            Entity damager = EventUtil.getLiableDamager(event);
            // only horses, no mules donkeys or undead ones
            if (EntityUtil.isPlayer(damager) && event.getEntity() instanceof Horse) {
                VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{damager.getUniqueId()}, (vampire) -> {
                    // ... and the vampire is allowed to infect through combat ...
                    if (vampire.isVampire() && Perm.COMBAT_INFECT.has(vampire.getPlayer())) {
                        Horse horse = (Horse) event.getEntity();

                        // ... Then there is a risk for infection ...
                        if (MathUtil.random.nextDouble() < vampire.combatInfectRisk()) {
                            // ... then we spawn the new horse ...
                            horse.getWorld().spawnEntity(horse.getLocation(), MathUtil.random.nextDouble() > 0.5 ? EntityType.SKELETON_HORSE : EntityType.ZOMBIE_HORSE);

                            // ... and we Thanos the old one ...
                            horse.remove();
                        }
                    }
                }, () -> {}, true);
            }
        }
    }

    // -------------------------------------------- //
    // FOOD
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void foodCake(PlayerInteractEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getPlayer().getWorld()))
            return;
        Player player = event.getPlayer();
        PluginConfig conf = VampireRevamp.getVampireConfig();
        // If cake eating is not allowed for vampires ...
        if (EntityUtil.isPlayer(player) && !conf.vampire.canEatCake) {
            // .. and the player right-clicks a cake block ...
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType() == Material.CAKE) {
                // ... and the player is a vampire ...
                UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{player.getUniqueId()});
                if (uplayer.isVampire()) {
                    // ... we deny!
                    event.setCancelled(true);
                    VampireRevamp.sendMessage(player,
                            MessageType.ERROR,
                            VampirismMessageKeys.CANT_EAT_ITEM,
                            "{item}", event.getClickedBlock().getType().name());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void foodBlood(EntityDamageEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getEntity().getWorld()))
            return;
        // If this is a close combat event ...
        // ... to a living entity ...
        if (EventUtil.isCloseCombatEvent(event) && event.getEntity() instanceof LivingEntity) {
            LivingEntity damagee = (LivingEntity) event.getEntity();
            PluginConfig conf = VampireRevamp.getVampireConfig();

            // ... of a tasty type ...
            double fullFoodQuotient = conf.fullFoodQuotient.getOrDefault(damagee.getType(), 0D);
            if (fullFoodQuotient != 0) {
                // ... that has blood left ...
                if (damagee.isValid() && !damagee.isDead() && damagee.getHealth() > 0) {
                    // ... and the liable damager is a vampire ...
                    Entity damager = EventUtil.getLiableDamager(event);
                    if (EntityUtil.isPlayer(damager)) {
                        VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{damager.getUniqueId()}, (vampire) -> {
                            // ... and the player is still retrievable ...
                            if (vampire.isVampire() && vampire.getPlayer() != null) {
                                // ... drink blood! ;,,;
                                double damage = event.getFinalDamage();
                                if (conf.general.useOldFoodFormula)
                                    damage = event.getDamage();
                                if (damagee.getHealth() < damage)
                                    damage = damagee.getHealth();
                                double food = damage * fullFoodQuotient;
                                if (conf.general.useOldFoodFormula)
                                    food = damage / damagee.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() * fullFoodQuotient * vampire.getPlayer().getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();

                                vampire.addFood(food);
                            }
                        }, () -> {}, true);
                    }
                }
            }
        }
    }

    // -------------------------------------------- //
    // BLOOD FLASK
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.NORMAL)
    public void bloodFlaskConsume(PlayerItemConsumeEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getPlayer().getWorld()))
            return;

        ItemStack item = event.getItem();
        // If the item is a potion ...
        // ... and is a blood flask ...
        if (item.getType().equals(Material.POTION) && BloodFlaskUtil.isBloodFlask(item)) {
            // ... get the blood amount ...
            double amount = BloodFlaskUtil.getBloodFlaskAmount(item);

            // ... and is the blood vampiric?  ...
            boolean isVampiric = BloodFlaskUtil.isBloodFlaskVampiric(item);

            // ... get the player ...
            UPlayer uplayer = VampireRevamp.getPlayerCollection().getDataNow(new Serializable[]{event.getPlayer().getUniqueId()});

            // ... are they bloodlusting? ...
            if (!uplayer.isBloodlusting()) {
                // ... calculate and add the blood amount to the player ...
                double lacking;
                if (uplayer.isVampire()) {
                    // Vampires drink blood to replenish food.
                    lacking = (20 - uplayer.getFood());
                    if (amount > lacking) amount = lacking;
                    uplayer.addFood(amount);
                } else if (isVampiric) {
                    // ... finally, if the player is human did they contract the dark disease from vampiric blood?
                    if (uplayer.isInfected()) {
                        uplayer.addInfection(0.01D);
                    } else if (event.getPlayer().hasPermission("vampire.flask.contract")
                            && MathUtil.random.nextDouble() * 20 < amount) {
                        uplayer.addInfection(0.05D, InfectionReason.FLASK, uplayer);
                    }
                }
            } else {
                VampireRevamp.sendMessage(event.getPlayer(),
                        MessageType.ERROR,
                        SkillMessageKeys.FLASK_BLOODLUSTING);
                event.setCancelled(true);
            }
        }
    }

    // -------------------------------------------- //
    // HOLY WATER
    // -------------------------------------------- //

    /**
     * You may wonder why the PotionSplashEvent was not used.
     * That event is not triggered. This potion has no vanilla effects.
     * Thus only this projectile hit event is triggered.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void holyWater(ProjectileHitEvent event) {
        // If this projectile is a thrown potion ...
        Projectile projectile = event.getEntity();
        PluginConfig conf = VampireRevamp.getVampireConfig();
        if (!conf.general.isBlacklisted(projectile.getWorld()) &&
                projectile instanceof ThrownPotion) {
            ThrownPotion thrownPotion = (ThrownPotion) projectile;

            // ... and the potion type is holy water ...
            if (HolyWaterUtil.isHolyWater(thrownPotion)) {
                // ... who is the thrower and where did it splash? ...
                Location splashLocation = thrownPotion.getLocation();

                ProjectileSource projectileShooter = projectile.getShooter();
                if (EntityUtil.isPlayer(projectileShooter)) {
                    Player shooter = (Player) projectileShooter;

                    // ... then to all nearby players ...
                    for (Player player : splashLocation.getWorld().getPlayers()) {
                        if (EntityUtil.isPlayer(player)
                                && player.getLocation().distance(splashLocation) <= conf.holyWater.splashRadius) {
                            VampireRevamp.getPlayerCollection().getDataSynchronous(new Serializable[]{player.getUniqueId()}, (uplayer) -> {
                                VampireRevamp.sendMessage(player,
                                        MessageType.INFO,
                                        HolyWaterMessageKeys.COMMON_REACT,
                                        "{player}", shooter.getDisplayName());
                                uplayer.runFxEnderBurst();

                                // Trigger a damage event so other plugins can cancel this.
                                EntityDamageByEntityEvent triggeredEvent = new EntityDamageByEntityEvent(shooter, player, DamageCause.CUSTOM, 1D);
                                Bukkit.getPluginManager().callEvent(triggeredEvent);
                                if (!triggeredEvent.isCancelled()) {
                                    if (uplayer.isHealthy()) {
                                        VampireRevamp.sendMessage(player,
                                                MessageType.INFO,
                                                HolyWaterMessageKeys.HEALTHY_REACT);
                                    } else if (uplayer.isInfected()) {
                                        VampireRevamp.sendMessage(player,
                                                MessageType.INFO,
                                                HolyWaterMessageKeys.INFECTED_REACT);
                                        uplayer.setInfection(0);
                                        uplayer.runFxEnder();
                                    } else if (uplayer.isVampire()) {
                                        VampireRevamp.sendMessage(player,
                                                MessageType.INFO,
                                                HolyWaterMessageKeys.VAMPIRE_REACT);
                                        uplayer.addTemp(conf.holyWater.temperature);
                                        uplayer.runFxFlameBurst();
                                    }
                                }
                            }, () -> {}, true);
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------- //
    // ALTARS
    // -------------------------------------------- //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void altars(PlayerInteractEvent event) {
        if (VampireRevamp.getVampireConfig().general.isBlacklisted(event.getPlayer().getWorld()))
            return;
        VampireRevamp.debugLog(Level.INFO, "Interacted with something");
        // If the player right-clicked a block ...
        Action action = event.getAction();
        // ... without a placeable item in hand ...
        // ... and the event isn't fired with the off-hand ...
        if (action == Action.RIGHT_CLICK_BLOCK &&
                event.getHand() != EquipmentSlot.OFF_HAND &&
                (!VampireRevamp.getVampireConfig().altar.checkIfBlockInHand
                        || (!event.isBlockInHand() && !(event.getItem() != null && event.getItem().getType() == Material.REDSTONE)))) {
            VampireRevamp.debugLog(Level.INFO, "Without block in hand");
            // ... run altar logic.
            Player player = event.getPlayer();
            if (EntityUtil.isPlayer(player)) {
                VampireRevamp plugin = VampireRevamp.getInstance();
                VampireRevamp.debugLog(Level.INFO, "a real player");

                if (plugin.getAltarDark().evalBlockUse(event.getClickedBlock(), player) || plugin.getAltarLight().evalBlockUse(event.getClickedBlock(), player)) {
                    VampireRevamp.debugLog(Level.INFO, "and is an altar!");
                    event.setCancelled(true);
                }
            }
        }
    }
}