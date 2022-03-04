package janktastic.jankbot;

import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGES;
import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_VOICE_STATES;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.api.services.youtube.model.SearchListResponse;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import janktastic.jankbot.config.JankBotConfig;
import janktastic.jankbot.config.JankBotConfigFactory;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

public class JankBot extends ListenerAdapter {

	private String commandPrefix;
	private static String discordToken;
	private static JankBotConfig jankBotConfig;
	private static YoutubeSearch youtubeSearch;
	private Map<Long, List<String>> userSearchMap = new HashMap<>();
	private static final String CODEBLOCK = "```";

	public static void main(String[] args) throws Exception {
		jankBotConfig = JankBotConfigFactory.buildConfig();
		youtubeSearch = new YoutubeSearch(jankBotConfig.getGoogleApiKey());
		discordToken = jankBotConfig.getDiscordBotToken();
		
		JDABuilder.create(discordToken, GUILD_MESSAGES, GUILD_VOICE_STATES)
				.addEventListeners(new JankBot()).build();
	}

	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;

	private JankBot() throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get("banner"));
        String banner = new String(encoded, StandardCharsets.UTF_8);
        System.out.println(banner);
		this.musicManagers = new HashMap<>();

		this.playerManager = new DefaultAudioPlayerManager();
		AudioSourceManagers.registerRemoteSources(playerManager);
		AudioSourceManagers.registerLocalSource(playerManager);
	}

	private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
		long guildId = Long.parseLong(guild.getId());
		GuildMusicManager musicManager = musicManagers.get(guildId);

		if (musicManager == null) {
			musicManager = new GuildMusicManager(playerManager);
			musicManagers.put(guildId, musicManager);
		}

		guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

		return musicManager;
	}

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		String[] message = event.getMessage().getContentRaw().split(" ", 2);

		if (message[0].startsWith(commandPrefix)) {
			System.out.println("command found");
			String command = message[0].split(commandPrefix)[1];
			System.out.println(command);
			List<String> args = new ArrayList<>();
			for (int i = 1; i < message.length; i++) {
				System.out.println(message[i]);
				args.add(message[i]);
			}

			User user = event.getAuthor();
			long userId = user.getIdLong();
			VoiceChannel voiceChannel = findVoiceChannelOfUser(event.getGuild(), userId);

			System.out.println(args.size());
			if ("play".equals(command) && args.size() == 1) {
				System.out.println("calling loadAndPlay");
				loadAndPlay(event.getChannel(), args.get(0), voiceChannel);
			} else if ("skip".equals(command)) {
				skipTrack(event.getChannel());
			}
			else if ("stop".equals(command)) {
				stopPlayback(event.getChannel());
			}
			else if ("clear".equals(command)) {
				emptyQueue(event.getChannel());
			}
			else if ("search".equals(command)) {
				search(event.getChannel(), userId, args.get(0));
			}
			else if ("help".equals(command) || "commands".equals(command)) {
				printHelp(event.getChannel(), event.getAuthor().getName());
			}
			else if (command.length() == 1 && Character.isDigit(command.charAt(0))) {
				if (userSearchMap.containsKey(userId) && !userSearchMap.isEmpty()) {
					String videoId = userSearchMap.get(userId).get(Integer.valueOf(command));
					loadAndPlay(event.getChannel(), youtubeSearch.getVideoUrl(videoId), voiceChannel);
				}
			}
		}

		super.onGuildMessageReceived(event);
	}
	public void printHelp(TextChannel textChannel, String user) {
		String format = "%-25s%-25s%n";
		String helpHeader = "**Please command me " + user + "-san :pleading_face:**";
		Map<String, String> commands = new LinkedHashMap<>();
		commands.put(commandPrefix + "play", "Plays an exact url or the first result for a search term");
		commands.put(commandPrefix + "search", "Returns the first 10 items for a search term. To play one of them use " + commandPrefix + "[0-9]");
		commands.put(commandPrefix + "skip", "Skips the current track");
		commands.put(commandPrefix + "stop", "Stops playback");
		commands.put(commandPrefix + "clear", "Clears the playback queue");
		
		String helpMsg = "";
		for (Entry<String, String> entry : commands.entrySet()) {
			helpMsg += String.format(format, entry.getKey(), entry.getValue());
		}
		textChannel.sendMessage(helpHeader + "\n" + codeblock(helpMsg)).queue();
	}
	
	public void search(final TextChannel textChannel, long userId, String playRequest) {
		SearchListResponse response = youtubeSearch.search(playRequest);
		Map<String, String> idTitleMap = youtubeSearch.getIdTitleMap(response);
		userSearchMap.put(userId, new ArrayList<String>(idTitleMap.keySet()));
		textChannel.sendMessage("Search Results:\n").queue();
		String results = "";
		Iterator<String> iter = idTitleMap.values().iterator();
		for (int i = 0; i < idTitleMap.size(); i++) {
			results += Integer.toString(i) + ":\t\t" + iter.next() + "\n";
		}
		textChannel.sendMessage(results.isEmpty() ? "No results found." : codeblock(results) + "\nTo select a track use " + commandPrefix + "[0-9]").queue();
	}
	public void loadAndPlay(final TextChannel textChannel, String playRequest, final VoiceChannel voiceChannel) {
		if (voiceChannel == null) {
			System.out.println("no channel");
			textChannel.sendMessage("You must be in a voice channel or specify one in the command.").queue();
			return;
		}
		
		if (!isLink(playRequest)) {
			SearchListResponse response = youtubeSearch.search(playRequest);
			/*
			Map<String, String> idTitleMap = youtubeSearch.getIdTitleMap(response);
			for (String title: idTitleMap.values()) {
				System.out.println(title);
			}
			for (String title: idTitleMap.keySet()) {
				System.out.println(title);
			}
			*/
			if (!response.getItems().isEmpty()) {
				playRequest = youtubeSearch.getVideoUrl(response.getItems().get(0).getId().getVideoId());
			}
			
		}
		
		
		final GuildMusicManager musicManager = getGuildAudioPlayer(textChannel.getGuild());
		JankAudioLoadResultHandler audioLoadHandler = new JankAudioLoadResultHandler(this, playRequest, musicManager, textChannel, voiceChannel);
		playerManager.loadItemOrdered(musicManager, playRequest, audioLoadHandler);
	}

	public void play(Guild guild, GuildMusicManager musicManager, AudioTrack track, VoiceChannel voiceChannel) {
		System.out.println("in play");
		System.out.println("channel: " + voiceChannel.getName());
		connectToVoiceChannel(guild.getAudioManager(), voiceChannel);

		musicManager.scheduler.queue(track);
	}

	public void skipTrack(TextChannel channel) {
		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
		musicManager.scheduler.nextTrack();

		channel.sendMessage("Skipped to next track.").queue();
	}
	
	public void stopPlayback(TextChannel channel) {
		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
		musicManager.scheduler.stop();

		channel.sendMessage("Playback stopped.").queue();
	}
	
	public void emptyQueue(TextChannel channel) {
		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
		musicManager.scheduler.clearQueue();
		channel.sendMessage("Playback queue cleared.").queue();
	}

	public VoiceChannel findVoiceChannelOfUser(Guild guild, long userId) {
		List<VoiceChannel> channels = guild.getVoiceChannels();
		for (VoiceChannel channel : channels) {
			List<Member> voiceChannelMembers = channel.getMembers();
			for (Member voiceChannelMember : voiceChannelMembers) {
				if (voiceChannelMember.getIdLong() == userId) {
					return channel;
				}
			}
		}
		return null;
	}

	public void connectToFirstVoiceChannel(AudioManager audioManager) {
		if (!audioManager.isConnected()) {
			for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
				audioManager.openAudioConnection(voiceChannel);
				break;
			}
		}
	}

	public void connectToVoiceChannel(AudioManager audioManager, VoiceChannel voiceChannel) {
		if (!audioManager.isConnected()) {
			audioManager.openAudioConnection(voiceChannel);
		}
	}
	
	private boolean isLink(String arg) {
		if (arg.startsWith("http://") || arg.startsWith("https://")) {
			return true;
		}
		return false;
	}
	private String codeblock(String text) {
		return CODEBLOCK + text + CODEBLOCK;
	}
}