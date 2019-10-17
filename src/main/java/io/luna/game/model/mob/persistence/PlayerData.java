package io.luna.game.model.mob.persistence;

import io.luna.game.model.Position;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.mob.PlayerSettings;
import io.luna.game.model.mob.Skill;
import io.luna.game.service.GameService;
import io.luna.game.service.LogoutService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A model acting as a proxy for {@link Player} save data. It primarily ensures thread safety for interactions between
 * {@link GameService} and {@link LogoutService}. All fields in this class intended for serialization should be public.
 *
 * @author lare96 <http://github.com/lare96>
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
    public PlayerSettings settings;
    public List<IndexedItem> inventory;
    public List<IndexedItem> bank;
    public List<IndexedItem> equipment;
    public Skill[] skills;
    public List<Long> friends;
    public List<Long> ignores;
    public LocalDateTime unbanDate;
    public LocalDateTime unmuteDate;
    public double runEnergy;
    public double weight;
    public Map<String, Object> attributes;

    // Used by the LogoutService for password hashing.
    transient volatile boolean needsHash;
    transient volatile String plainTextPassword;

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
        player.setSettings(settings);
        player.getInventory().init(inventory);
        player.getBank().init(bank);
        player.getEquipment().init(equipment);
        player.getSkills().set(skills);
        player.getFriends().addAll(friends);
        player.getIgnores().addAll(ignores);
        player.setUnbanDate(unbanDate);
        player.setUnmuteDate(unmuteDate);
        player.setRunEnergy(runEnergy, false);
        player.setWeight(weight, false);
        player.getAttributes().load(attributes);
    }

    /**
     * Saves {@code player}'s data to this model.
     */
    public PlayerData save(Player player) {
        databaseId = player.getDatabaseId();
        String hashedPw = player.getHashedPassword();
        String plainTextPw = player.getPassword();
        if (hashedPw == null) {
            // No hashed password, we need to generate one.
            plainTextPassword = plainTextPw;
            needsHash = true;
        } else {
            // We have a hashed password, use it.
            password = hashedPw;
        }
        position = player.getPosition();
        rights = player.getRights();
        lastIp = player.getClient().getIpAddress();
        appearance = player.getAppearance().toArray();
        settings = player.getSettings().copy();
        inventory = player.getInventory().toList();
        bank = player.getBank().toList();
        equipment = player.getEquipment().toList();
        skills = player.getSkills().toArray();
        friends = new ArrayList<>(player.getFriends());
        ignores = new ArrayList<>(player.getIgnores());
        unbanDate = player.getUnbanDate();
        unmuteDate = player.getUnmuteDate();
        runEnergy = player.getRunEnergy();
        weight = player.getWeight();
        attributes = player.getAttributes().save();
        return this;
    }

    /**
     * @return {@code true} if the underlying player is banned.
     */
    public boolean isBanned() {
        return unbanDate != null && !LocalDateTime.now().isAfter(unbanDate);
    }
}