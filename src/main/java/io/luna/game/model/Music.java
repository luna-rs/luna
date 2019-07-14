package io.luna.game.model;

import com.google.gson.Gson;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.ColorChangeMessageWriter;
import io.luna.net.msg.out.MusicMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Music {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The collection of songs for the server
     */
    public static Music[] repo;

    /**
     * Model for Music JSON objects
     */
    public String song_name = "";
    public int song_id = -1;
    public int music_tab_id = -1;
    public int music_button_id = -1;
    public int[] regions = null;

    public Music(String song_name, int song_id, int music_tab_id, int music_button_id, int[] regions) {
        this.song_name = song_name;
        this.song_id = song_id;
        this.music_tab_id = music_tab_id;
        this.music_button_id = music_button_id;
        this.regions = regions;
    }

    /**
     * Loads Music definitions from JSON
     */
    public static void loadMusic() {
        try {
            Music[] musicRepo = new Gson().fromJson(new FileReader(new File("./data/def/audio/music.json")), Music[].class);
            repo = musicRepo;
            LOGGER.info("Loaded " + musicRepo.length + " Songs.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Attempts to find the proper song to play based on region ID
     * @param regionId
     * @return
     */
    public static Music getSongForReqion(int regionId) {
        if (repo==null)
            loadMusic();
        for (Music m : repo) {
            for (int region : m.regions) {
                if (region == regionId) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Updates the players music interface with previously unlocked songs
     * @param plr
     */
    public static void updateMusicInterface(Player plr) {
        if (Music.repo==null)
            Music.loadMusic();

        for (Music m : Music.repo) {
            if (m.music_tab_id!=-1)
            if (plr.unlockedSongs[m.song_id] == 1) {
                plr.queue(new ColorChangeMessageWriter(m.music_tab_id, Color.GREEN));
            } else {
                plr.queue(new ColorChangeMessageWriter(m.music_tab_id, Color.RED));
            }
        }
        Music startingSong = getSongForReqion(plr.getPosition().getRegionPosition().getId());
        if (startingSong!=null) {
            stopMusic(plr);
            playSong(startingSong, plr);
        }
    }

    /**
     * Plays a song to a given player.
     * @param m
     * @param plr
     */
    public static void playSong(Music m, Player plr) {
        if (plr.lastSong!=m.song_id) {
            plr.lastSong=m.song_id;
            plr.queue(new MusicMessageWriter(m.song_id));
            plr.queue(new ColorChangeMessageWriter(m.music_tab_id, Color.GREEN));
            plr.unlockedSongs[m.song_id] = 1;
        }
    }

    /**
     * Stops music playback for a given player
     * @param player
     */
    public static void stopMusic(Player player) {
        if (player.lastSong!=0) {
            player.lastSong=0;
            player.queue(new MusicMessageWriter(-1));
        }
    }
}
