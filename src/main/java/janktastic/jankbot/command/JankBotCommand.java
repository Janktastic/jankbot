package janktastic.jankbot.command;

import java.util.Map;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

public class JankBotCommand {
  private CommandType type;
  private Map<CommandOption, String> options;
  private String arg;
  // the user who is executing the command
  private long userId;
  //the channel the command was executed in
  private TextChannel textChannel;
  //the server the command was executed in
  private Guild server;

  protected JankBotCommand() {
  };

  protected JankBotCommand(CommandType type, Map<CommandOption, String> options, String arg, long userId,
      TextChannel textChannel, Guild server) {
    this.type = type;
    this.options = options;
    this.arg = arg;
    this.userId = userId;
    this.textChannel = textChannel;
    this.server = server;
  }

  public CommandType getType() {
    return type;
  }

  public void setType(CommandType type) {
    this.type = type;
  }

  public Map<CommandOption, String> getOptions() {
    return options;
  }

  public void setOptions(Map<CommandOption, String> options) {
    this.options = options;
  }

  public String getArg() {
    return arg;
  }

  public void setArg(String arg) {
    this.arg = arg;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public TextChannel getTextChannel() {
    return textChannel;
  }

  public void setTextChannel(TextChannel textChannel) {
    this.textChannel = textChannel;
  }

  public Guild getServer() {
    return server;
  }

  public void setServer(Guild server) {
    this.server = server;
  }
}
