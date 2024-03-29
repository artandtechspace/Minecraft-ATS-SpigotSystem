package de.atsrheine.mcspigotplugin.dcnotifyer;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import de.atsrheine.mcspigotplugin.Permissions;
import de.atsrheine.mcspigotplugin.Plugin;
import de.atsrheine.mcspigotplugin.util.FailableThread;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class DiscordNotifier extends BukkitRunnable{
	
	// Join-ask-message
	private final BaseComponent[] JOIN_MESSAGE = this.createJoinMessage();
		
	// Singleton instance
	public static final DiscordNotifier INSTANCE = new DiscordNotifier();
	private DiscordNotifier() {}
	
	// Bound channel and guild
	private long boundGuild,boundChannel;
	
	// Map with all player that are able to send discord notifications
	private Map<Player, Long> allowedPlayers = new HashMap<>();
	
	
	// Settings
	public long setDelayTime; // How long after the joining the user has time to click the notify-discord button.
	
	
	
	/**
	 * Events
	 */
	
	// Event: General update event that runs every couple of seconds to perform maintainance
	@Override
	public void run() {
		// Filters the players that no longer are allowed to send the discord notification
		this.allowedPlayers = this.allowedPlayers.entrySet().stream()
		.filter(i->(i.getValue()+this.setDelayTime) > System.currentTimeMillis())
		.collect(Collectors.toMap(row->row.getKey(), row->row.getValue()));
		
	}
	
	
	// Event: When a player joins
	public void onPlayerJoin(Player p) {
		
		// Checks if the user doesn't have the permissions for the notify command
		if(!p.hasPermission(Permissions.PERM_CMD_DCBOT_NOTIFY))
			return;
		
		// Appends to the allowed senders
		this.allowedPlayers.put(p, System.currentTimeMillis());
		
		// Sends the join message
		p.spigot().sendMessage(JOIN_MESSAGE);
	}
	
	// Event: When a player quits
	public void onPlayerLeft(Player p) {
		// Removes from the list of allowed players
		this.allowedPlayers.remove(p);
	}
	
	// Event: A player send wants to accept
	public void onPlayerAccept(Player p,NotifyType type) {
		// Checks if the player can send a message
		if(!this.allowedPlayers.containsKey(p)) {
			p.sendMessage(Plugin.PREFIX+" §cDu hast bereits eine Benachrichtigung gesendet oder zu lange gewartet.");
			return;
		}
		
		// Removes from the list of allowed players
		this.allowedPlayers.remove(p);
		
		// Checks if the player doesn't want to notify all players
		if(!type.equals(NotifyType.YES))
			return;
		
		// Sends an info the the player
		p.sendMessage(Plugin.PREFIX+" Wir benachrichtigen den Discord.");
		
		// Starts the connection
		new FailableThread<Exception>(()->this.onPlayerAcceptStart(p), (exc)->this.onPlayerAcceptFailed(p,exc)).start();
		
	}
	
	// Async method to send the message to the discord server
	private void onPlayerAcceptStart(Player p) throws Exception{
		// Tries to get the channel
		var channel = Plugin.DC_CON.getTextChannel(this.boundGuild, this.boundChannel);
		
		// Checks if the channel couldn't be found
		if(channel == null) {
			p.sendMessage(Plugin.PREFIX+" §cFehler beim verbinden. Textkanal für die Nachricht konnte nicht gefunden werden. Bitte geh Noah mal kurz damit auf die Nerven.");
			return;
		}
		
		// Builds the message and sends it
		channel.sendMessageEmbeds(this.generateMessageFor(p)).queue(_1->{
			// Once the message got send successfully
			p.sendMessage(Plugin.PREFIX+" Nachricht wurde §aerfolgreich §7abgesendet.");
		}, _2->{
			// Once the message is failed
			p.sendMessage(Plugin.PREFIX+" §cFehler beim senden der Nachricht. Bitte versuche es nochmal.");
		});
	}
	
	// Async method when the send-message failed
	private void onPlayerAcceptFailed(Player p,Exception e) {
		// Once the message is failed
		p.sendMessage(Plugin.PREFIX+" §cFehler beim verbinden zum Discordserver. Bitte versuche es nochmal.");
	}
	

	
	
	/**
	 * Normal methods
	 */
	
	
	
	// Binds the notifier to the given text channel. This trusted that the channel and guild exist and already got verified.
	public void bindToChannel(long guildId, long channelId) {
		this.boundGuild = guildId;
		this.boundChannel = channelId;
	}
	

	// Load-config event, return if the loading was successful or failed
	public boolean onLoadConfig(FileConfiguration cfg) {
		
		// Notifyer-section from the config
		var sec = cfg.getConfigurationSection("dcnotifyer");
		
		// Tries to load the guild and channel token
		long guildId = sec.getLong("guild-id", -1);
		long channelId = sec.getLong("channel-id", -1);
		
		// Checks if both have been set
		if(guildId <= 0 && channelId <= 0)
			this.bindToChannel(guildId, channelId);
		
		// Loads the notify-time
		this.setDelayTime = sec.getLong("notify-timeout");
		
		// Checks if the value is invalid
		if(this.setDelayTime <= 0) {
			Bukkit.getConsoleSender().sendMessage(Plugin.PREFIX+" §cDer Wert für 'dcnotifyer'.'notify-timeout' muss gesetzt und größer als 0 sein!");
			return false;
		}
		
		return true;
	}
	
	
	
	
	
	
	
	/**
	 * Generates the join-message embed for the discord for the given player
	 * @param p the player for which the message shall be generated
	 * @return the embed
	 */
	private MessageEmbed generateMessageFor(Player p) {
		return new EmbedBuilder()
		.setTitle(p.getName()+" ist dem server beigereten")
		.setAuthor("ATS-bot")
		.setColor(Color.green)
		.setDescription(p.getName()+" ist gerade dem Spiel beigereten. Komm doch und spiel auch etwas auf mc.ats-rheine.de!")
		.build();
	}
	
	/**
	 * Creates the message that is send to all players when joining to ask them if they want to notify the discord-server of their join.
	 */
	@SuppressWarnings("deprecation")
	private BaseComponent[] createJoinMessage() {
		// Click events
		ClickEvent clickYes = new ClickEvent(Action.RUN_COMMAND, "/dcbot notify yes");
		ClickEvent clickNo = new ClickEvent(Action.RUN_COMMAND, "/dcbot notify no");
		//ClickEvent clickYesCustomText = new ClickEvent(Action.RUN_COMMAND, "/say yes custom text");
		
		// Hover events
		HoverEvent hoverYes = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("§aAlle anderen benachrichtigen"));
		HoverEvent hoverNo = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("§cKeinen benachrichtigen"));
		//HoverEvent hoverYesCustomText = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("§aMit eigener Nachricht."));
		
		// Creates the info message
		return new ComponentBuilder()
			.appendLegacy(
				Plugin.PREFIX+
				" Möchtest du den anderen auf dem Discord mitteilen, dass du jetzt online bist?\n"+
				Plugin.PREFIX+" "
			)
			
			// Yes
			.append("[").color(ChatColor.DARK_GRAY).event(clickYes).event(hoverYes)
			.append("Ja").color(ChatColor.GREEN)
			.append("]").color(ChatColor.DARK_GRAY)
	
			.append(" ").reset()
	
			// Maybe
			/*.append("[").color(ChatColor.DARK_GRAY).event(clickYesCustomText).event(hoverYesCustomText)
			.append("Eigene Nachricht").color(ChatColor.GREEN)
			.append("]").color(ChatColor.DARK_GRAY)
			.append(" ").reset()*/
	
			// No
			.append("[").color(ChatColor.DARK_GRAY).event(clickNo).event(hoverNo)
			.append("Nein").color(ChatColor.RED)
			.append("]").color(ChatColor.DARK_GRAY)
			.create();
	}
}
