package janktastic.jankbot.command.commands;

import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.CommandType;

public class SearchCommand extends AbstractCommand {

  @Override
  public void run() {
    Map<String, String> idTitleMap = discordAudioManager.search(textChannel, userId, arg);
    textChannel.sendMessage("Search Results:\n").queue();
    String results = "";
    Iterator<String> iter = idTitleMap.values().iterator();
    for (int i = 0; i < idTitleMap.size(); i++) {
      results += Integer.toString(i) + ":\t\t" + iter.next() + "\n";
    }

    textChannel.sendMessage(results.isEmpty() ? "No results found." : codeblock(results) + "\nTo select a track use " + commandPrefix + "[0-9]").queue();
  }

  @Override
  public CommandType getType() {
    return CommandType.search;
  }

}
