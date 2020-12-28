package io.luna.game.model.object;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.EntityList;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * An {@link EntityList} implementation model for {@link GameObject}s. Iterating on instances of this should be avoided
 * because of the slow {@link #iterator()} implementation.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GameObjectList extends EntityList<GameObject> {

    /**
     * A set of objects spawned by the server.
     */
    private final Set<GameObject> dynamicSet = new HashSet<>(128);

    /**
     * A set of objects
     */
    private final Set<GameObject> staticSet = new HashSet<>(); // TODO Modify based on amount of cache objects.

    /**
     * Creates a new {@link EntityList}.
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
     * <br><br>
     * <strong>Warning:</strong> This function loops through every object in the World, including objects from the
     * cache. It shouldn't be relied on in performance critical code.
     * <br><br>
     * If you only want to loop through spawned objects, use {@link #dynamicIterator()}.
     */
    @SuppressWarnings("unchecked")
    @Override
    public UnmodifiableIterator<GameObject> iterator() {
        Iterator<GameObject> all = Iterators.concat(dynamicIterator(), staticIterator()); // Combine them.
        return Iterators.unmodifiableIterator(all); // Make them immutable.
    }

    @Override
    protected boolean onRegister(GameObject object) {

        // Check if an object will be replaced by this registration, and remove it.
        var existingObject = findAll(object.getPosition()).
                filter(object::replaces).findFirst();
        removeFromSet(existingObject);

        // Set object as active.
        boolean added = object.isDynamic() ? dynamicSet.add(object) : staticSet.add(object);
        if (added) {
            object.show();
            object.setState(EntityState.ACTIVE);
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
        return dynamicSet.size() + staticSet.size();
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
                dynamicSet.remove(removeObject);
            } else {
                staticSet.remove(removeObject);
            }
            removeObject.hide();
            removeObject.setState(EntityState.INACTIVE);
            return true;
        }
        return false;
    }

    /**
     * Returns an iterator over all spawned objects.
     * @return
     */
    public UnmodifiableIterator<GameObject> dynamicIterator() {
        return Iterators.unmodifiableIterator(dynamicSet.iterator());
    }

    /**
     * Returns an iterator over all cache loaded objects.
     */
    public UnmodifiableIterator<GameObject> staticIterator() {
        return Iterators.unmodifiableIterator(staticSet.iterator());
    }
}