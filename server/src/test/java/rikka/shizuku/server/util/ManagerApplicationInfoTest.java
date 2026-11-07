package rikka.shizuku.server.util;

import android.content.pm.ApplicationInfo;
import android.os.RemoteException;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManagerApplicationInfoTest {
    public static class PackageService {
        public ApplicationInfo getApplicationInfo(String pkg, long flags, int user) throws RemoteException {
            throw new RemoteException("package service unavailable");
        }
    }
    public static class LegacyPackageService {
        public ApplicationInfo getApplicationInfo(String pkg, int flags, int user) {
            assertEquals("manager", pkg);
            assertEquals(123, flags);
            assertEquals(10, user);
            return null;
        }
    }
    @Test public void binderFailureIsNotReportedAsMissing() throws Exception {
        withBackend(new PackageService(), () -> assertThrows(RemoteException.class,
                () -> Android17Compat.getApplicationInfoOrThrow("manager", 0, 0)));
    }
    @Test public void legacyIntFlagsAndSuccessfulAbsenceRemainSupported() throws Exception {
        withBackend(new LegacyPackageService(), () -> {
            try { assertNull(Android17Compat.getApplicationInfoOrThrow("manager", 123, 10)); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private void withBackend(Object backend, Runnable check) throws Exception {
        Field manager = Android17Compat.class.getDeclaredField("sPackageManager");
        Field method = Android17Compat.class.getDeclaredField("sGetApplicationInfoMethod");
        manager.setAccessible(true);
        method.setAccessible(true);
        Object previousManager = manager.get(null);
        Object previousMethod = method.get(null);
        try {
            manager.set(null, backend);
            method.set(null, null);
            check.run();
        } finally {
            manager.set(null, previousManager);
            method.set(null, previousMethod);
        }
    }
}
