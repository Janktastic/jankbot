package janktastic.jankbot.command;

import java.util.Map;

public abstract class JankBotCommand {
  private CommandType type;
  private Map<CommandOption, String> options;
  private String arg;
  
  public abstract void execute();
  
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
}
