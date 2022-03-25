package janktastic.jankbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import java.nio.Buffer;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

//Implementation of JDA AudioSendHandler that wraps lava audio player
public class LavaPlayerSendHandler implements AudioSendHandler {
  private final AudioPlayer audioPlayer;
  private final ByteBuffer buffer;
  private final MutableAudioFrame frame;

  // wrap lava audioplayer
  public LavaPlayerSendHandler(AudioPlayer audioPlayer) {
    this.audioPlayer = audioPlayer;
    this.buffer = ByteBuffer.allocate(1024);
    this.frame = new MutableAudioFrame();
    this.frame.setBuffer(buffer);
  }

  @Override
  public boolean canProvide() {
    // returns true if audio was provided
    return audioPlayer.provide(frame);
  }

  @Override
  public ByteBuffer provide20MsAudio() {
    // flip byte buffer to read mode
    ((Buffer) buffer).flip();
    return buffer;
  }

  // youtube already provides audio in opus format, tell jda it doesnt need to encode
  @Override
  public boolean isOpus() {
    return true;
  }
}