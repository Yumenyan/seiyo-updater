package jp.seiyo.updater;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Updater {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeiyoUpdater");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SeiyoUpdater");
        thread.setDaemon(true);
        return thread;
    });
    private static CompletableFuture<Snapshot> current;

    record Installed(Path path, String version, String archiveHash) {}
    record Row(Catalog.Entry entry, Installed installed, String installedHash, boolean matched, String problem) {}
    record Snapshot(Path root, byte[] key, long revision, Map<String, Row> rows) {}
    record Plan(Set<String> included, Set<String> locked, List<Row> changes) {}

    static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(Catalog.CONFIG_NAME);
    }

    static synchronized CompletableFuture<Snapshot> check(boolean refresh) {
        if (current == null || (refresh && current.isDone())) {
            current = CompletableFuture.supplyAsync(() -> {
                try {
                    if (!Files.isRegularFile(configPath())) {
                        throw new IOException("配布元から受け取った設定ファイルを config に入れてください。");
                    }
                    return inspect(Catalog.Config.load(configPath()),
                        FabricLoader.getInstance().getGameDir().toRealPath());
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }, IO);
        }
        return current;
    }

    private static Snapshot inspect(Catalog.Config config, Path root) throws Exception {
        Catalog.Release release = Catalog.fetch(config);
        Path home = UpdateWorker.directory(root.resolve(UpdateWorker.HOME));
        Map<String, List<Installed>> mods = new HashMap<>();
        Path modFolder = UpdateWorker.directory(root.resolve("mods"));
        Path packFolder = UpdateWorker.directory(root.resolve("resourcepacks"));
        try (var paths = Files.list(modFolder)) {
            for (Path file : paths.sorted().toList()) {
                if (!Files.isRegularFile(file, UpdateWorker.NO_LINKS)) continue;
                String filename = file.getFileName().toString();
                if (PackedArchive.isPacked(file)) {
                    String id = filename.substring(0, filename.length() - PackedArchive.EXTENSION.length());
                    var header = PackedArchive.header(file);
                    mods.computeIfAbsent(id, key -> new ArrayList<>())
                        .add(new Installed(file, header.version(), header.hash()));
                    continue;
                }
                if (!filename.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                JsonObject metadata;
                try {
                    metadata = zipJson(file, "fabric.mod.json");
                } catch (ZipException | RuntimeException ignored) {
                    continue;
                }
                if (metadata == null || !metadata.has("id") || !metadata.has("version")) continue;
                String id = metadata.get("id").getAsString();
                mods.computeIfAbsent(id, key -> new ArrayList<>())
                    .add(new Installed(file, metadata.get("version").getAsString(), ""));
            }
        }

        Map<String, Row> rows = new LinkedHashMap<>();
        for (Catalog.Entry entry : release.entries().values()) {
            List<Installed> candidates = mods.getOrDefault(entry.id(), List.of());
            Installed installed = null;
            String problem = "";
            Path target = (entry.type().equals("mod") ? modFolder : packFolder).resolve(storedName(entry));
            if (entry.type().equals("mod")) {
                if (candidates.size() > 1) problem = "同じ Mod ID のファイルが複数あります。";
                if (candidates.size() == 1) installed = candidates.get(0);
                if (candidates.isEmpty() && FabricLoader.getInstance().isModLoaded(entry.id())) {
                    problem = "この Mod は別の場所または他の JAR の内部から読み込まれています。";
                }
                if (entry.id().equals("seiyo_updater")) {
                    problem = "アップデーター本体とブートストラップは手動で更新してください。";
                } else if (System.getProperty(RuntimeFiles.AGENT_ROOT) == null) {
                    problem = "Mod の更新には星陽ブートストラップの設定が必要です。";
                }
            } else {
                Path zip = packFolder.resolve(entry.file());
                if (Files.isRegularFile(target, UpdateWorker.NO_LINKS)) {
                    var header = PackedArchive.header(target);
                    installed = new Installed(target, header.version(), header.hash());
                } else if (Files.isRegularFile(zip, UpdateWorker.NO_LINKS)) {
                    installed = new Installed(zip, "ZIP", "");
                }
                if (UpdateWorker.exists(zip) && (installed == null || !installed.path().equals(zip))) {
                    problem = "保存先の名前が別のファイルと重複しています。";
                }
            }
            if (UpdateWorker.exists(target) && (installed == null || !installed.path().equals(target))) {
                problem = "保存先の名前が別のファイルと重複しています。";
            }

            String hash = "";
            if (installed != null) {
                String filename = installed.path().getFileName().toString();
                if (!UpdateWorker.validFilename(filename) && !UpdateWorker.validOldFilename(filename)) {
                    problem = "導入済みファイルの名前を確認してください。";
                }
                hash = UpdateWorker.hash(installed.path());
            }
            boolean matched = installed != null && installed.archiveHash().equals(entry.hash())
                && installed.path().equals(target) && installed.version().equals(entry.version());
            rows.put(entry.id(), new Row(entry, installed, hash, matched, problem));
        }
        rememberRevision(home, config, release);
        return new Snapshot(root, release.key(), release.revision(), Collections.unmodifiableMap(rows));
    }

    static String storedName(Catalog.Entry entry) {
        return PackedArchive.storedName(entry.type(), entry.id(), entry.file());
    }

    private static void rememberRevision(Path home, Catalog.Config config, Catalog.Release release) throws Exception {
        byte[] channel = java.security.MessageDigest.getInstance("SHA-256").digest(
            (config.manifest() + "\n" + config.publicKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path version = home.resolve("revision-" + HexFormat.of().formatHex(channel));
        String value = release.revision() + " " + release.fingerprint();
        if (Files.isRegularFile(version, UpdateWorker.NO_LINKS)) {
            String[] previous = Files.readString(version).trim().split(" ", 2);
            long latest = Long.parseLong(previous[0]);
            if (latest > release.revision() || (latest == release.revision() && !previous[1].equals(release.fingerprint()))) {
                throw new IOException("以前に確認したカタログより古いか、同じ配布番号が再利用されています。");
            }
            if (latest == release.revision()) return;
        }
        Path temporary = Files.createTempFile(home, "revision-", ".tmp");
        try {
            Files.writeString(temporary, value);
            Files.move(temporary, version, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Set<String> defaults(Snapshot snapshot) {
        Set<String> selected = new HashSet<>();
        for (Row row : snapshot.rows.values()) {
            if (!row.matched && row.installed != null && row.problem.isEmpty()) selected.add(row.entry.id());
        }
        return selected;
    }

    static Plan plan(Snapshot snapshot, Set<String> selected) {
        Set<String> included = new HashSet<>();
        Set<String> locked = new HashSet<>();
        for (Row row : snapshot.rows.values()) {
            if (row.entry.required()) {
                locked.add(row.entry.id());
                include(snapshot, row.entry.id(), included, locked);
            }
        }
        for (String id : selected) include(snapshot, id, included, locked);
        List<Row> changes = snapshot.rows.values().stream()
            .filter(row -> included.contains(row.entry.id()) && !row.matched).toList();
        return new Plan(Set.copyOf(included), Set.copyOf(locked), changes);
    }

    private static void include(Snapshot snapshot, String id, Set<String> included, Set<String> locked) {
        if (!included.add(id)) return;
        for (String dep : snapshot.rows.get(id).entry.dependencies()) {
            locked.add(dep);
            include(snapshot, dep, included, locked);
        }
    }

    static CompletableFuture<Void> stage(Snapshot snapshot, Plan plan, Consumer<String> progress) {
        return CompletableFuture.runAsync(() -> {
            try {
                stageAndSchedule(snapshot, plan, progress);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, IO);
    }

    private static void stageAndSchedule(Snapshot snapshot, Plan plan, Consumer<String> progress) throws Exception {
        Path home = UpdateWorker.directory(snapshot.root.resolve(UpdateWorker.HOME));
        Path pending = home.resolve(UpdateWorker.PENDING);
        if (UpdateWorker.exists(pending)) throw new IOException("適用待ちの更新があります。");
        if (plan.changes.isEmpty()) throw new IOException("選択された更新がありません。");
        for (Row row : plan.changes) {
            if (!row.problem.isEmpty()) throw new IOException(row.entry.name() + ": " + row.problem);
        }
        Path job = Files.createDirectory(home.resolve("job-" + UUID.randomUUID()));
        boolean launched = false;
        boolean registered = false;
        try {
            List<UpdateWorker.Change> changes = new ArrayList<>();
            Set<String> replacing = new HashSet<>();
            for (Row row : plan.changes) replacing.add(row.entry.id());
            for (int i = 0; i < plan.changes.size(); i++) {
                Row row = plan.changes.get(i);
                Catalog.Entry entry = row.entry;
                progress.accept("取得中 " + (i + 1) + "/" + plan.changes.size() + " · " + entry.name());
                Path staged = job.resolve(i + ".stage");
                Path raw = job.resolve("verify.download");
                byte[] data = Catalog.artifact(snapshot.key, entry);
                try {
                    Files.write(raw, data, StandardOpenOption.CREATE_NEW);
                    verifyArchive(raw, entry, snapshot, replacing);
                } finally {
                    Files.deleteIfExists(raw);
                }
                PackedArchive.write(staged, entry.file(), entry.version(), entry.hash(), data);
                String previous = row.installed == null ? "" : row.installed.path().getFileName().toString();
                changes.add(new UpdateWorker.Change(entry.folder(), previous, storedName(entry), row.installedHash,
                    UpdateWorker.hash(staged)));
            }
            UpdateWorker.save(job, changes);

            Path worker = job.resolve("seiyo-update-worker.jar");
            try (InputStream input = Updater.class.getResourceAsStream("/seiyo-update-worker.jar")) {
                if (input == null) throw new IOException("更新用プログラムが JAR にありません。");
                Files.copy(input, worker);
            }

            Files.writeString(job.resolve("pending.tmp"), job.getFileName().toString(), StandardOpenOption.CREATE_NEW);
            Files.move(job.resolve("pending.tmp"), pending, StandardCopyOption.ATOMIC_MOVE);
            registered = true;

            Path bin = Path.of(System.getProperty("java.home"), "bin");
            Path java = bin.resolve("java");
            if (Files.isRegularFile(bin.resolve("javaw.exe"))) java = bin.resolve("javaw.exe");
            else if (Files.isRegularFile(bin.resolve("java.exe"))) java = bin.resolve("java.exe");
            new ProcessBuilder(java.toString(), "-cp", worker.toString(), UpdateWorker.class.getName(),
                snapshot.root.toString(), Long.toString(ProcessHandle.current().pid()))
                .directory(snapshot.root.toFile()).redirectErrorStream(true)
                .redirectOutput(job.resolve("worker.log").toFile()).start();
            launched = true;
        } finally {
            if (!launched) {
                if (registered) Files.deleteIfExists(pending);
                try (var files = Files.list(job)) {
                    for (Path file : files.toList()) Files.deleteIfExists(file);
                }
                Files.delete(job);
            }
        }
    }

    private static void verifyArchive(Path path, Catalog.Entry entry, Snapshot snapshot,
            Set<String> replacing) throws Exception {
        if (entry.type().equals("mod")) {
            JsonObject data = zipJson(path, "fabric.mod.json");
            if (data == null || !entry.id().equals(Catalog.required(data, "id", 64))
                    || !entry.version().equals(Catalog.required(data, "version", 100))
                    || (data.has("environment") && "server".equals(data.get("environment").getAsString()))) {
                throw new IOException("Fabric Mod の ID、バージョン、対応環境を確認してください: " + entry.name());
            }
            JsonObject dependencies = data.getAsJsonObject("depends");
            if (dependencies != null) {
                for (String id : List.of("minecraft", "java", "fabricloader")) {
                    var dependency = FabricLoader.getInstance().getModContainer(id);
                    if (!dependencies.has(id) || dependency.isEmpty()) continue;
                    if (!matches(dependencies.get(id), dependency.get().getMetadata().getVersion())) {
                        throw new IOException(entry.name() + " が現在の " + id + " に対応していません。");
                    }
                }
                for (var dependency : dependencies.entrySet()) {
                    String id = dependency.getKey();
                    Row row = snapshot.rows.get(id);
                    if (row == null || !row.entry.type().equals("mod")) continue;
                    String version = replacing.contains(id) ? row.entry.version() :
                        row.installed == null ? null : row.installed.version();
                    if (version == null || !matches(dependency.getValue(), Version.parse(version))) {
                        throw new IOException(entry.name() + " に対応する " + row.entry.name()
                            + " が選ばれていません。選択内容と配布元の依存設定を確認してください。");
                    }
                }
            }
        } else {
            JsonObject data = zipJson(path, "pack.mcmeta");
            if (data == null || !data.has("pack") || !data.getAsJsonObject("pack").has("pack_format")
                    || !data.getAsJsonObject("pack").getAsJsonPrimitive("pack_format").isNumber()) {
                throw new IOException("ZIP の直下に有効な pack.mcmeta が必要です: " + entry.name());
            }
        }
    }

    private static boolean matches(JsonElement value, Version installed) throws Exception {
        if (!value.isJsonArray()) return VersionPredicate.parse(value.getAsString()).test(installed);
        for (JsonElement alternative : value.getAsJsonArray()) {
            if (VersionPredicate.parse(alternative.getAsString()).test(installed)) return true;
        }
        return false;
    }

    private static JsonObject zipJson(Path path, String name) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) return null;
            try (InputStream source = zip.getInputStream(entry)) {
                return Catalog.parse(Catalog.read(source, Catalog.MAX_JSON));
            }
        }
    }

    static String error(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        LOGGER.warn("Seiyo update operation failed", error);
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private Updater() {}
}
