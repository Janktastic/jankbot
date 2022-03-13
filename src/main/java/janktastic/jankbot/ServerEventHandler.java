package janktastic.jankbot;

import java.util.ArrayList;
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

import janktastic.jankbot.command.JankBotCommand;
import janktastic.jankbot.command.JankBotCommandFactory;
import janktastic.jankbot.config.JankBotConfig;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

public class ServerEventHandler extends ListenerAdapter {
  
  private JankBotConfig jankBotConfig;
  private JankBotCommandFactory cmdFactory;
  
  private final YoutubeSearch youtubeSearch;
  private final AudioPlayerManager playerManager;
  
  //map of guild ids to player/queuemanager container
  private final Map<Long, ServerMusicManager> musicManagers;
  
  //map of discord user ids to their last search results
  private Map<Long, List<String>> userSearchMap = new HashMap<>();
  
  //discord codeblock markup for pretty printing bot responses
  private static final String CODEBLOCK = "```";

  
public ServerEventHandler(JankBotConfig jankBotConfig, YoutubeSearch youtubeSearch) {
  this.youtubeSearch = youtubeSearch;
  this.jankBotConfig = jankBotConfig;
  commandPrefix = jankBotConfig.getCommandPrefix();
  cmdFactory = new JankBotCommandFactory(commandPrefix);
}

  
  @Override
  public void onReady(ReadyEvent event) {
    System.out.println("JankBot loaded, connected to " + event.getGuildAvailableCount() + " servers:");
    
    List<Guild> guilds = event.getJDA().getGuilds();
    //print server list to track who's added JankBot
    for (Guild guild : guilds) {
      System.out.println(guild.getName());
    }
  }


  //called when bot notices a new message sent in a guild (server)
  //if command prefix detected attempt to execute the requested command
  @Override
  public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
	  JankBotCommand command = cmdFactory.parseMessage(event);
	  /*
	    TextChannel textChannel = event.getChannel();
	    VoiceChannel voiceChannel = findVoiceChannelOfUser(event.getGuild(), userId);

	    System.out.println(args.size());
	    if ("play".equals(command) && args.size() == 1) {
	      System.out.println("calling loadAndPlay");
	      loadAndPlay(textChannel, args.get(0), voiceChannel);
	    } else if ("skip".equals(command)) {
	      skipTrack(textChannel);
	    } else if ("stop".equals(command)) {
	      stopPlayback(textChannel);
	    } else if ("clear".equals(command)) {
	      emptyQueue(textChannel);
	    } else if ("search".equals(command)) {
	      search(textChannel, userId, args.get(0));
	    } else if ("help".equals(command) || "commands".equals(command)) {
	      printHelp(textChannel, user.getName());
	    } else if ("queue".equals(command)) {
	      printQueue(textChannel);
	    } else if ("leave".equals(command)) {
	      leaveVoice(event.getGuild());
	    }
	    // if command is a single digit then its a search playback request
	    else if (command.length() == 1 && Character.isDigit(command.charAt(0))) {
	      // check to make sure the requesting user has a search list to play from
	      if (userSearchMap.containsKey(userId) && !userSearchMap.isEmpty()) {
	        // generate youtube link
	        String videoId = userSearchMap.get(userId).get(Integer.valueOf(command));
	        loadAndPlay(textChannel, youtubeSearch.getVideoUrl(videoId), voiceChannel);
	      }
	    }
	  }
	  */
    
  

    super.onGuildMessageReceived(event);
  }


  

}