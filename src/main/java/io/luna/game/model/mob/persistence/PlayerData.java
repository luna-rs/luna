package io.luna.game.model.mob.persistence;

import io.luna.Luna;
import io.luna.game.GameSettings.PasswordStrength;
import io.luna.game.model.Position;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerMusicTab;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.mob.Skill;
import io.luna.game.service.GameService;
import io.luna.game.service.LogoutService;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A model acting as a proxy for {@link Player} save data. It primarily ensures thread safety for interactions between
 * {@link GameService} and {@link LogoutService}. All fields in this class intended for serialization should be public.
 *
 * @author lare96
 */
public final class PlayerData {

    /* This should be avoided this unless necessary and attributes used instead. But if you wish to save player data
       the 'old' way simply declare a field then add it to the 'save' and 'load' functions. */
    public int databaseId;
    public volatile String password;
    public Position position;
    public PlayerRights rights;
    public String lastIp;
    public int[] appearance;
    public PlayerMusicTab musicTab;
    public List<IndexedItem> inventory;
    public List<IndexedItem> bank;
    public List<IndexedItem> equipment;
    public Skill[] skills;
    public List<Long> friends;
    public List<Long> ignores;
    public Instant unbanInstant;
    public Instant unmuteInstant;
    public double runEnergy;
    public double weight;
    public Map<String, Integer> varps;
    public List<Object> attributes;

    // Used by persistence classes to ignore temporary bots.
    transient volatile boolean temporaryBot;
    transient volatile boolean bot;

    /**
     * The username of the player this data belongs to.
     */
    private transient final String username;

    /**
     * Creates a new {@link PlayerData}.
     *
     * @param username The username of the player this data belongs to.
     */
    public PlayerData(String username) {
        this.username = username;
    }

    /**
     * Loads {@code player}'s data from this model.
     */
    public void load(Player player) {
        player.setDatabaseId(databaseId);
        player.setHashedPassword(password);
        player.setPosition(position);
        player.setRights(rights);
        player.setLastIp(lastIp);
        player.getAppearance().setValues(appearance);
        player.setMusicTab(musicTab);
        player.getInventory().init(inventory);
        player.getBank().init(bank);
        player.getEquipment().init(equipment);
        player.getSkills().set(skills);
        player.getFriends().addAll(friends);
        player.getIgnores().addAll(ignores);
        player.setUnbanInstant(unbanInstant);
        player.setUnmuteInstant(unmuteInstant);
        player.setRunEnergy(runEnergy, false);
        player.setWeight(weight, false);
        player.getAttributes().load(attributes);
        player.getVarpManager().fromMap(varps);
    }

    /**
     * Saves {@code player}'s data to this model.
     */
    public PlayerData save(Player player) {
        String hashedPw = player.getHashedPassword();
        String plainTextPw = player.getPassword();
        if (hashedPw == null) {
            // No hashed password, we need to generate one.
            PasswordStrength passwordStrength = Luna.settings().game().passwordStrength();
            if(passwordStrength != PasswordStrength.NONE) {
                String salt = BCrypt.gensalt(passwordStrength.getRounds());
                password = BCrypt.hashpw(plainTextPw, salt);
            } else {
                password = "password";
            }
        } else {
            // We have a hashed password, use it.
            password = hashedPw;
        }
        bot = player.isBot();
        temporaryBot = bot && player.asBot().isTemporary();

        databaseId = player.getDatabaseId();
        position = player.getPosition();
        rights = player.getRights();
        lastIp = player.getClient().getIpAddress();
        appearance = player.getAppearance().toArray();
        musicTab = player.getMusicTab().copy();
        inventory = player.getInventory().toList();
        bank = player.getBank().toList();
        equipment = player.getEquipment().toList();
        skills = player.getSkills().toArray();
        friends = new ArrayList<>(player.getFriends());
        ignores = new ArrayList<>(player.getIgnores());
        unbanInstant = player.getUnbanInstant();
        unmuteInstant = player.getUnmuteInstant();
        runEnergy = player.getRunEnergy();
        weight = player.getWeight();
        attributes = player.getAttributes().save();
        varps = player.getVarpManager().toMap();
        return this;
    }

    /**
     * @return The username of the player this data belongs to.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return {@code true} if the underlying player is banned.
     */
    public boolean isBanned() {
        return unbanInstant != null && !Instant.now().isAfter(unbanInstant);
    }
}