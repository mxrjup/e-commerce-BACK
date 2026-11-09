package com.ecommerce.catalog.support;

import com.ecommerce.common.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.function.Supplier;

/**
 * Turns a database constraint violation into a 409.
 *
 * <p>The services check uniqueness and usage before writing, for a clear
 * message. The constraints still have the last word, for two concurrent
 * requests or a row referenced by a table this service does not know about
 * (products, from C01): without this, those cases would surface as a 500.
 */
public final class Persistence {

    private Persistence() {
    }

    public static <T> T orConflict(Supplier<T> write, String message) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(message);
        }
    }

    public static void orConflict(Runnable write, String message) {
        orConflict(() -> {
            write.run();
            return null;
        }, message);
    }
}
