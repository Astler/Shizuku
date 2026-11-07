package moe.shizuku.common.util;

/** Pure identification rules: a process name alone never proves ownership. */
public final class UserServiceIdentity {
    private UserServiceIdentity() {}

    // /proc/pid/stat field 22; comm can itself contain spaces and parentheses.
    public static long startTicks(String stat) {
        int end = stat.lastIndexOf(')');
        if (end < 0) throw new IllegalArgumentException("Invalid proc stat");
        String[] fields = stat.substring(end + 1).trim().split("\\s+");
        return Long.parseLong(fields[19]);
    }

    public static boolean isOrphan(String recordedManager, String manager,
                                   String recordedBoot, String boot,
                                   int recordedUid, int uid,
                                   int recordedPid, int pid,
                                   long recordedStart, long start,
                                   String recordedName, String name,
                                   String executable, boolean ownerAlive) {
        return !ownerAlive && manager.equals(recordedManager) && boot.equals(recordedBoot)
                && recordedUid == uid && recordedPid > 0 && recordedPid == pid
                && recordedStart > 0 && recordedStart == start
                && recordedName.equals(name)
                && ("/system/bin/app_process".equals(executable)
                    || "/system/bin/app_process32".equals(executable)
                    || "/system/bin/app_process64".equals(executable));
    }
}
