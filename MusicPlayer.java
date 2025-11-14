import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public class MusicPlayer {
    private Clip clip;
    private boolean paused = false;
    private long pausePosMicros = 0;

    public void playLoopFromFile(String path) throws Exception {
        stop();
        File f = new File(path);
        if (!f.exists()) throw new IOException("Arquivo não encontrado: " + path);
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(f)) {
            startClip(ais);
        }
    }

    public void playLoopFromURL(String urlStr) throws Exception {
        stop();
        URL url = new URL(urlStr);
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(url)) {
            startClip(ais);
        }
    }

    private void startClip(AudioInputStream ais) throws Exception {
        AudioFormat base = ais.getFormat();
        AudioFormat dec = base;
        if (base.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            dec = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16, base.getChannels(),
                    base.getChannels() * 2, base.getSampleRate(), false);
            ais = AudioSystem.getAudioInputStream(dec, ais);
        }
        DataLine.Info info = new DataLine.Info(Clip.class, dec);
        clip = (Clip) AudioSystem.getLine(info);
        clip.open(ais);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        clip.start();
        paused = false;
        pausePosMicros = 0;
    }

    public void togglePause() {
        if (clip == null) return;
        if (!paused) {
            pausePosMicros = clip.getMicrosecondPosition();
            clip.stop();
            paused = true;
        } else {
            clip.setMicrosecondPosition(pausePosMicros);
            clip.start();
            paused = false;
        }
    }

    public void stop() {
        if (clip != null) {
            clip.stop();
            clip.flush();
            clip.close();
            clip = null;
        }
        paused = false;
        pausePosMicros = 0;
    }
}
