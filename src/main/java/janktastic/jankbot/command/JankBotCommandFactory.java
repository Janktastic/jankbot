package janktastic.jankbot.command;

import java.util.HashMap;
import java.util.Map;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

public class JankBotCommandFactory {
	private String commandPrefix;

	public JankBotCommandFactory(String commandPrefix) {
		this.commandPrefix = commandPrefix;
	}

	public JankBotCommand build(GuildMessageReceivedEvent event) {
		
		String[] message = event.getMessage().getContentRaw().split(" ");
		// if message isn't a command then return null;
		if (!message[0].startsWith(commandPrefix)) {
			return null;
		}
		
		User user = event.getAuthor();
    long userId = user.getIdLong();
    
		// split the commandPrefix from the command
		String commandString = message[0].split(commandPrefix)[1];

		JankBotCommand command;
		// handle shorthand search result play commands [0-9]
		if (commandString.length() == 1 && Character.isDigit(commandString.charAt(0))) {
			command = new JankBotCommand(CommandType.playSearchResult, null, null, userId, event.getChannel(), event.getGuild());
			return command;
		}

		command = new JankBotCommand();
		try {
			CommandType type = CommandType.valueOf(commandString);
			command.setType(type);
		} catch (IllegalArgumentException | NullPointerException e) {
			// not a valid command, return null;
			return null;
		}

		// split args, anything starting with a '--' is a flag/option
		// they must come at the beginning of the command otherwise they're considered
		// part of the main argument
		Map<CommandOption, String> options = new HashMap<>();
		String argument = "";
		for (int i = 1; i < message.length; i++) {
			if (message[i].startsWith("--") && "".equals(argument)) {
				// split option into key/value
				String[] optionKeyValueString = message[i].split("=", 2);
				try {
					CommandOption option = CommandOption.valueOf(optionKeyValueString[0]);
					options.put(option, optionKeyValueString[1]);
				} catch (IllegalArgumentException | NullPointerException e) {
					// not a valid option, skip
				}

			} else {
				argument += message[i] + ' ';
			}
		}
		argument = argument.trim();
		command.setOptions(options);
		command.setArg(argument);
		command.setUserId(userId);
		command.setTextChannel(event.getChannel());
		command.setServer(event.getGuild());
		return command;
	}

}
