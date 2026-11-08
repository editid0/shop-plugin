package dev.editid.simpleshop.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Records every transaction to a daily log file (off-thread) and keeps recent ones in memory. */
public final class TransactionLog {

    public enum Type { BUY, SELL, COMMAND }

    public record Entry(long time, UUID uuid, String player, Type type, String item, int amount, double total) {
    }

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final File folder;
    private final Logger logger;
    private final Supplier<Boolean> toFile;
    private final IntSupplier memorySize;
    private final Deque<Entry> recent = new ArrayDeque<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SimpleShop-TransactionLog");
        thread.setDaemon(true);
        return thread;
    });

    public TransactionLog(File folder, Logger logger, Supplier<Boolean> toFile, IntSupplier memorySize) {
        this.folder = folder;
        this.logger = logger;
        this.toFile = toFile;
        this.memorySize = memorySize;
    }

    public void record(UUID uuid, String player, Type type, String item, int amount, double total) {
        Entry entry = new Entry(System.currentTimeMillis(), uuid, player, type, item, amount, total);
        int max = memorySize.getAsInt();
        synchronized (recent) {
            recent.addFirst(entry);
            while (recent.size() > max) recent.removeLast();
        }
        if (toFile.get()) writer.execute(() -> write(entry));
    }

    private void write(Entry e) {
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.time()), ZoneId.systemDefault());
        String line = String.format(Locale.ROOT, "%s | %s (%s) | %s | %s x%d | %.2f%n",
                LINE_TIME.format(time), e.player(), e.uuid(), e.type(), e.item(), e.amount(), e.total());
        File file = new File(folder, "transactions-" + FILE_DATE.format(time) + ".log");
        try {
            folder.mkdirs();
            try (BufferedWriter out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write(line);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not write transaction log", ex);
        }
    }

    /** Newest first; {@code player} filters by name (case-insensitive) when not null. */
    public List<Entry> recent(String player, int limit) {
        List<Entry> result = new ArrayList<>();
        synchronized (recent) {
            Iterator<Entry> it = recent.iterator();
            while (it.hasNext() && result.size() < limit) {
                Entry e = it.next();
                if (player == null || e.player().equalsIgnoreCase(player)) result.add(e);
            }
        }
        return result;
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
