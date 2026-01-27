package co.aospa.glyph.Utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

public class OGGUtils {

    public static void main(String[] args) throws Exception {
        String customData = null;
        String authorData = null;

        try (InputStream in = new BufferedInputStream(
                new FileInputStream(args[0]))) {

            metadata = read(in);

        } catch (Exception e) {
            System.out.println("File not found or could not be opened");
        }

        try {
            customData = readCompressedField(glyphDotsField);
            authorData = readCompressedField(glyphFrameDataField);
            System.out.println("CUSTOM1 (DOTS)=" + customData);
            System.out.println("CSV ANIM=" + authorData);
        } catch (Exception e) {
            System.out.println("Could not get fields!");
            System.exit(0);
        }

        try {
            String device = validateAnimation(metadata);
            if (device != null) {
                System.out.println("Device:" + device);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }

    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};
    private static final byte[] OPUS_HEAD = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
    private static final byte[] OPUS_TAGS = {'O', 'p', 'u', 's', 'T', 'a', 'g', 's'};

    private static final String composerField = "COMPOSER";
    private static final String glyphDotsField = "CUSTOM1";
    private static final String glyphFrameDataField = "AUTHOR";
    private static final String columnCountField = "CUSTOM2";

    private static Map<String, String> metadata;

    public static Map<String, String> read(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(in));
        ByteArrayOutputStream packetBuf = new ByteArrayOutputStream();

        boolean seenOpusHead = false;

        while (true) {
            // ---- Ogg page header ----
            byte[] header = new byte[27];
            dis.readFully(header);

            for (int i = 0; i < 4; i++) {
                if (header[i] != OGG_MAGIC[i])
                    throw new IOException("Not an Ogg stream");
            }

            int pageSegments = header[26] & 0xFF;
            byte[] lacing = new byte[pageSegments];
            dis.readFully(lacing);

            // ---- Page data ----
            for (int i = 0; i < pageSegments; i++) {
                int len = lacing[i] & 0xFF;
                byte[] seg = new byte[len];
                dis.readFully(seg);
                packetBuf.write(seg);

                // packet boundary
                if (len < 255) {
                    byte[] packet = packetBuf.toByteArray();
                    packetBuf.reset();

                    if (!seenOpusHead && isHeader(packet, OPUS_HEAD)) {
                        seenOpusHead = true;
                    } else if (seenOpusHead && isHeader(packet, OPUS_TAGS)) {
                        return parseComments(packet, OPUS_TAGS.length);
                    }
                }
            }
        }
    }

    private static boolean isHeader(byte[] packet, byte[] magic) {
        if (packet.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (packet[i] != magic[i]) return false;
        }
        return true;
    }

    private static Map<String, String> parseComments(byte[] packet, int offset)
            throws IOException {

        DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(packet, offset, packet.length - offset));

        Map<String, String> map = new LinkedHashMap<>();

        int vendorLen = readLE32(in);
        byte[] vendor = new byte[vendorLen];
        in.readFully(vendor);

        int count = readLE32(in);
        for (int i = 0; i < count; i++) {
            int len = readLE32(in);
            byte[] data = new byte[len];
            in.readFully(data);

            String entry = new String(data, StandardCharsets.UTF_8);
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;

            String key = entry.substring(0, eq).toUpperCase(Locale.US);
            String value = entry.substring(eq + 1);

            map.put(key, value);

        }
        return map;
    }

    private static int readLE32(DataInputStream in) throws IOException {
        return (in.readUnsignedByte()) |
                (in.readUnsignedByte() << 8) |
                (in.readUnsignedByte() << 16) |
                (in.readUnsignedByte() << 24);
    }


    public static String readField(String fieldName) throws IllegalArgumentException {
        String values = metadata.get(fieldName);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Missing tag: " + fieldName);
        }
        return values;
    }

    public static String readCompressedField(String fieldName) throws IllegalArgumentException {
        String base64Data;
        String fieldData;
        byte[] decodedData;

        try {
            base64Data = readField(fieldName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read field: " + fieldName);
        }

        try {
            decodedData = parseBase64(base64Data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Field is not base64 data!: " + fieldName);
        }

        try {
            fieldData = new String(inflateZlib(decodedData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to uncompress: " + fieldName);
        }

        return fieldData;
    }

    public static byte[] parseBase64(String data) throws IllegalArgumentException {
        byte[] decoded;
        String sanitizedString = data.replaceAll("\\s+", "");
        try {
            decoded = Base64.getMimeDecoder().decode(sanitizedString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Data is not valid Base64", e);
        }
        return decoded;
    }

    public static String validateAnimation(Map<String, String> metadata) throws Exception {
        String metaColumnCount = null;
        String animation = null;
        int parsedColumnCount = 0;
        String composer = null;

        try {
            animation = readCompressedField(glyphFrameDataField);
        } catch (Exception e) {
            throw new IllegalArgumentException("No animation data found!");
        }

        try {
            metaColumnCount = readField(columnCountField);
        } catch (Exception e) {
            System.out.println("Could not locate CUSTOM2 field, parsing COMPOSER instead!");
        }

        try {
            composer = readField(composerField);
        } catch (Exception e) {
            System.out.println("Could not get COMPOSER field!");
        }

        parsedColumnCount = countCsvColumns(animation);

        if (parsedColumnCount != 0) {

        } else {
            throw new IllegalArgumentException("Animation length is invalid!");
        }


        if (metaColumnCount != null) {
            switch (metaColumnCount) {
                case "5cols" -> {
                    return "Phone (1)";
                }
                case "26cols" -> {
                    return "Phone (2a)";
                }
                case "33cols" -> {
                    return "Phone (2)";
                }
                case "36cols" -> {
                    return "Phone (3a)";
                }
            }
        } else if (composer != null) {
            if (composer.contains("Spacewar")) {
                return "Phone (1)";
            } else if (composer.contains("Pong")) {
                return "Phone (2)";
            } else if (composer.contains("Pacman")) {
                return "Phone (2a)";
            } else if (composer.contains("Asteroids")) {
                return "Phone (3a)";
            }
        } else if (animation != null) {
            switch (parsedColumnCount) {
                case 5 -> {
                    return "Phone (1)";
                }
                case 26 -> {
                    return "Phone (2a)";
                }
                case 33 -> {
                    return "Phone (2)";
                }
                case 36 -> {
                    return "Phone (3a)";
                }
            }
        }
        return null;
    }

    private static byte[] inflateZlib(byte[] data)
            throws IOException {

        Inflater inflater = new Inflater(false);

        try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
             InflaterInputStream zin = new InflaterInputStream(bin, inflater);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buf = new byte[4096];
            int n;
            while ((n = zin.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();

        } catch (ZipException e) {
            throw new ZipException(
                    "Tag is not valid zlib-compressed data");
        }
    }

    public static int countCsvColumns(String csv) {
        if (csv == null || csv.isEmpty()) {
            return 0;
        }

        String[] lines = csv.split("\\R");

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            int columns = 1;
            boolean inQuotes = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (c == ',' && !inQuotes) {
                    columns++;
                }
            }
            return columns;
        }

        return 0;
    }
}

