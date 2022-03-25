package janktastic.jankbot.config;

public class JankBotConfig {

  private String discordBotToken;
  // string at the beginning of a message that signifies its a command
  private String commandPrefix;
  private String googleApiKey;

  public String getDiscordBotToken() {
    return discordBotToken;
  }

  public void setDiscordBotToken(String discordBotToken) {
    this.discordBotToken = discordBotToken;
  }

  public String getCommandPrefix() {
    return commandPrefix;
  }

  public void setCommandPrefix(String commandPrefix) {
    this.commandPrefix = commandPrefix;
  }

  public String getGoogleApiKey() {
    return googleApiKey;
  }

  public void setGoogleApiKey(String googleApiKey) {
    this.googleApiKey = googleApiKey;
  }

}
