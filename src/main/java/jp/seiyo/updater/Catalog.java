package jp.seiyo.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class Catalog {
    static final String GAME_VERSION = "1.19.2";
    static final int MAX_FILE = 128 * 1024 * 1024;
    static final int MAX_JSON = 2 * 1024 * 1024;
    static final String CONFIG_NAME = "seiyo-updater.json";
    private static final byte[] MAGIC = {'S', 'Y', 'U', '1'};
    private static final Base64.Decoder BASE64 = Base64.getDecoder();

    record Config(URI manifest, String publicKey, byte[] contentKey) {
        static Config load(Path path) throws Exception {
            try (InputStream input = Files.newInputStream(path)) {
                JsonObject json = parse(read(input, 16384));
                URI manifest = https(URI.create(required(json, "manifestUrl", 2048)));
                String key = required(json, "publicKey", 512);
                byte[] content = json.has("contentKey") ? BASE64.decode(required(json, "contentKey", 128)) : null;
                if (content != null && content.length != 32) throw new IOException("旧形式の配布鍵が不正です。");
                return new Config(manifest, key, content);
            }
        }
    }

    record Entry(String id, String name, String type, String version, String file,
                 URI uri, boolean encrypted, String hash, int size, boolean required,
                 List<String> dependencies, String notes) {
        String folder() {
            return type.equals("mod") ? "mods" : "resourcepacks";
        }
    }

    record Release(long revision, String fingerprint, Map<String, Entry> entries, byte[] key) {}

    static Release fetch(Config config) throws Exception {
        JsonObject envelope = parse(download(config.manifest, MAX_JSON));
        String encoding = required(envelope, "format", 32);
        boolean packed = encoding.equals("seiyo-packed-v1");
        if (!packed && !encoding.equals("seiyo-signed-v1")) {
            throw new IOException("更新カタログの形式が異なります。");
        }
        byte[] payload = BASE64.decode(required(envelope, "payload", MAX_JSON));
        byte[] signed = BASE64.decode(required(envelope, "signature", 256));
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(
            new X509EncodedKeySpec(BASE64.decode(config.publicKey))));
        verifier.update(payload);
        if (signed.length != 64 || !verifier.verify(signed)) {
            throw new IOException("更新カタログの署名が一致しません。");
        }
        if (packed) {
            PackedArchive.mask(payload, payload.length, 0);
            try (var inflated = new InflaterInputStream(new ByteArrayInputStream(payload))) {
                payload = read(inflated, MAX_JSON);
            }
        }

        JsonObject json = parse(payload);
        if (!"seiyo-catalog-v1".equals(required(json, "format", 32))
                || !GAME_VERSION.equals(required(json, "minecraft", 16))
                || !"fabric".equals(required(json, "loader", 16))) {
            throw new IOException("このカタログは星陽の Fabric 1.19.2 用ではありません。");
        }
        String revisionText = json.get("revision").getAsString();
        if (!revisionText.matches("[1-9][0-9]{0,14}")) {
            throw new IOException("配布番号が不正です。");
        }
        JsonArray items = json.getAsJsonArray("entries");
        if (items.size() > 250) throw new IOException("登録上限は250件です。");
        Map<String, Entry> entries = new LinkedHashMap<>();
        Set<String> paths = new HashSet<>();
        for (JsonElement item : items) {
            Entry entry = entry(item.getAsJsonObject(), config.manifest);
            if (packed && entry.encrypted) throw new IOException("新形式のカタログには通常の JAR／ZIP の URL を指定してください。");
            if (entry.encrypted && config.contentKey == null) throw new IOException("旧カタログを取得するには旧形式の設定ファイルが必要です。");
            if (entries.putIfAbsent(entry.id, entry) != null
                    || !paths.add(entry.folder() + "/" + entry.file.toLowerCase(Locale.ROOT))) {
                throw new IOException("ID または保存名が重複しています: " + entry.id);
            }
        }
        Set<String> checked = new HashSet<>();
        for (String id : entries.keySet()) checkDependencies(id, entries, new HashSet<>(), checked);
        return new Release(Long.parseLong(revisionText), hex(payload),
            java.util.Collections.unmodifiableMap(entries), config.contentKey);
    }

    private static Entry entry(JsonObject json, URI base) throws IOException {
        String id = required(json, "id", 64);
        String type = required(json, "type", 16);
        String file = required(json, "file", 160);
        String hash = required(json, "sha256", 64);
        String mode = required(json, "mode", 20);
        String size = json.get("size").getAsString();
        if (!id.matches("[a-z][a-z0-9_-]{1,63}")
                || !UpdateWorker.validFilename(file)
                || !(type.equals("mod") && file.endsWith(".jar")
                    || type.equals("pack") && file.endsWith(".zip"))
                || !hash.matches("[0-9a-f]{64}") || !size.matches("[1-9][0-9]{0,8}")
                || Integer.parseInt(size) > MAX_FILE
                || !(mode.equals("aes-gcm") || mode.equals("plain"))) {
            throw new IOException("登録内容が不正です: " + id);
        }
        URI asset = https(base.resolve(required(json, "source", 2048)));
        List<String> dependencies = new ArrayList<>();
        for (JsonElement dep : json.getAsJsonArray("requires")) {
            String dependency = dep.getAsString();
            if (dependencies.contains(dependency) || dependencies.size() >= 250) {
                throw new IOException("依存関係が不正です: " + id);
            }
            dependencies.add(dependency);
        }
        if (!json.getAsJsonPrimitive("required").isBoolean()) {
            throw new IOException("必須フラグが不正です: " + id);
        }
        String notes = json.has("notes") ? json.get("notes").getAsString() : "";
        if (notes.length() > 2000) throw new IOException("更新メモが長すぎます: " + id);
        return new Entry(id, required(json, "name", 100), type, required(json, "version", 100),
            file, asset, mode.equals("aes-gcm"), hash, Integer.parseInt(size),
            json.get("required").getAsBoolean(), List.copyOf(dependencies), notes);
    }

    private static void checkDependencies(String id, Map<String, Entry> entries,
            Set<String> visiting, Set<String> checked) throws IOException {
        if (checked.contains(id)) return;
        Entry current = entries.get(id);
        if (current == null) throw new IOException("カタログにない依存先です: " + id);
        if (!visiting.add(id)) throw new IOException("依存関係が循環しています: " + id);
        for (String dep : current.dependencies) checkDependencies(dep, entries, visiting, checked);
        visiting.remove(id);
        checked.add(id);
    }

    static byte[] artifact(byte[] key, Entry entry) throws Exception {
        byte[] data = download(entry.uri, entry.size + (entry.encrypted ? 32 : 0));
        if (entry.encrypted) {
            if (data.length != entry.size + 32 || !java.util.Arrays.equals(MAGIC,
                    java.util.Arrays.copyOf(data, MAGIC.length))) {
                throw new IOException("暗号化ファイルの形式が異なります: " + entry.name);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, data, 4, 12));
            cipher.updateAAD(MAGIC);
            try {
                data = cipher.doFinal(data, 16, data.length - 16);
            } catch (javax.crypto.AEADBadTagException exception) {
                throw new IOException("配布鍵が異なるか、ファイルが破損しています: " + entry.name, exception);
            }
        }
        if (data.length != entry.size || !hex(data).equals(entry.hash)) {
            throw new IOException("ファイルの SHA-256 またはサイズが一致しません: " + entry.name);
        }
        return data;
    }

    static JsonObject parse(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static String required(JsonObject json, String key, int max) throws IOException {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isString()) {
            throw new IOException("文字列の項目がありません: " + key);
        }
        String value = json.get(key).getAsString();
        if (value.isBlank() || value.length() > max) throw new IOException("項目が不正です: " + key);
        return value;
    }

    static byte[] read(InputStream stream, int maximum) throws IOException {
        byte[] bytes = stream.readNBytes(maximum + 1);
        if (bytes.length > maximum) throw new IOException("ファイルがサイズ上限を超えています。");
        return bytes;
    }

    private static String hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static URI https(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IOException("HTTPS の配布 URL を指定してください。");
        }
        return uri;
    }

    private static byte[] download(URI location, int limit) throws IOException {
        URI current = location;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "SeiyoUpdater/1.0");
            try {
                int status = connection.getResponseCode();
                if (status == 200) {
                    if (connection.getContentLengthLong() > limit) {
                        throw new IOException("配布ファイルがサイズ上限を超えています。");
                    }
                    try (InputStream body = connection.getInputStream()) {
                        return read(body, limit);
                    }
                }
                String redirect = connection.getHeaderField("Location");
                if (!Set.of(301, 302, 303, 307, 308).contains(status) || redirect == null) {
                    throw new IOException("配布先で HTTP " + status + " が返されました。");
                }
                current = https(current.resolve(redirect));
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("配布先の URL が何度も転送されました。");
    }
}
