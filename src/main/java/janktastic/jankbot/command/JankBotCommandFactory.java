package janktastic.jankbot.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

public class JankBotCommandFactory {
  private String commandPrefix;
  public JankBotCommandFactory(String commandPrefix) {
    this.commandPrefix = commandPrefix;
  }

  public CommandType parseMessage(GuildMessageReceivedEvent event) {
    String[] message = event.getMessage().getContentRaw().split(" ");
    // if message isn't a command then return null;
    if (!message[0].startsWith(commandPrefix)) {
      return null;
    }
    // split the commandPrefix from the command
    String command = message[0].split(commandPrefix)[1];
    
    if (CommandType.valueOf(command) == null) {
      
    }

    // split args, anything starting with a '--' is a flag/option
    // they must come at the beginning of the command otherwise they're considered part of the main argument
    Map<String, String> options = new HashMap<>();
    String argument = null;
    for (int i = 1; i < message.length; i++) {
      if (message[i].startsWith("--") && argument == null) {
        //split option into key/value
        String[] option = message[i].split("=", 2);
        options.put(option[0], option[1]);
      } else {
        argument += message[i] + ' ';
      }
    }
    argument = argument.trim();

    User user = event.getAuthor();
    long userId = user.getIdLong();
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

}
