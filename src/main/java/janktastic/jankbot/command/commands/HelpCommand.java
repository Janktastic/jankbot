package janktastic.jankbot.command.commands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import janktastic.jankbot.JankBotUtil;
import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.CommandType;

public class HelpCommand extends AbstractCommand {

  @Override
  public void run() {
    //TODO: add the help string to each individual command and dynamically build this from command classes
    // and build string once instead of each time help is run. Probably when the factory is initialized.

    String format = "%-25s%-25s%n";
    String helpHeader = "**Please command me " + server.getMemberById(userId).getEffectiveName() + "-san :pleading_face:**";
    Map<String, String> commands = new LinkedHashMap<>();
    commands.put(commandPrefix + "play", "Plays an exact url or the first result for a search term");
    commands.put(commandPrefix + "search",
        "Returns the first 10 items for a search term. To play one of them use " + commandPrefix + "[0-9]");
    commands.put(commandPrefix + "skip", "Skips the current track");
    commands.put(commandPrefix + "stop", "Stops playback");
    commands.put(commandPrefix + "clear", "Clears the playback queue");
    commands.put(commandPrefix + "queue", "Print currently queued tracks");
    commands.put(commandPrefix + "leave", "Leave the current voice channel");

    String helpMsg = "";
    for (Entry<String, String> entry : commands.entrySet()) {
      helpMsg += String.format(format, entry.getKey(), entry.getValue());
    }
    textChannel.sendMessage(helpHeader + "\n" + JankBotUtil.codeblock(helpMsg)).queue();
  }

  @Override
  public CommandType getType() {
    return CommandType.help;
  }

}
