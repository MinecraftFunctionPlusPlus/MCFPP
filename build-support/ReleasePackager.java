import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarFile;
import java.util.zip.*;
import lu.luz.jzopfli.ZopfliH;
import lu.luz.jzopfli.Zopfli_lib;
import org.apache.commons.compress.archivers.zip.*;
import org.tukaani.xz.*;

/** Build-only, lossless release packing. No additional decoder is needed by Java. */
public final class ReleasePackager {
    private static final String ROOT = "META-INF/mcfpp/stdlib/";
    private static final String INDEX = ROOT + "index";
    private static final int MAGIC = 0x4d435250;
    private static final String ENCODER = "zopfli-0.0.4-30-unlimited-jdk-all-v1";
    private record Item(String name, byte[] bytes, int mode) {}
    private record Descriptor(String name, int size, int[] chunks) {}
    private record Encoded(int method, byte[] bytes) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("mode input output cache");
        Path input = Path.of(args[1]), output = Path.of(args[2]), cache = Path.of(args[3], ENCODER);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectories(cache);
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".release-", ".tmp");
        try {
            if (args[0].equals("xz")) xz(input, temporary);
            else if (args[0].equals("jar") || args[0].equals("zip") || args[0].equals("browser-jar")) {
                List<Item> original = readZip(input);
                boolean browser = args[0].equals("browser-jar");
                List<Item> items = browser ? compactRuntime(java25View(input), true)
                    : args[0].equals("jar") ? compactRuntime(java25View(input)) : original;
                if (browser) writeBrowserZip(items, temporary);
                else writeZip(items, temporary, cache);
                verifyZip(items, temporary);
                if (args[0].equals("jar") || browser) verifyLibrary(original, temporary);
            } else throw new IllegalArgumentException("Unknown mode: " + args[0]);
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            System.out.printf(Locale.ROOT, "%s: %,d -> %,d bytes%n", output.getFileName(), Files.size(input), Files.size(output));
        } finally { Files.deleteIfExists(temporary); }
    }

    private static List<Item> readZip(Path path) throws IOException {
        List<Item> items = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try (var zip = org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(path).get()) {
            var entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (!names.add(entry.getName())) throw new IOException("Duplicate ZIP entry: " + entry.getName());
                try (var stream = zip.getInputStream(entry)) {
                    items.add(new Item(entry.getName(), stream.readAllBytes(), entry.getUnixMode()));
                }
            }
        }
        items.sort(Comparator.comparing((Item it) -> !it.name.equals("META-INF/MANIFEST.MF")).thenComparing(Item::name));
        return items;
    }

    private static List<Item> java25View(Path path) throws IOException {
        List<Item> items = new ArrayList<>();
        // The compiler and its MNI toolchain require JDK 25. Let the JDK itself
        // choose each active class/resource instead of shipping inactive variants.
        try (var jar = new JarFile(path.toFile(), true, java.util.zip.ZipFile.OPEN_READ, Runtime.Version.parse("25"));
             var entries = jar.versionedStream()) {
            for (var entry : entries.toList()) {
                if (entry.isDirectory()) continue;
                try (var stream = jar.getInputStream(entry)) {
                    items.add(new Item(entry.getName(), stream.readAllBytes(), 0100644));
                }
            }
        }
        items.sort(Comparator.comparing((Item it) -> !it.name.equals("META-INF/MANIFEST.MF")).thenComparing(Item::name));
        System.out.printf("JDK 25 runtime view: %d active entries%n", items.size());
        return items;
    }

    private static List<Item> compactRuntime(List<Item> input) throws Exception {
        return compactRuntime(input, false);
    }

    /** Browser packaging shares the lossless stdlib layout but retains every active runtime
     * class/resource. Dependency minimization belongs to the original compiler build. */
    private static List<Item> compactRuntime(List<Item> input, boolean preserveRuntimeEntries) throws Exception {
        Set<String> classes = new HashSet<>();
        for (Item item : input) if (item.name.endsWith(".class")) classes.add(item.name);
        boolean icuUsed = classes.stream().anyMatch(name -> name.startsWith("com/ibm/icu/"));
        List<Item> output = new ArrayList<>();
        List<byte[]> chunks = new ArrayList<>();
        Map<String, Integer> chunkIds = new HashMap<>();
        List<Descriptor> descriptors = new ArrayList<>();
        long originalBytes = 0, unusedIcuBytes = 0;
        for (Item item : input) {
            if (item.name.startsWith("datapack/")) {
                originalBytes += item.bytes.length;
                var ids = new ArrayList<Integer>();
                int start = 0;
                long rolling = 0;
                for (int end = 0; end < item.bytes.length; end++) {
                    rolling = (rolling << 1) + GEAR[item.bytes[end] & 255];
                    int length = end + 1 - start;
                    if (end + 1 == item.bytes.length || length >= 65536 || length >= 4096 && (rolling & 16383) == 0) {
                        byte[] chunk = Arrays.copyOfRange(item.bytes, start, end + 1);
                        String hash = hash(chunk);
                        Integer id = chunkIds.get(hash);
                        if (id == null) {
                            id = chunks.size(); chunkIds.put(hash, id); chunks.add(chunk);
                        } else if (!Arrays.equals(chunks.get(id), chunk)) throw new IOException("Chunk hash collision");
                        ids.add(id); start = end + 1; rolling = 0;
                    }
                }
                descriptors.add(new Descriptor(item.name, item.bytes.length, ids.stream().mapToInt(Integer::intValue).toArray()));
            } else if (!preserveRuntimeEntries && !icuUsed && item.name.startsWith("com/ibm/icu/")) {
                unusedIcuBytes += item.bytes.length;
            } else if (!preserveRuntimeEntries && item.name.startsWith("META-INF/services/")) {
                StringBuilder providers = new StringBuilder();
                for (String line : new String(item.bytes, StandardCharsets.UTF_8).split("\\R")) {
                    String provider = line.split("#", 2)[0].trim();
                    if (!provider.isEmpty() && classes.contains(provider.replace('.', '/') + ".class")) providers.append(provider).append('\n');
                }
                if (!providers.isEmpty()) output.add(new Item(item.name, providers.toString().getBytes(StandardCharsets.UTF_8), item.mode));
            } else output.add(item);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream index = new DataOutputStream(buffer)) {
            index.writeInt(MAGIC); index.writeInt(1); index.writeInt(descriptors.size());
            for (Descriptor descriptor : descriptors) {
                index.writeUTF(descriptor.name); index.writeInt(descriptor.size); index.writeInt(descriptor.chunks.length);
                for (int id : descriptor.chunks) index.writeInt(id);
            }
        }
        output.add(new Item(INDEX, buffer.toByteArray(), 0100644));
        for (int id = 0; id < chunks.size(); id++) output.add(new Item(ROOT + id, chunks.get(id), 0100644));
        long uniqueBytes = chunks.stream().mapToLong(chunk -> chunk.length).sum();
        System.out.printf(Locale.ROOT, "Standard library: %,d files, %,d -> %,d unique bytes in %,d chunks; unused ICU resources: %,d bytes%n",
            descriptors.size(), originalBytes, uniqueBytes, chunks.size(), unusedIcuBytes);
        return output;
    }

    private static final long[] GEAR = new long[256];
    static {
        Random random = new Random(0x4d43465050L);
        for (int i = 0; i < GEAR.length; i++) GEAR[i] = random.nextLong();
    }

    /** Normal DEFLATE keeps local prepare runs short; the release task keeps its exhaustive
     * compressor. Both paths verify all entries and original standard-library bytes. */
    private static void writeBrowserZip(List<Item> items, Path output) throws IOException {
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(output))) {
            for (Item item : items) {
                var entry = new ZipEntry(item.name);
                entry.setTimeLocal(LocalDateTime.of(1980, 2, 1, 0, 0));
                zip.putNextEntry(entry);
                zip.write(item.bytes);
                zip.closeEntry();
            }
        }
    }

    private static void writeZip(List<Item> items, Path output, Path cache) throws Exception {
        int workers = Math.min(4, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        Map<String, Future<Encoded>> shared = new HashMap<>();
        List<Future<Encoded>> pending = new ArrayList<>();
        try {
            for (Item item : items) {
                String digest = hash(item.bytes);
                pending.add(shared.computeIfAbsent(digest, key -> executor.submit(() -> encode(item.bytes, cache.resolve(key)))));
            }
            try (var zip = new ZipArchiveOutputStream(output)) {
                zip.setUseZip64(Zip64Mode.AsNeeded);
                for (int i = 0; i < items.size(); i++) {
                    Item item = items.get(i);
                    Encoded encoded = pending.get(i).get();
                    ZipArchiveEntry entry = new ZipArchiveEntry(item.name);
                    entry.setTimeLocal(LocalDateTime.of(1980, 2, 1, 0, 0));
                    entry.setUnixMode(item.mode == 0 ? 0100644 : item.mode);
                    entry.setMethod(encoded.method);
                    entry.setSize(item.bytes.length); entry.setCompressedSize(encoded.bytes.length);
                    CRC32 crc = new CRC32(); crc.update(item.bytes); entry.setCrc(crc.getValue());
                    zip.addRawArchiveEntry(entry, new ByteArrayInputStream(encoded.bytes));
                    if ((i + 1) % 1000 == 0 || i + 1 == items.size()) {
                        System.out.printf("Compressed %d/%d entries%n", i + 1, items.size());
                    }
                }
            }
        } finally { executor.shutdownNow(); }
    }

    private static Encoded encode(byte[] input, Path cache) throws Exception {
        if (Files.isRegularFile(cache)) {
            byte[] cached = Files.readAllBytes(cache);
            if (cached.length > 0) {
                Encoded encoded = new Encoded(cached[0], Arrays.copyOfRange(cached, 1, cached.length));
                try { verifyEncoding(input, encoded); return encoded; }
                catch (IOException invalid) { Files.delete(cache); }
            }
        }
        Encoded best = new Encoded(ZipEntry.STORED, input);
        if (input.length > 0) {
            for (int strategy : new int[]{Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY}) {
                for (int level = 1; level <= 9; level++) {
                    Deflater deflater = new Deflater(level, true);
                    try {
                        deflater.setStrategy(strategy); deflater.setInput(input); deflater.finish();
                        ByteArrayOutputStream stream = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
                        while (!deflater.finished()) { int count = deflater.deflate(buffer); stream.write(buffer, 0, count); }
                        byte[] bytes = stream.toByteArray();
                        if (bytes.length < best.bytes.length) best = new Encoded(ZipEntry.DEFLATED, bytes);
                    } finally { deflater.end(); }
                }
            }
            ZopfliH.ZopfliOptions options = new ZopfliH.ZopfliOptions();
            options.numiterations = 30; options.blocksplittingmax = 0;
            byte[][] result = { new byte[0] }; int[] size = { 0 };
            Zopfli_lib.ZopfliCompress(options, ZopfliH.ZopfliFormat.ZOPFLI_FORMAT_DEFLATE, input, input.length, result, size);
            if (size[0] < best.bytes.length) best = new Encoded(ZipEntry.DEFLATED, Arrays.copyOf(result[0], size[0]));
        }
        verifyEncoding(input, best);
        Path temporary = Files.createTempFile(cache.getParent(), ".encoding-", ".tmp");
        try (var stream = Files.newOutputStream(temporary)) { stream.write(best.method); stream.write(best.bytes); }
        try { Files.move(temporary, cache, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
        return best;
    }

    private static void verifyEncoding(byte[] input, Encoded encoded) throws IOException {
        if (encoded.method == ZipEntry.STORED) {
            if (!Arrays.equals(input, encoded.bytes)) throw new IOException("Invalid stored data");
        } else if (encoded.method == ZipEntry.DEFLATED) {
            Inflater inflater = new Inflater(true);
            try (var stream = new InflaterInputStream(new ByteArrayInputStream(encoded.bytes), inflater)) {
                if (!Arrays.equals(input, stream.readAllBytes()) || !inflater.finished()) throw new IOException("Invalid DEFLATE round trip");
            } finally { inflater.end(); }
        } else throw new IOException("Unknown encoding: " + encoded.method);
    }

    private static void verifyZip(List<Item> items, Path path) throws IOException {
        try (var zip = new java.util.zip.ZipFile(path.toFile())) {
            if (zip.size() != items.size()) throw new IOException("ZIP entry count mismatch");
            for (Item item : items) {
                var entry = zip.getEntry(item.name);
                if (entry == null) throw new IOException("Missing ZIP entry: " + item.name);
                try (var stream = zip.getInputStream(entry)) {
                    if (!Arrays.equals(item.bytes, stream.readAllBytes())) throw new IOException("ZIP content mismatch: " + item.name);
                }
            }
        }
    }

    private static void verifyLibrary(List<Item> original, Path path) throws IOException {
        Map<String, byte[]> expected = new HashMap<>();
        for (Item item : original) if (item.name.startsWith("datapack/")) expected.put(item.name, item.bytes);
        try (var zip = new java.util.zip.ZipFile(path.toFile());
             var index = new DataInputStream(zip.getInputStream(zip.getEntry(INDEX)))) {
            if (index.readInt() != MAGIC || index.readInt() != 1 || index.readInt() != expected.size()) throw new IOException("Invalid library index");
            Map<Integer, byte[]> chunks = new HashMap<>();
            int checked = 0;
            while (index.available() > 0) {
                String name = index.readUTF(); int length = index.readInt(); int count = index.readInt();
                ByteArrayOutputStream content = new ByteArrayOutputStream(length);
                for (int i = 0; i < count; i++) {
                    int id = index.readInt(); byte[] chunk = chunks.get(id);
                    if (chunk == null) {
                        try (var stream = zip.getInputStream(zip.getEntry(ROOT + id))) { chunk = stream.readAllBytes(); }
                        chunks.put(id, chunk);
                    }
                    content.write(chunk);
                }
                if (!Arrays.equals(expected.remove(name), content.toByteArray())) throw new IOException("Library content mismatch: " + name);
                checked++;
            }
            if (!expected.isEmpty()) throw new IOException("Missing library files");
            System.out.printf("Verified all %d standard-library files byte for byte%n", checked);
        }
    }

    private static void xz(Path input, Path output) throws Exception {
        LZMA2Options options = new LZMA2Options(9);
        options.setNiceLen(273); options.setDepthLimit(512);
        try (var source = Files.newInputStream(input); var stream = new XZOutputStream(Files.newOutputStream(output), options)) {
            source.transferTo(stream);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var source = Files.newInputStream(input)) { source.transferTo(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest)); }
        byte[] expected = digest.digest();
        try (var stream = new XZInputStream(Files.newInputStream(output))) { stream.transferTo(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest)); }
        if (!Arrays.equals(expected, digest.digest())) throw new IOException("XZ content mismatch");
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
