package com.vapez.smp.util;

import com.vapez.smp.VapezPlugin;
import org.bukkit.Bukkit;

public final class Tasks {

    private Tasks() {
    }

    public static void later(long ticks, Runnable run) {
        Bukkit.getScheduler().runTaskLater(VapezPlugin.get(), run, ticks);
    }

    public static void repeat(long delay, long period, Runnable run) {
        Bukkit.getScheduler().runTaskTimer(VapezPlugin.get(), run, delay, period);
    }

    public static void async(Runnable run) {
        Bukkit.getScheduler().runTaskAsynchronously(VapezPlugin.get(), run);
    }

    public static void sync(Runnable run) {
        if (Bukkit.isPrimaryThread()) {
            run.run();
        } else {
            Bukkit.getScheduler().runTask(VapezPlugin.get(), run);
        }
    }
}
