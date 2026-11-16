package dev.veryue.vuequests.gametest;

import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeamFailureCapture {
    private static final Map<Path, CrashReport> reports = new ConcurrentHashMap<>();
    private static final Map<Path, Boolean> enabled = new ConcurrentHashMap<>();

    static void enable(Path directory) { enabled.put(directory, true); }
    static CrashReport report(Path directory) { return reports.get(directory); }
    static void disable(Path directory) { enabled.remove(directory); reports.remove(directory); }

    public static boolean capture(CrashReport report) {
        String details = report.getFriendlyReport(ReportType.CRASH);
        for (Path directory : enabled.keySet()) {
            if (details.contains("VUEQuests persistence failure") && details.contains(directory.toString())) {
                reports.putIfAbsent(directory, report);
                return true;
            }
        }
        return false;
    }
}
