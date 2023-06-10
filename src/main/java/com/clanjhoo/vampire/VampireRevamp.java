package com.clanjhoo.vampire;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import co.aikar.commands.MessageType;
import co.aikar.commands.PaperCommandManager;
import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;
import com.clanjhoo.dbhandler.data.DBObjectManager;
import com.clanjhoo.dbhandler.data.StorageType;
import com.clanjhoo.vampire.compat.WerewolfCompat;
import com.clanjhoo.vampire.jamesstuff.ClanConfigHandler;
import com.clanjhoo.vampire.jamesstuff.ClanInstance;
import com.clanjhoo.vampire.jamesstuff.PlayerClanHandler;
import com.clanjhoo.vampire.listeners.EntryVampiresListener;
import com.clanjhoo.vampire.listeners.ListenerMain;
import com.clanjhoo.vampire.listeners.PhantomListener;
import com.clanjhoo.vampire.keyproviders.GrammarMessageKeys;
import com.clanjhoo.vampire.altar.AltarDark;
import com.clanjhoo.vampire.altar.AltarLight;
import com.clanjhoo.vampire.cmd.CmdVampire;
import com.clanjhoo.vampire.compat.VampireExpansion;
import com.clanjhoo.vampire.compat.WorldGuardCompat;
import com.clanjhoo.vampire.config.PluginConfig;
import com.clanjhoo.vampire.entity.VPlayer;

