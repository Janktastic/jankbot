package janktastic.jankbot.command;

import java.util.Map;

import janktastic.jankbot.DiscordAudioManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

public abstract class AbstractJankBotCommand {
  protected Map<CommandOption, String> options;
  protected String arg;
  // the user who is executing the command
  protected long userId;
  //the channel the command was executed in
  protected TextChannel textChannel;
  //the server the command was executed in
  protected Guild server;
  protected DiscordAudioManager discordAudioManager;

  protected AbstractJankBotCommand() {
  };

  protected AbstractJankBotCommand(Map<CommandOption, String> options, String arg, long userId,
      TextChannel textChannel, DiscordAudioManager discordAudioManager) {
    this.options = options;
    this.arg = arg;
    this.userId = userId;
    this.textChannel = textChannel;
    this.server = textChannel.getGuild();
    this.discordAudioManager = discordAudioManager;
  }

  public abstract void run();
  
  protected Map<CommandOption, String> getOptions() {
    return options;
  }

  protected void setOptions(Map<CommandOption, String> options) {
    this.options = options;
  }

  protected String getArg() {
    return arg;
  }

  protected void setArg(String arg) {
    this.arg = arg;
  }

  protected long getUserId() {
    return userId;
  }

  protected void setUserId(long userId) {
    this.userId = userId;
  }

  protected TextChannel getTextChannel() {
    return textChannel;
  }

  protected void setTextChannel(TextChannel textChannel) {
    this.textChannel = textChannel;
  }

  protected Guild getServer() {
    return server;
  }

  protected void setServer(Guild server) {
    this.server = server;
  }

  protected DiscordAudioManager getDiscordAudioManager() {
    return discordAudioManager;
  }

  protected void setDiscordAudioManager(DiscordAudioManager discordAudioManager) {
    this.discordAudioManager = discordAudioManager;
  }
}
