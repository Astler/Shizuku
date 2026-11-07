package rikka.shizuku.server;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.Arrays;
import java.util.Collections;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.UserManagerApis;
import rikka.shizuku.server.ktx.HandlerKt;
import rikka.shizuku.server.util.Android17Compat;
import rikka.shizuku.server.util.InstalledPackagesCompat;

public class ShizukuConfigManagerTest {
    @Test public void restartPreservesConfigWithoutFrameworkGrantAndNewDecisionKeepsOwner() {
        ShizukuConfig saved = new ShizukuConfig();
        ShizukuConfig.PackageEntry allowed = new ShizukuConfig.PackageEntry(10001, ConfigManager.FLAG_ALLOWED);
        allowed.packages.add("test.allowed");
        ShizukuConfig.PackageEntry denied = new ShizukuConfig.PackageEntry(10002, ConfigManager.FLAG_DENIED);
        denied.packages.add("test.denied");
        saved.packages.addAll(Arrays.asList(allowed, denied));
        PackageInfo first = info(10001, "test.allowed");
        PackageInfo second = info(10002, "test.denied");
        try (MockedStatic<ShizukuConfigManager> storage = mockStatic(ShizukuConfigManager.class);
             MockedStatic<PackageManagerApis> packages = mockStatic(PackageManagerApis.class);
             MockedStatic<UserManagerApis> users = mockStatic(UserManagerApis.class);
             MockedStatic<InstalledPackagesCompat> installed = mockStatic(InstalledPackagesCompat.class);
             MockedStatic<Android17Compat> permissions = mockStatic(Android17Compat.class);
             MockedStatic<HandlerKt> handlers = mockStatic(HandlerKt.class)) {
            storage.when(ShizukuConfigManager::load).thenReturn(saved);
            packages.when(() -> PackageManagerApis.getPackagesForUidNoThrow(10001)).thenReturn(Collections.singletonList("test.allowed"));
            packages.when(() -> PackageManagerApis.getPackagesForUidNoThrow(10002)).thenReturn(Collections.singletonList("test.denied"));
            packages.when(() -> PackageManagerApis.getPackagesForUidNoThrow(10003)).thenReturn(Collections.singletonList("test.new"));
            users.when(UserManagerApis::getUserIdsNoThrow).thenReturn(Collections.singletonList(0));
            installed.when(() -> InstalledPackagesCompat.getInstalledPackagesNoThrow(PackageManager.GET_PERMISSIONS, 0))
                    .thenReturn(Arrays.asList(first, second));
            handlers.when(HandlerKt::getWorkerHandler).thenReturn(mock(Handler.class));

            ShizukuConfigManager manager = new ShizukuConfigManager();
            assertTrue(manager.find(10001).isAllowed());
            assertTrue(manager.find(10002).isDenied());
            permissions.verifyNoInteractions();

            manager.update(10003, null, ConfigManager.MASK_PERMISSION, ConfigManager.FLAG_ALLOWED);
            assertEquals(Collections.singletonList("test.new"), manager.find(10003).packages);
        }
    }

    private static PackageInfo info(int uid, String name) {
        PackageInfo info = new PackageInfo();
        info.packageName = name;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.uid = uid;
        info.requestedPermissions = new String[]{ServerConstants.PERMISSION};
        return info;
    }
}
