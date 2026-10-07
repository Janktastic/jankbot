package janktastic.jankbot.config;

public class JankBotConfig {

	private String discordBotToken;
	private String commandPrefix;
	private String googleApiKey;
	//optional, yt-cipher server used to decipher youtube signatures
	private String remoteCipherUrl;
	private String remoteCipherPassword;
	//optional, lets the youtube TV client play age restricted videos. get one with janktastic.jankbot.YoutubeLogin
	private String youtubeOauthRefreshToken;

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

	public String getRemoteCipherUrl() {
		return remoteCipherUrl;
	}

	public void setRemoteCipherUrl(String remoteCipherUrl) {
		this.remoteCipherUrl = remoteCipherUrl;
	}

	public String getRemoteCipherPassword() {
		return remoteCipherPassword;
	}

	public void setRemoteCipherPassword(String remoteCipherPassword) {
		this.remoteCipherPassword = remoteCipherPassword;
	}

	public String getYoutubeOauthRefreshToken() {
		return youtubeOauthRefreshToken;
	}

	public void setYoutubeOauthRefreshToken(String youtubeOauthRefreshToken) {
		this.youtubeOauthRefreshToken = youtubeOauthRefreshToken;
	}

}
