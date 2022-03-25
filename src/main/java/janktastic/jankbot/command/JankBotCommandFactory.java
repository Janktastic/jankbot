package janktastic.jankbot.command;

import java.util.HashMap;
import java.util.Map;

import janktastic.jankbot.audio.DiscordAudioManager;
import janktastic.jankbot.command.commands.ClearCommand;
import janktastic.jankbot.command.commands.HelpCommand;
import janktastic.jankbot.command.commands.LeaveCommand;
import janktastic.jankbot.command.commands.PlayCommand;
import janktastic.jankbot.command.commands.PlaySearchResultCommand;
import janktastic.jankbot.command.commands.QueueCommand;
import janktastic.jankbot.command.commands.SearchCommand;
import janktastic.jankbot.command.commands.SkipCommand;
import janktastic.jankbot.command.commands.StopCommand;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

public class JankBotCommandFactory {
  private String commandPrefix;
  private DiscordAudioManager discordAudioManager;

  public JankBotCommandFactory(DiscordAudioManager discordAudioManager, String commandPrefix) {
    this.commandPrefix = commandPrefix;
    this.discordAudioManager = discordAudioManager;
  }

  public AbstractCommand build(GuildMessageReceivedEvent event) {

    String[] message = event.getMessage().getContentRaw().split(" ");
    // if message isn't a command then return null;
    if (!message[0].startsWith(commandPrefix)) {
      return null;
    }

    User user = event.getAuthor();
    long userId = user.getIdLong();

    // split the commandPrefix from the command
    String commandString = message[0].split(commandPrefix)[1];

    AbstractCommand command;
    // handle shorthand search result play commands [0-9]
    if (commandString.length() == 1 && Character.isDigit(commandString.charAt(0))) {
      command = new PlaySearchResultCommand();
      command.setArg(commandString);
    } else {
      try {
        CommandType type = CommandType.valueOf(commandString);
        switch (type) {
          case play :
            command = new PlayCommand();
            break;
          case skip :
            command = new SkipCommand();
            break;
          case stop :
            command = new StopCommand();
            break;
          case clear :
            command = new ClearCommand();
            break;
          case search :
            command = new SearchCommand();
            break;
          case help :
            command = new HelpCommand();
            break;
          case queue :
            command = new QueueCommand();
            break;
          case leave :
            command = new LeaveCommand();
            break;
          case playSearchResult :
            command = new PlaySearchResultCommand();
            break;
          default : // unreachable as non enum values will throw an exception
                    // but gotta make the compiler happy and let it know command
                    // was initialized
            return null;
        }
      } catch (IllegalArgumentException | NullPointerException e) {
        // not a valid command, return null;
        return null;
      }
    }

    // parse options, they must start with "--" and come at the beginning of the command
    // otherwise they're considered part of the main argument
    Map<CommandOption, String> options = new HashMap<>();
    String argument = "";
    for (int i = 1; i < message.length; i++) {
      if (message[i].startsWith("--") && "".equals(argument)) {
        // split option into key/value
        String optionNoPrefix = message[i].split("--", 2)[1];
        String[] optionKeyValueArray = optionNoPrefix.split("=", 2);
        try {
          CommandOption option = CommandOption.valueOf(optionKeyValueArray[0]);
          options.put(option, optionKeyValueArray[1]);
        } catch (IllegalArgumentException | NullPointerException e) {
          // not a valid option, skip
        }

      } else {
        // if command was a playSearchResult in the form of -0, -1, etc
        // then there is no argument string and the arg is already set
        if (command.getArg() == null) {
          argument += message[i] + ' ';
        }
      }
    }
    argument = argument.trim();
    command.setOptions(options);
    if (command.getArg() == null) {
      command.setArg(argument);
    }
    command.setUserId(userId);
    command.setTextChannel(event.getChannel());
    command.setServer(event.getGuild());
    command.setCommandPrefix(commandPrefix);
    command.setDiscordAudioManager(discordAudioManager);

    return command;
  }

}
