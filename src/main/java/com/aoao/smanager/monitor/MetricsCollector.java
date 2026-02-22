package com.aoao.smanager.monitor;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import java.util.List;
import java.util.Arrays;

public class MetricsCollector {
    private final SystemInfo si = new SystemInfo();
    private final HardwareAbstractionLayer hal = si.getHardware();
    private long[] prevCpuTicks = hal.getProcessor().getSystemCpuLoadTicks();
    private long prevDiskRead = 0;
    private long prevDiskWrite = 0;
    private long prevNetRecv = 0;
    private long prevNetSend = 0;
    private long prevTime = System.nanoTime();

    public MetricsSnapshot sample() {
        GlobalMemory mem = hal.getMemory();
        CentralProcessor cpu = hal.getProcessor();
        double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = cpu.getSystemCpuLoadTicks();

        List<oshi.hardware.HWDiskStore> disks = hal.getDiskStores();
        long read = 0;
        long write = 0;
        for (oshi.hardware.HWDiskStore d : disks) {
            read += d.getReadBytes();
            write += d.getWriteBytes();
        }
        List<NetworkIF> nifs = hal.getNetworkIFs();
        long recv = 0;
        long send = 0;
        for (NetworkIF nif : nifs) {
            try {
                nif.updateAttributes();
            } catch (Exception ignored) {
            }
            recv += nif.getBytesRecv();
            send += nif.getBytesSent();
        }
        long now = System.nanoTime();
        double dt = Math.max(1e-6, (now - prevTime) / 1_000_000_000.0);
        double diskReadRate = prevDiskRead == 0 ? 0 : (read - prevDiskRead) / dt;
        double diskWriteRate = prevDiskWrite == 0 ? 0 : (write - prevDiskWrite) / dt;
        double netDownRate = prevNetRecv == 0 ? 0 : (recv - prevNetRecv) / dt;
        double netUpRate = prevNetSend == 0 ? 0 : (send - prevNetSend) / dt;
        prevDiskRead = read;
        prevDiskWrite = write;
        prevNetRecv = recv;
        prevNetSend = send;
        prevTime = now;

        FileSystem fs = si.getOperatingSystem().getFileSystem();
        long totalSpace = 0;
        long freeSpace = 0;
        for (OSFileStore s : fs.getFileStores()) {
            totalSpace += s.getTotalSpace();
            freeSpace += s.getUsableSpace();
        }

        MetricsSnapshot s = new MetricsSnapshot();
        s.memoryTotalBytes = mem.getTotal();
        s.memoryFreeBytes = mem.getAvailable();
        s.memoryUsedBytes = s.memoryTotalBytes - s.memoryFreeBytes;
        s.cpuUsage = cpuLoad;
        double[] la = cpu.getSystemLoadAverage(3);
        s.systemLoadAverage = la != null ? Arrays.stream(la).map(v -> Double.isNaN(v) ? 0 : v).toArray() : new double[]{0, 0, 0};
        s.diskTotalBytes = totalSpace;
        s.diskFreeBytes = freeSpace;
        s.diskReadBytesPerSec = diskReadRate;
        s.diskWriteBytesPerSec = diskWriteRate;
        s.netDownBytesPerSec = netDownRate;
        s.netUpBytesPerSec = netUpRate;
        s.timestamp = System.currentTimeMillis();
        return s;
    }
}
