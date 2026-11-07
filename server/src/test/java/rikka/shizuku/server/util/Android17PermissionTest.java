package rikka.shizuku.server.util;

import org.junit.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import rikka.hidden.compat.PermissionManagerApis;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

public class Android17PermissionTest {
    public static class Android17Permissions {
        public int checkPermission(String pkg, String permission, String device, int user) {
            assertEquals("test.client", pkg);
            assertEquals("test.permission", permission);
            assertEquals("default:0", device);
            assertEquals(10, user);
            return 42;
        }
    }

    public static class LegacyPermissions {
        public int checkPermission(String permission, String pkg, int user) {
            assertEquals("test.permission", permission);
            assertEquals("test.client", pkg);
            assertEquals(10, user);
            return 43;
        }
    }

    @Test public void usesAndroid17ArgumentOrderAndPersistentDeviceId() throws Exception {
        check(new Android17Permissions(), 42);
    }

    @Test public void preservesLegacyArgumentOrder() throws Exception {
        check(new LegacyPermissions(), 43);
    }

    private void check(Object backend, int expected) throws Exception {
        Field manager = Android17Compat.class.getDeclaredField("sPermissionManager");
        Field method = Android17Compat.class.getDeclaredField("sCheckPermissionMethod");
        manager.setAccessible(true);
        method.setAccessible(true);
        Object previousManager = manager.get(null);
        Object previousMethod = method.get(null);
        try (MockedStatic<PermissionManagerApis> permissions = mockStatic(PermissionManagerApis.class)) {
            permissions.when(() -> PermissionManagerApis.checkPermission("test.permission", "test.client", 10))
                    .thenThrow(new NoSuchMethodError());
            manager.set(null, backend);
            method.set(null, null);
            assertEquals(expected, Android17Compat.checkPermission("test.permission", "test.client", 10));
        } finally {
            manager.set(null, previousManager);
            method.set(null, previousMethod);
        }
    }
}
