package moe.shizuku.common.util;

import android.os.Process;
import android.system.Os;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Private launch tickets shared by the server and its app_process starter.
 * They survive a server SIGKILL; neither client names nor a scan of matching
 * process names is evidence that we launched a process. Old, unjournaled builds
 * are deliberately left alone. Root and shell keep separate, owner-only stores.
 */
public final class UserServiceRegistry {
    private static final String TAG = "UserServiceRegistry";
    public static final String RECORD_ARGUMENT = "--fork-service-record=";

    private UserServiceRegistry() {}

    private static File directory(String manager) throws Exception {
        if (!manager.matches("[A-Za-z0-9_.]+")) throw new IOException("Invalid manager id");
        File directory = new File("/data/local/tmp/" + manager + "-user-services-" + Process.myUid());
        if (directory.mkdir()) Os.chmod(directory.getPath(), 0700);
        StructStat stat = Os.lstat(directory.getPath());
        if (!OsConstants.S_ISDIR(stat.st_mode) || stat.st_uid != Process.myUid()
                || (stat.st_mode & 0777) != 0700) {
            throw new IOException("Unsafe user-service directory");
        }
        return directory;
    }

    private static void checkFile(File file) throws Exception {
        StructStat stat = Os.lstat(file.getPath());
        if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_uid != Process.myUid()
                || (stat.st_mode & 0077) != 0) throw new IOException("Unsafe launch ticket");
    }

    private static String read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 65536) throw new IOException("Oversized proc/ticket file");
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static long start(int pid) throws IOException {
        return UserServiceIdentity.startTicks(read(new File("/proc/" + pid + "/stat")));
    }

    private static String boot() throws IOException {
        return read(new File("/proc/sys/kernel/random/boot_id")).trim();
    }

    private static boolean ownerAlive(Properties ticket) throws Exception {
        int pid = Integer.parseInt(ticket.getProperty("serverPid"));
        try {
            Os.stat("/proc/" + pid);
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.ENOENT || e.errno == OsConstants.ESRCH) return false;
            throw e; // Inaccessible is not evidence that the owner is dead.
        }
        return start(pid) == Long.parseLong(ticket.getProperty("serverStart"));
    }

    private static Properties load(File file) throws Exception {
        checkFile(file);
        Properties ticket = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            ticket.load(input);
        }
        return ticket;
    }

    private static void save(File file, Properties ticket) throws Exception {
        // Directory is private to this UID; never follow a caller supplied link.
        if (file.exists()) checkFile(file);
        File temporary = new File(file.getPath() + ".tmp");
        if (!temporary.createNewFile()) throw new IOException("Ticket write in progress");
        try {
            Os.chmod(temporary.getPath(), 0600);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                ticket.store(output, "Owned app_process user service");
                output.getFD().sync();
            }
            if (!temporary.renameTo(file)) throw new IOException("Cannot publish launch ticket");
        } finally {
            temporary.delete();
        }
    }

    /** Called before starting the shell; failure prevents an untracked launch. */
    public static File prepare(String manager, String token, String packageName) throws Exception {
        if (!token.matches("[A-Za-z0-9-]+")) throw new IOException("Invalid service token");
        File file = new File(directory(manager), token + ".properties");
        Properties ticket = new Properties();
        ticket.setProperty("manager", manager);
        ticket.setProperty("token", token);
        ticket.setProperty("package", packageName);
        ticket.setProperty("boot", boot());
        ticket.setProperty("uid", Integer.toString(Process.myUid()));
        ticket.setProperty("serverPid", Integer.toString(Process.myPid()));
        ticket.setProperty("serverStart", Long.toString(start(Process.myPid())));
        save(file, ticket);
        return file;
    }

    /** Register before loading any client code (which may block or crash). */
    public static void register(String path, String manager, String token, String processName) throws Exception {
        File file = new File(path);
        if (!directory(manager).equals(file.getParentFile())
                || !file.getName().equals(token + ".properties")) throw new IOException("Invalid ticket path");
        Properties ticket = load(file);
        if (!manager.equals(ticket.getProperty("manager"))
                || !token.equals(ticket.getProperty("token"))
                || !boot().equals(ticket.getProperty("boot")) || !ownerAlive(ticket)) {
            throw new IOException("Originating server is gone");
        }
        ticket.setProperty("pid", Integer.toString(Process.myPid()));
        ticket.setProperty("start", Long.toString(start(Process.myPid())));
        // UserService.create sets this exact name before entering client code.
        ticket.setProperty("name", processName);
        save(file, ticket);
    }

    private static boolean matches(File file, Properties ticket, String manager, String boot) throws Exception {
        checkFile(file);
        int pid = Integer.parseInt(ticket.getProperty("pid"));
        String proc = "/proc/" + pid;
        String name = read(new File(proc + "/cmdline")).split("\u0000", 2)[0];
        return UserServiceIdentity.isOrphan(ticket.getProperty("manager"), manager,
                ticket.getProperty("boot"), boot,
                Integer.parseInt(ticket.getProperty("uid")), Os.stat(proc).st_uid,
                pid, pid, Long.parseLong(ticket.getProperty("start")), start(pid),
                ticket.getProperty("name"), name, Os.readlink(proc + "/exe"), ownerAlive(ticket));
    }

    /** Runs before the new server accepts clients. Errors never justify a kill. */
    public static void cleanup(String manager) {
        try {
            String currentBoot = boot();
            File[] files = directory(manager).listFiles((dir, name) -> name.endsWith(".properties"));
            if (files == null) return;
            for (File file : files) {
                try {
                    Properties ticket = load(file);
                    if (currentBoot.equals(ticket.getProperty("boot")) && ownerAlive(ticket)) continue;
                    if (ticket.containsKey("pid") && currentBoot.equals(ticket.getProperty("boot"))) {
                        // Re-read identity immediately before signalling to reject PID reuse.
                        if (matches(file, ticket, manager, currentBoot)
                                && matches(file, ticket, manager, currentBoot)) {
                            int pid = Integer.parseInt(ticket.getProperty("pid"));
                            Os.kill(pid, OsConstants.SIGKILL);
                            Log.i(TAG, "Terminated orphan user service " + pid);
                        } else {
                            // The child may still be entering UserService.create; retain proof
                            // for the next start instead of guessing from its nice-name.
                            continue;
                        }
                    }
                    file.delete();
                } catch (Exception e) {
                    Log.w(TAG, "Skipping unverified launch ticket " + file.getName(), e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "User-service cleanup unavailable", e);
        }
    }
}
