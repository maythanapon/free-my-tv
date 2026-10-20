package io.freemytv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only, optional system telemetry. Never substitutes app CPU usage for whole-TV usage. */
final class SystemStats {
    static final class Snapshot {
        final String cpu, ram, storage, temperature;
        final float cpuFraction, ramFraction, storageFraction;
        Snapshot(int cpu, float ram, long free, long total, float temperature) {
            this.cpu = cpu < 0 ? "—" : cpu + "%";
            this.ram = ram < 0 ? "—" : Math.round(ram * 100) + "%";
            storage = free < 0 ? "—" : String.format(Locale.US, "%.1f GB", free / 1e9);
            this.temperature = Float.isNaN(temperature) ? "—" : Math.round(temperature) + "°C";
            cpuFraction = cpu < 0 ? 0 : cpu / 100f;
            ramFraction = Math.max(0, ram);
            storageFraction = total > 0 ? free / (float) total : 0;
        }
    }

    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Pattern CPU_TEMP = Pattern.compile("Temperature\\{mValue=([-0-9.]+), mType=0, mName=CPU\\b");
    private boolean busy;
    private long lastTotal = -1, lastIdle;
    SystemStats(Context context) { this.context = context.getApplicationContext(); }

    void refresh(java.util.function.Consumer<Snapshot> done) {
        if (busy || worker.isShutdown()) return;
        busy = true;
        worker.execute(() -> {
            int cpu = -1;
            float ram = -1, temperature = Float.NaN;
            long free = -1, total = -1;
            try {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                context.getSystemService(ActivityManager.class).getMemoryInfo(info);
                if (info.totalMem > 0) ram = Math.max(0, Math.min(1, 1 - info.availMem / (float) info.totalMem));
                StatFs disk = new StatFs(Environment.getDataDirectory().getPath());
                free = disk.getAvailableBytes();
                total = disk.getTotalBytes();
            } catch (RuntimeException ignored) {}
            try (Adb adb = Adb.connect(context, 1000, null)) {
                String result = adb.shell("head -n 1 /proc/stat; dumpsys thermalservice");
                String[] fields = result.split("\\n", 2)[0].trim().split("\\s+");
                if (fields.length >= 9 && fields[0].equals("cpu")) {
                    long sum = 0;
                    // Guest counters are already included in user/nice; exclude them.
                    for (int i = 1; i <= 8; i++) sum += Long.parseLong(fields[i]);
                    long idle = Long.parseLong(fields[4]) + Long.parseLong(fields[5]);
                    if (lastTotal >= 0 && sum > lastTotal && idle >= lastIdle)
                        cpu = (int) Math.max(0, Math.min(100, Math.round(100.0 * (1 - (idle - lastIdle) / (double) (sum - lastTotal)))));
                    lastTotal = sum;
                    lastIdle = idle;
                }
                // Prefer the HAL's current value over the earlier cached temperature.
                Matcher match = CPU_TEMP.matcher(result);
                while (match.find()) temperature = Float.parseFloat(match.group(1));
                if (Float.isInfinite(temperature) || temperature < -20 || temperature > 150) temperature = Float.NaN;
            } catch (Exception ignored) { lastTotal = -1; }
            Snapshot snapshot = new Snapshot(cpu, ram, free, total, temperature);
            context.getMainExecutor().execute(() -> {
                busy = false;
                done.accept(snapshot);
            });
        });
    }

    void close() { worker.shutdownNow(); }
}
