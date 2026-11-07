package rikka.shizuku.server.util;

import static org.junit.Assert.*;

import org.junit.Test;
import moe.shizuku.common.util.UserServiceIdentity;

public class UserServiceIdentityTest {
    private static boolean orphan(String manager, String boot, int uid, int pid,
                                  long start, String name, String exe, boolean ownerAlive) {
        return UserServiceIdentity.isOrphan("gg.pressf.access", manager, "boot-a", boot,
                2000, uid, 123, pid, 456, start, "client:world_io", name, exe, ownerAlive);
    }

    @Test public void identifiesOnlyJournaledAppProcessFromDeadServer() {
        assertTrue(orphan("gg.pressf.access", "boot-a", 2000, 123, 456,
                "client:world_io", "/system/bin/app_process64", false));
        assertTrue(orphan("gg.pressf.access", "boot-a", 2000, 123, 456,
                "client:world_io", "/system/bin/app_process32", false));
    }

    @Test public void rejectsOtherManagerBootUidPidAndReusedPid() {
        assertFalse(orphan("other.manager", "boot-a", 2000, 123, 456, "client:world_io", "/system/bin/app_process64", false));
        assertFalse(orphan("gg.pressf.access", "boot-b", 2000, 123, 456, "client:world_io", "/system/bin/app_process64", false));
        assertFalse(orphan("gg.pressf.access", "boot-a", 10000, 123, 456, "client:world_io", "/system/bin/app_process64", false));
        assertFalse(orphan("gg.pressf.access", "boot-a", 2000, 789, 456, "client:world_io", "/system/bin/app_process64", false));
        assertFalse(orphan("gg.pressf.access", "boot-a", 2000, 123, 999, "client:world_io", "/system/bin/app_process64", false));
    }

    @Test public void rejectsSameNameOrdinaryAppAndLiveOwner() {
        assertFalse(orphan("gg.pressf.access", "boot-a", 2000, 123, 456, "other:world_io", "/system/bin/app_process64", false));
        assertFalse(orphan("gg.pressf.access", "boot-a", 2000, 123, 456, "client:world_io", "/system/bin/sleep", false));
        assertFalse(orphan("gg.pressf.access", "boot-a", 2000, 123, 456, "client:world_io", "/system/bin/app_process64", true));
        assertFalse(orphan("gg.pressf.access", "boot-a", 2000, 123, 0, "client:world_io", "/system/bin/app_process64", false));
    }

    @Test public void parsesStartTimeWhenCommHasSpacesAndParentheses() {
        assertEquals(987654, UserServiceIdentity.startTicks(
                "123 (client (world) io) S 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 987654 20"));
        assertThrows(IllegalArgumentException.class, () -> UserServiceIdentity.startTicks("invalid"));
    }
}
