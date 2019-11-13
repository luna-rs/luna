package io.luna.game.model;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.mob.Player;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model that handles {@link Area} registration and tracking.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class AreaManager implements Iterable<Area> {

    /**
     * The world.
     */
    private final World world;

    /**
     * The map of registered {@link Area}s.
     */
    private final Set<Area> registeredAreas = new HashSet<>();

    /**
     * Creates a new {@link AreaManager}.
     *
     * @param world The world.
     */
    public AreaManager(World world) {
        this.world = world;
    }

    @Override
    public UnmodifiableIterator<Area> iterator() {
        return Iterators.unmodifiableIterator(registeredAreas.iterator());
    }

    @Override
    public Spliterator<Area> spliterator() {
        return Spliterators.spliterator(registeredAreas, Spliterator.NONNULL | Spliterator.DISTINCT);
    }

    /**
     * Registers an area.
     *
     * @param area The area.
     */
    public void register(Area area) {
        checkState(registeredAreas.add(area), "This area is already registered!");
    }

    /**
     * Fires an {@code enter}, {@code exit}, or {@code move} listener on position changes.
     *
     * @param player The player.
     * @param oldPos The old position.
     * @param newPos The new position.
     */
    public void notifyPositionChange(Player player, Position oldPos, Position newPos) {
        for (var area : registeredAreas) {
            boolean containsOld = area.contains(oldPos);
            boolean containsNew = area.contains(newPos);
            if (containsOld && !containsNew) {
                // Old position is in this area, new position isn't.
                area.exit(player);
            } else if (!containsOld && containsNew) {
                // Old position isn't in this area, new position is.
                area.enter(player);
            } else if (containsOld && containsNew) {
                // Old position is in this area, so is the new one.
                area.move(player);
            }
        }
    }

    /**
     * Fires an {@code enter} listener on login.
     *
     * @param player The player.
     */
    public void notifyLogin(Player player) {
        var position = player.getPosition();
        registeredAreas.stream()
            .filter(area -> area.contains(position))
            .forEach(area -> area.enter(player));
    }

    /**
     * Fires an {@code exit} listener on logout.
     *
     * @param player The player.
     */
    public void notifyLogout(Player player) {
        var position = player.getPosition();
        registeredAreas.stream()
            .filter(area -> area.contains(position))
            .forEach(area -> area.exit(player));
    }

    /**
     * Returns all registered {@link Area}s that contain {@code position}.
     *
     * @param position The position.
     * @return The areas that contain the position.
     */
    public List<Area> getAllContaining(Position position) {
        return registeredAreas.stream()
            .filter(area -> area.contains(position))
            .collect(Collectors.toList());
    }

    /**
     * Returns the first (smallest) {@link Area} that contains {@code position}.
     *
     * @param position The position.
     * @return The areas that contain the position.
     */
    public Optional<Area> getFirstContaining(Position position) {
        var areaList = getAllContaining(position);
        return areaList.stream().min(Comparator.comparingInt(Area::size));
    }

    /**
     * Returns the last (largest) {@link Area} that contains {@code position}.
     *
     * @param position The position.
     * @return The areas that contain the position.
     */
    public Optional<Area> getLastContaining(Position position) {
        var areaList = getAllContaining(position);
        return areaList.stream().max(Comparator.comparingInt(Area::size));
    }

    /**
     * @return A stream over the registered areas.
     */
    public Stream<Area> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
