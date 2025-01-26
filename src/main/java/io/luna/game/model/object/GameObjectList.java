package io.luna.game.model.object;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.cache.Cache;
import io.luna.game.model.StationaryEntityList;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * A {@link StationaryEntityList} that tracks all {@link GameObject} types existing natively and spawned by the server.
 *
 * @author lare96
 */
public final class GameObjectList extends StationaryEntityList<GameObject> {

    /**
     * A set of objects that were spawned by the server.
     */
    private final Set<GameObject> dynamicObjects = new HashSet<>(128);

    /**
     * A set of objects existing natively on the map. Decoded from the {@link Cache} resource.
     */
    private final Set<GameObject> staticObjects = new HashSet<>(1_376_518);

    /**
     * Creates a new {@link StationaryEntityList}.
     *
     * @param world The world.
     */
    public GameObjectList(World world) {
        super(world, EntityType.OBJECT);
    }

    @Override
    public Spliterator<GameObject> spliterator() {
        return Spliterators.spliterator(iterator(), size(),
                Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.DISTINCT);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>Warning:</strong> The returned iterator is a combination of both {@link #dynamicIterator()} and
     * {@link #staticIterator()}.
     * <p>
     * If you only want to iterate over server spawned objects, use {@link #dynamicIterator()}.
     * <p>
     * If you only want to iterate over objects existing natively, use {@link #staticIterator()}.
     */
    @SuppressWarnings("unchecked")
    @Override
    public UnmodifiableIterator<GameObject> iterator() {
        Iterator<GameObject> all = Iterators.concat(dynamicIterator(), staticIterator()); // Combine them.
        return Iterators.unmodifiableIterator(all); // Make them immutable.
    }

    @Override
    protected boolean onRegister(GameObject object) {

        if (object.isDynamic()) {
            // Check if an object will be replaced by this registration, and remove it.
            var existingObject = findAll(object.getPosition()).
                    filter(object::replaces).findFirst();
            removeFromSet(existingObject);
        }

        // Set object as active.
        boolean added = object.isDynamic() ? dynamicObjects.add(object) : staticObjects.add(object);
        if (added) {
            object.setState(EntityState.ACTIVE);
            if (object.isDynamic()) {
                object.show();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onUnregister(GameObject object) {
        Optional<GameObject> existingObject = findAll(object.getPosition()).
                filter(object::replaces).
                findFirst();
        return removeFromSet(existingObject);
    }

    @Override
    public int size() {
        return dynamicObjects.size() + staticObjects.size();
    }

    /**
     * Removes {@code object} from one of the backing sets.
     *
     * @param object The object to remove.
     * @return {@code true} if the object was removed.
     */
    private boolean removeFromSet(Optional<GameObject> object) {
        if (object.isPresent()) {
            var removeObject = object.get();
            if (removeObject.isDynamic()) {
                dynamicObjects.remove(removeObject);
            } else {
                staticObjects.remove(removeObject);
            }
            removeObject.hide();
            removeObject.setState(EntityState.INACTIVE);
            return true;
        }
        return false;
    }

    /**
     * Returns an iterator over all server spawned objects.
     */
    public UnmodifiableIterator<GameObject> dynamicIterator() {
        return Iterators.unmodifiableIterator(dynamicObjects.iterator());
    }

    /**
     * Returns an iterator over all cache loaded objects.
     */
    public UnmodifiableIterator<GameObject> staticIterator() {
        return Iterators.unmodifiableIterator(staticObjects.iterator());
    }
}