import com.clanjhoo.vampire.tasks.BatTask;
import com.clanjhoo.vampire.tasks.TheTask;
import com.clanjhoo.vampire.util.CollectionUtil;
import com.clanjhoo.vampire.util.DisguiseUtil;
import com.clanjhoo.vampire.util.SemVer;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitScheduler;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VampireRevamp extends JavaPlugin {
	// -------------------------------------------- //
	// INSTANCE & CONSTRUCT
	// -------------------------------------------- //

	private static VampireRevamp plugin;
	private int cleanTaskId = -1;
	private int theTaskId = -1;
	private int batTaskId = -1;
	public PaperCommandManager manager;
	public Map<UUID, Boolean> batEnabled = new ConcurrentHashMap<>();
	public Set<LivingEntity> bats = new HashSet<>();
	public Map<UUID, List<LivingEntity>> batmap = new ConcurrentHashMap<>();
	public boolean isDisguiseEnabled = false;
	public boolean hasVault = false;
	private PluginConfig conf = null;
	private static boolean isPapermc = false;
	private AltarDark altarDark;
	private AltarLight altarLight;
	private SemVer serverVersion;
	private boolean disabled = false;
	private WorldGuardCompat wg;
	private WerewolfCompat ww;
	private VampireExpansion expansionPAPI;
	private PhantomListener pl;
	private EntryVampiresListener dvl;
	private static Permission perms = null;

	private DBObjectManager<VPlayer> vPlayerManager;
	
	// -------------------------------------------- //
	// FIELDS
	// -------------------------------------------- //

	public boolean setVampireGroup(Player player, boolean isVampire) {
		if (isVampire) {
			return perms.playerAddGroup(player, conf.compatibility.vampirePermissionGroup);
		}
		else {
			return perms.playerRemoveGroup(player, conf.compatibility.vampirePermissionGroup);
		}
	}

	public boolean permissionGroupEnabled() {
		return perms != null;
	}

	public boolean isPaperMc() {
		return isPapermc;
	}

	public AltarLight getAltarLight() {
		return altarLight;
	}

	public AltarDark getAltarDark() {
		return altarDark;
	}

	public static SemVer getServerVersion() {
		return getInstance().serverVersion;
	}

	public static boolean isWorldGuardEnabled() {
		return getInstance().wg != null && VampireRevamp.getVampireConfig().compatibility.useWorldGuardRegions;
	}

	public static WorldGuardCompat getWorldGuardCompat() {
		return getInstance().wg;
	}

	public static WerewolfCompat getWerewolvesCompat() {
		return getInstance().ww;
	}

	// -------------------------------------------- //
	// OVERRIDE
	// -------------------------------------------- //
	@Override
	public void onLoad() {
		plugin = this;

		isPapermc = false;
		try {
			Class.forName("com.destroystokyo.paper.VersionHistoryManager$VersionData");
			isPapermc = true;
		} catch (ClassNotFoundException e) {
			this.getLogger().info("Use Paper for more features like Phantom related ones!");
		}

		if (isPapermc) {
			this.getLogger().info("Using Paper");
		}

		ClanInstance.getInstance().reload();

		String versionString = this.getServer().getVersion();
		try {
			String versionRegex = "^(?:.*MC: )?([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?(?:.*)?$";
			Pattern versionPattern = Pattern.compile(versionRegex);

			Matcher versionMatcher = versionPattern.matcher(versionString);
			if (versionMatcher.find()) {
				//log(Level.INFO, "Found " + versionMatcher.groupCount() + " groups!");
				int major = Integer.parseInt(versionMatcher.group(1));
				int minor = Integer.parseInt(versionMatcher.group(2));
				int patch = 0;
				try {
					patch = Integer.parseInt(versionMatcher.group(3));
				}
				catch (NumberFormatException ignored) {}
				serverVersion = new SemVer(major, minor, patch);
				log(Level.INFO, "Detected server version: " + serverVersion);
				log(Level.INFO, "If this is not the version you are running please contact the plugin developer.");
			}
			else {
				throw new IllegalStateException("No match found after find");
			}
		} catch (Exception ex) {
			log(Level.SEVERE, "Error found while detecting server version. Version: " + versionString);
			ex.printStackTrace();
			this.getPluginLoader().disablePlugin(this);
			disabled = true;
			return;
		}

		try {
			this.saveDefaultConfig();
		}
		catch (Exception ex) {
			log(Level.WARNING, "Error found while saving default config.yml!");
			ex.printStackTrace();
		}

		vPlayerManager = null;
		loadConfig(true);

		// WorldGuard compat
		if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
			wg = new WorldGuardCompat();
		}
		else {
			wg = null;
			VampireRevamp.log(Level.WARNING, "WorldGuard plugin not detected. Disabling WorldGuard hooks.");
		}

		try {
			File localesFolder = new File(this.getDataFolder() + "/locales");
			if (!localesFolder.exists())
				localesFolder.mkdir();
			String[] providedLocales = new String[]{"lang_en.yml"};
			for (String locale : providedLocales) {
				File localeFile = new File(localesFolder, locale);
				if (!localeFile.exists()) {
					localeFile.createNewFile();
					try (InputStream is = getResource("locales/" + locale); OutputStream os = new FileOutputStream(localeFile)) {
						ByteStreams.copy(is, os); //Copies file from plugin jar into newly created file.
					}
				}
			}
		}
		catch (Exception ex) {
			log(Level.WARNING, "Error found while creating default locale files");
			ex.printStackTrace();
		}
	}

	@Override
	public void saveDefaultConfig() {
		File dataFolder = this.getDataFolder();
		File configFile = new File(dataFolder, "config.yml");
		if (!dataFolder.exists()) {
			dataFolder.mkdir();
		}

		if (!configFile.exists()) {
			PluginConfig conf = new PluginConfig();
			boolean result = conf.saveConfigToFile(configFile);
		}
	}

	public boolean reloadVampireConfig() {
		this.reloadConfig();
		ww.disable();
		HandlerList.unregisterAll(pl);
		HandlerList.unregisterAll(dvl);
		boolean result = loadConfig(false);

		loadCompat();
		return result;
	}

	public void loadCompat() {
		// Werewolves compat
		ww = new WerewolfCompat();

		// Paper compat
		if (isPapermc && new SemVer(1, 13).compareTo(serverVersion) < 0 && this.conf.truce.entityTypes.contains(EntityType.PHANTOM)) {
			pl = new PhantomListener();
			Bukkit.getPluginManager().registerEvents(pl, this);
		}

		if (wg != null && wg.useWG) {
			dvl = new EntryVampiresListener();
			Bukkit.getPluginManager().registerEvents(dvl, this);
		}
	}

	public boolean reloadLocales() {
		return loadLocales(false);
	}

	public boolean reloadAll() {
		return reloadVampireConfig() && reloadLocales();
	}

	public static DBObjectManager<VPlayer> getVPlayerManager() {
		return getInstance().vPlayerManager;
	}

	public static void loadPlayerFromDB(Player p) {
		boolean result = VampireRevamp.getVPlayerManager().getDataSynchronous(
				(vPlayer) -> {
					if (perms != null) {
						VampireRevamp.getInstance().setVampireGroup(p, vPlayer.isVampire());
					}
				},
				() -> VampireRevamp.log(Level.SEVERE, "Couldn't load player " + p.getName() + " from DB.")
				, true, p.getUniqueId());
		if (!result) {
			VampireRevamp.log(Level.SEVERE, "Error preparing load for player " + p.getName() + ".");
		}
	}

	private boolean loadConfig(boolean loadDefaults) {
		boolean result = false;
		try {
			this.conf = new PluginConfig(this.getConfig());
			log(Level.INFO, "Loaded configuration!");
			debugLog(Level.INFO, this.conf.toString());
			result = true;
		}
		catch (Exception ex) {
			log(Level.WARNING, "Error found while loading config.yml!");
			ex.printStackTrace();
			if (loadDefaults) {
				log(Level.INFO, "Loading default config...");
				this.conf = new PluginConfig();
			}
		}
		boolean reloadPlayers = false;

		if (vPlayerManager != null) {
			reloadPlayers = true;
			this.getLogger().log(Level.INFO, "Saving player data...");
			vPlayerManager.saveAll();
			this.getLogger().log(Level.INFO, "Saved!");
		}
		if (vPlayerManager == null) {
			try {
				vPlayerManager = new DBObjectManager<>(VPlayer.class, null, this, null, StorageType.JSON, "store");
			} catch (IOException ex) {
				this.getLogger().log(Level.SEVERE, "Couldn't create storage! Disabling plugin!");
				ex.printStackTrace();
				this.getPluginLoader().disablePlugin(this);
				disabled = true;
				return false;
			}
		}
		if (reloadPlayers) {
			for (Player p : Bukkit.getOnlinePlayers()) {
				VampireRevamp.loadPlayerFromDB(p);
			}
		}

		return result;
	}

	private boolean loadLocales(boolean disableOnFail) {
		boolean hasDefaultLocale = false;
		boolean finalResult = false;
		try {
			File folder = new File(this.getDataFolder() + "/locales");
			for (final File file : folder.listFiles()) {
				if (file.isFile()) {
					String[] raw = file.getName().split("\\.");
					if (raw.length != 2 || (!raw[1].equalsIgnoreCase("yml") && !raw[1].equalsIgnoreCase("yaml")))
						continue;
					raw = raw[0].split("_");
					if (raw.length != 2 || !raw[0].equalsIgnoreCase("lang"))
						continue;
					Locale loc = new Locale(raw[1]);
					manager.addSupportedLanguage(loc);
					if (conf.general.debug)
						localeLoader(file, loc);
					boolean result = manager.getLocales().loadYamlLanguageFile(file, loc);
					if (result) {
						log(Level.INFO, "Loaded language: " + loc.toString());
					}
					else {
						log(Level.INFO, "Nothing loaded from " + file.getName());
					}
					if (conf.general.defaultLocale.equals(loc) && result) {
						hasDefaultLocale = true;
						manager.getLocales().setDefaultLocale(conf.general.defaultLocale);
					}
				}
			}

			finalResult = true;
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		finally {
			if (!hasDefaultLocale) {
				Level msgLevel = disableOnFail ? Level.SEVERE : Level.WARNING;
				VampireRevamp.log(msgLevel, "Couldn't load the default locale file!");
				if (disableOnFail)
					this.getPluginLoader().disablePlugin(this);
				finalResult = false;
			}
		}

		return finalResult;
	}

	private boolean setupPermissions() {
		RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
		if (rsp == null) {
			return false;
		}
		perms = rsp.getProvider();
		return perms != null;
	}

	@Override
	public void onEnable()
	{
		if (disabled) {
			return;
		}

		isDisguiseEnabled = Bukkit.getPluginManager().isPluginEnabled("LibsDisguises");
		if (isDisguiseEnabled)
			DisguiseUtil.plugin = this;

		if (conf.compatibility.useVampirePermGroup) {
			hasVault = getServer().getPluginManager().getPlugin("Vault") != null;
			if (hasVault) {
				if (setupPermissions()) {
					if (perms.hasGroupSupport()) {
						VampireRevamp.log(Level.INFO, "Successfully hooked with Vault permission service!");
					}
					else {
						VampireRevamp.log(Level.WARNING, "Your permission plugin doesn't support groups! Vampire permission group won't be available.");
						perms = null;
					}
				}
				else {
					VampireRevamp.log(Level.WARNING, "No permission plugin compatible with Vault could be found! Vampire permission group won't be available.");
				}
			}
			else {
				VampireRevamp.log(Level.WARNING, "You need Vault plugin to enable vampire permission group!");
			}
		}

		loadCompat();

		manager = new PaperCommandManager(this);

		manager.enableUnstableAPI("help");
		setFormatting(manager);
		manager.addSupportedLanguage(conf.general.defaultLocale);
		manager.usePerIssuerLocale(true);
		if (!loadLocales(true))
			return;

		CmdVampire baseCommand = new CmdVampire();
		manager.registerCommand(baseCommand);
		baseCommand.initialize();
		manager.getCommandCompletions().registerAsyncCompletion("yesno", c -> ImmutableList.of("yes", "no"));
		manager.getCommandCompletions().registerAsyncCompletion("reloads", c -> ImmutableList.of("locales", "config", "all"));

		altarDark = new AltarDark();
		altarLight = new AltarLight();

		// PlaceholderAPI
		if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			expansionPAPI = new VampireExpansion();
			expansionPAPI.register();
		}

		// Listener
		Bukkit.getPluginManager().registerEvents(new ListenerMain(), this);


		BukkitScheduler scheduler = getServer().getScheduler();

		cleanTaskId = scheduler.scheduleSyncRepeatingTask(this, vPlayerManager::saveAndRemoveUnactive, 0L, 5 * 60 * 20L);
		theTaskId = scheduler.scheduleSyncRepeatingTask(this, new TheTask(), 0L, (this.conf.general.taskDelayMillis * 20) / 1000);
		batTaskId = scheduler.scheduleSyncRepeatingTask(this, new BatTask(), 0L, (this.conf.general.batTaskDelayMillis * 20) / 1000);
	}

	public static YamlConfiguration fileToYamlConfig (File file) throws IOException, InvalidConfigurationException {
		YamlConfiguration yamlConfiguration = new YamlConfiguration();
		yamlConfiguration.load(file);

		return yamlConfiguration;
	}

	public static boolean localeLoader(File file, Locale locale) {
		try {
			boolean loaded = false;
			YamlConfiguration config = fileToYamlConfig(file);
			Iterator var4 = config.getKeys(false).iterator();

			while(true) {
				String parentKey;
				ConfigurationSection inner;
				do {
					if (!var4.hasNext()) {
						return loaded;
					}

					parentKey = (String)var4.next();
					inner = config.getConfigurationSection(parentKey);
				} while(inner == null);

				Iterator var7 = inner.getKeys(false).iterator();

				while(var7.hasNext()) {
					String key = (String)var7.next();
					String value = inner.getString(key);
					if (value != null && !value.isEmpty()) {
						MessageKey loadedKey = MessageKey.of(parentKey + "." + key);
						debugLog(Level.INFO, "[" + locale.toString() +
								"] Loaded key " + loadedKey.getKey() +
								" with value " + value);
						loaded = true;
					}
				}
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	public static void log(Level level, String message) {
		Bukkit.getLogger().log(level, "[" + VampireRevamp.getInstance().getName() + "] " + message);
	}

	public static void debugLog(Level level, String message) {
		if (getVampireConfig().general.debug)
			log(level, message);
	}

	public static VampireRevamp getInstance() {
		return VampireRevamp.plugin;
	}

	public static PluginConfig getVampireConfig() {
		return VampireRevamp.plugin.conf;
	}

	public static PaperCommandManager getCommandManager() {
		return VampireRevamp.plugin.manager;
	}

	public File getPlayerFolder() {
		return new File(this.getDataFolder(), "store");
	}

	@Override
	public void onDisable() {
		if (disabled) {
			return;
		}
		BukkitScheduler scheduler = getServer().getScheduler();
		scheduler.cancelTask(cleanTaskId);
		scheduler.cancelTask(theTaskId);
		scheduler.cancelTask(batTaskId);

		if (vPlayerManager != null) {
			this.getLogger().log(Level.INFO, "Saving player data...");
			vPlayerManager.saveAndRemoveAllSync();
			this.getLogger().log(Level.INFO, "Saved!");
		}
	}

	public void setFormatting(CommandManager manager) {
		Set<MessageType> messageTypes = getMessageTypes();

		for (MessageType mtype : messageTypes) {
			manager.setFormat(mtype, 1, ChatColor.RESET);
			int ordis = ChatColor.RESET.ordinal();
			for (ChatColor cc : ChatColor.values()) {
				int thisIndex = 1 + cc.ordinal();
				if (cc.ordinal() < ordis)
					thisIndex += 1;
				if (cc.ordinal() != ordis) {
					//debugLog(Level.INFO, mtype.toString() + ": " + thisIndex + " -> " + cc.name());
					manager.setFormat(mtype, thisIndex, cc);
				}
			}
		}
	}

	public Set<MessageType> getMessageTypes() {
		return CollectionUtil.set(
				MessageType.ERROR,
				MessageType.HELP,
				MessageType.INFO,
				MessageType.SYNTAX
		);
	}

	public static void sendMessage(CommandSender sender, MessageType type, MessageKeyProvider key, String... replacements) {
		CommandIssuer issuer = VampireRevamp.getCommandIssuer(sender);
		List<String> allReps = VampireRevamp.colorReplacements();
		allReps.addAll(Arrays.asList(replacements));
		issuer.sendMessage(type, key, allReps.toArray(new String[0]));
	}

	private static CommandIssuer getCommandIssuer(CommandSender sender) {
		return getInstance().manager.getCommandIssuer(sender);
	}

	public static List<String> colorReplacements() {
		return CollectionUtil.list(
				"<black>", "<c2>",
				"</black>", "</c2>",
				"<dark_blue>", "<c3>",
				"</dark_blue>", "</c3>",
				"<dark_green>", "<c4>",
				"</dark_green>", "</c4>",
				"<dark_aqua>", "<c5>",
				"</dark_aqua>", "</c5>",
				"<dark_red>", "<c6>",
				"</dark_red>", "</c6>",
				"<dark_purple>", "<c7>",
				"</dark_purple>", "</c7>",
				"<gold>", "<c8>",
				"</gold>", "</c8>",
				"<gray>", "<c9>",
				"</gray>", "</c9>",
				"<grey>", "<c9>",
				"</grey>", "</c9>",
				"<dark_grey>", "<c10>",
				"</dark_grey>", "</c10>",
				"<dark_gray>", "<c10>",
				"</dark_gray>", "</c10>",
				"<blue>", "<c11>",
				"</blue>", "</c11>",
				"<green>", "<c12>",
				"</green>", "</c12>",
				"<aqua>", "<c13>",
				"</aqua>", "</c13>",
				"<red>", "<c14>",
				"</red>", "</c14>",
				"<light_purple>", "<c15>",
				"</light_purple>", "</c15>",
				"<yellow>", "<c16>",
				"</yellow>", "</c16>",
				"<white>", "<c17>",
				"</white>", "</c17>",
				"<random>", "<c18>",
				"</random>", "</c18>",
				"<b>", "<c19>",
				"</b>", "</c19>",
				"<strike>", "<c20>",
				"</strike>", "</c20>",
				"<u>", "<c21>",
				"</u>", "</c21>",
				"<i>", "<c22>",
				"</i>", "</c22>",
				"<reset>", "<c1>",
				"</reset>", "</c1>"
		);
	}

	public static String getMessage(CommandSender sender, MessageKeyProvider keyProvider) {
		return VampireRevamp.getCommandManager().getLocales().getMessage(VampireRevamp.getCommandIssuer(sender), keyProvider);
	}

	public static String[] getYouAreWere(CommandSender sender, OfflinePlayer target, boolean self) {
		String you = getMessage(sender, GrammarMessageKeys.YOU);
		you = you.substring(0, 1).toUpperCase() + you.substring(1);
		String are = getMessage(sender, GrammarMessageKeys.TO_BE_2ND);
		String were = getMessage(sender, GrammarMessageKeys.TO_BE_2ND_PAST);
		if (!self) {
			you = target.getName();
			are = getMessage(sender, GrammarMessageKeys.TO_BE_3RD);
			were = getMessage(sender, GrammarMessageKeys.TO_BE_3RD_PAST);
		}

		return new String[] {you, are, were};
	}
}