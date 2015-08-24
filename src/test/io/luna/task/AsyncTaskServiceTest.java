package io.luna.task;

import static org.junit.Assert.assertEquals;
import io.luna.task.AsyncTaskService;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * A test that ensures the {@link AsyncTaskService} is functioning correctly.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class AsyncTaskServiceTest {

    /**
     * Test the default service.
     */
    @Test
    public void testDefaultService() {
        AsyncTaskService services = AsyncTaskService.newService();
        simulate(services);
    }

    /**
     * Test a service that scales with the amount of tasks.
     */
    @Test
    public void testScalingService() {
        AsyncTaskService services = AsyncTaskService.newScalingThreadService();
        simulate(services);
    }

    /**
     * Test a default service that is defined by the user.
     */
    @Test
    public void testUserDefinedService() {
        AsyncTaskService services = AsyncTaskService.newService(1);
        simulate(services);
    }

    /**
     * Simulates a test using {@code services} with a randomized quantity of
     * tasks.
     * 
     * @param services
     *            the service to use.
     */
    private void simulate(AsyncTaskService services) {
        int amount = ThreadLocalRandom.current().nextInt(100) + 1;
        AtomicInteger val = new AtomicInteger();

        for (int i = 0; i < amount; i++) {
            services.add(() -> val.incrementAndGet());
        }

        services.startAsync();
        services.awaitTerminated();
        assertEquals(amount, val.get());
    }
}
