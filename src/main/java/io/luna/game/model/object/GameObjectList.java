package io.luna.game.model.object;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.EntityList;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * An {@link EntityList} implementation model for {@link GameObject}s. Iterating on instances of this should
 * be avoided because of the slow {@link #iterator()} implementation.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class GameObjectList extends EntityList<GameObject> {

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
     * <strong>Warning:</strong> This function can run pretty slow and therefore shouldn't be relied on in
     * performance critical code.
     */
    @Override
    public UnmodifiableIterator<GameObject> iterator() {
        List<Iterator<GameObject>> iteratorList = new ArrayList<>();
        for (Chunk chunk : world.getChunks()) {
            Set<GameObject> objects = chunk.getAll(type);
            if (objects.size() > 0) { // Retrieve iterators from chunk.
                iteratorList.add(objects.iterator());
            }
        }
        Iterator[] iteratorArray = Iterators.toArray(iteratorList.iterator(), Iterator.class);
        Iterator<GameObject> all = Iterators.concat(iteratorArray); // Combine them.
        return Iterators.unmodifiableIterator(all); // Make them immutable.
    }
}