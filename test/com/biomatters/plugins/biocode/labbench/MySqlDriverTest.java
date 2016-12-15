package com.biomatters.plugins.biocode.labbench;

import org.junit.Assert;
import org.junit.Test;

import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MySqlDriverTest extends Assert {
    @Test
    public void driverCanBeLoadedFromMultipleThreadsAtOnce() throws Exception {
        // Due to this test relying on timing, it will not fail 100% of the time
        final BiocodeService service = BiocodeService.getInstance();
        final AtomicReference<Exception> exception = new AtomicReference<Exception>();
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    Driver driver = service.getDriver();
                    assertNotNull(driver);
                } catch (Exception e) {
                    exception.set(e);
                }
            }
        };
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(runnable);
            threads.add(thread);
            thread.start();

        }
        for (Thread thread : threads) {
            thread.join();
        }
        Exception e = exception.get();
        if(e != null) throw e;
    }
}
