package com.androditto;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.function.Consumer;
import java.util.function.Function;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.io.FileOutputStream;

/** Minimal Ditto peer sender for CF_UNICODETEXT clips. */
final class DittoProtocol {
    private static final int PORT = 23443;
    private static final int HEADER_SIZE = 140;
    private static final int ROUND_COUNT = 100000;
    private static final SecureRandom RANDOM = new SecureRandom();

    static final class FilePart {
        final String name; final long size; final File file;
        FilePart(String name, File file) { this.name = name; this.file = file; this.size = file.length(); }
    }

    static void sendFileClip(String host, String password, String localIp, java.util.List<String> names) throws Exception {
        StringBuilder paths = new StringBuilder();
        for (String name : names) paths.append("C:\\DittoLink\\").append(name.replace('\\', '_').replace('/', '_')).append('\0');
        paths.append('\0');
        byte[] pathBytes = paths.toString().getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer drop = ByteBuffer.allocate(20 + pathBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        drop.putInt(20).putInt(0).putInt(0).putInt(0).putInt(1).put(pathBytes);
        try (Socket socket = new Socket(InetAddress.getByName(host), PORT)) {
            OutputStream out = socket.getOutputStream();
            out.write(encrypt(sendInfo(0, "Ditto Link", localIp, "Files", 0, PORT), password));
            byte[] encryptedDrop = encrypt(drop.array(), password);
            out.write(encrypt(sendInfo(2, "", "", "CF_HDROP", encryptedDrop.length), password));
            out.write(encryptedDrop);
            out.write(encrypt(sendInfo(3, "", "", "CF_HDROP", encryptedDrop.length), password));
            out.write(encrypt(sendInfo(4, "", "", "", 0), password));
            out.write(encrypt(sendInfo(5, "", "", "", 0), password));
            out.flush();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(java.util.Locale.ROOT, "%02x", b)); return s.toString();
    }

    static void sendText(String host, String password, String text, String localIp) throws Exception {
        sendText(host, password, text, localIp, PORT);
    }

    static void sendText(String host, String password, String text, String localIp, int port) throws Exception {
        byte[] clip = (text + "\0").getBytes(StandardCharsets.UTF_16LE);
        try (Socket socket = new Socket(InetAddress.getByName(host), port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write(encrypt(sendInfo(0, "Ditto Link", localIp, "Text", 0), password));
            byte[] encryptedClip = encrypt(clip, password);
            out.write(encrypt(sendInfo(2, "", "", "CF_UNICODETEXT", encryptedClip.length), password));
            out.write(encryptedClip);
            out.write(encrypt(sendInfo(3, "", "", "CF_UNICODETEXT", encryptedClip.length), password));
            out.write(encrypt(sendInfo(4, "", "", "", 0), password));
            out.write(encrypt(sendInfo(5, "", "", "", 0), password));
            out.flush();
        }
    }

    static void listen(String password, Consumer<String> onText, Function<java.util.List<String>, java.util.List<FilePart>> requestedFiles) throws Exception {
        listen(password, PORT, onText, requestedFiles, null, files -> {}, () -> {});
    }

    static void listen(String password, int port, Consumer<String> onText, Function<java.util.List<String>, java.util.List<FilePart>> requestedFiles) throws Exception {
        listen(password, port, onText, requestedFiles, null, files -> {}, () -> {});
    }

    static void listen(String password, Consumer<String> onText, Function<java.util.List<String>, java.util.List<FilePart>> requestedFiles,
                       File receiveDirectory, Consumer<java.util.List<File>> onFiles) throws Exception {
        listen(password, PORT, onText, requestedFiles, receiveDirectory, onFiles, () -> {});
    }

    static void listen(String password, Consumer<String> onText, Function<java.util.List<String>, java.util.List<FilePart>> requestedFiles,
                       File receiveDirectory, Consumer<java.util.List<File>> onFiles, Runnable onListening) throws Exception {
        listen(password, PORT, onText, requestedFiles, receiveDirectory, onFiles, onListening);
    }

    private static void listen(String password, int port, Consumer<String> onText,
                               Function<java.util.List<String>, java.util.List<FilePart>> requestedFiles,
                               File receiveDirectory, Consumer<java.util.List<File>> onFiles, Runnable onListening) throws Exception {
        try (ServerSocket server = new ServerSocket(port)) {
            onListening.run();
            server.setSoTimeout(1000);
            while (!Thread.currentThread().isInterrupted()) {
                Socket accepted;
                try { accepted = server.accept(); }
                catch (java.net.SocketTimeoutException retry) { continue; }
                try (Socket socket = accepted) {
                    socket.setSoTimeout(30000);
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    byte[] unicodeText = null;
                    byte[] dropFiles = null;
                    byte[] remoteDrop = null;
                    while (true) {
                        byte[] infoBytes = decrypt(readExact(in, 508), password);
                        ByteBuffer info = ByteBuffer.wrap(infoBytes).order(ByteOrder.LITTLE_ENDIAN);
                        info.position(4);
                        int type = info.getInt();
                        if (type == 2) {
                            info.position(info.position() + 4 + 20 + 16);
                            String format = readString(info, 250);
                            info.position(info.position() + 2);
                            int length = info.getInt();
                            byte[] data = decrypt(readExact(in, length), password);
                            if ("CF_UNICODETEXT".equals(format)) unicodeText = data;
                            if ("CF_HDROP".equals(format)) dropFiles = data;
                            if ("Ditto Remote CF_HDROP".equals(format)) remoteDrop = data;
                        } else if (type == 4) {
                            if (unicodeText != null) {
                                String value = new String(unicodeText, StandardCharsets.UTF_16LE).replace("\0", "");
                                onText.accept(value);
                            }
                            unicodeText = null;
                        } else if (type == 6 && dropFiles != null) {
                            java.util.List<String> paths = parseDropFiles(dropFiles);
                            java.util.List<FilePart> files = requestedFiles.apply(paths);
                            sendFileData(out, password, files);
                            break;
                        } else if (type == 5) {
                            if (dropFiles != null && remoteDrop != null && receiveDirectory != null) {
                                java.util.List<File> received = requestRemoteFiles(password, dropFiles, remoteDrop, receiveDirectory);
                                onFiles.accept(received);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private static byte[] readExact(InputStream in, int count) throws Exception {
        if (count < 0 || count > 32 * 1024 * 1024) throw new IllegalArgumentException("Invalid Ditto data size");
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int n = in.read(data, offset, count - offset);
            if (n < 0) throw new java.io.EOFException("Connection closed during clip");
            offset += n;
        }
        return data;
    }

    private static String readString(ByteBuffer b, int size) {
        byte[] bytes = new byte[size]; b.get(bytes);
        int end = 0; while (end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, 0, end, StandardCharsets.UTF_8).trim();
    }

    private static java.util.List<String> parseDropFiles(byte[] bytes) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        if (bytes.length < 22) return paths;
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int offset = b.getInt(0); boolean wide = b.getInt(16) != 0;
        if (offset < 20 || offset >= bytes.length || !wide) return paths;
        StringBuilder item = new StringBuilder();
        for (int i = offset; i + 1 < bytes.length; i += 2) {
            char c = (char)((bytes[i] & 255) | ((bytes[i + 1] & 255) << 8));
            if (c == 0) { if (item.length() == 0) break; paths.add(item.toString()); item.setLength(0); }
            else item.append(c);
        }
        return paths;
    }

    private static java.util.List<File> requestRemoteFiles(String password, byte[] drop, byte[] remote, File directory) throws Exception {
        if (remote.length < 44) throw new IllegalArgumentException("Invalid Ditto remote file details");
        int ipEnd = 0; while (ipEnd < 25 && remote[ipEnd] != 0) ipEnd++;
        String host = new String(remote, 0, ipEnd, StandardCharsets.UTF_8).trim();
        int port = ByteBuffer.wrap(remote).order(ByteOrder.LITTLE_ENDIAN).getShort(42) & 0xffff;
        if (port == 0) port = PORT;
        java.util.List<File> received = new java.util.ArrayList<>();
        directory.mkdirs();
        try (Socket socket = new Socket(InetAddress.getByName(host), port)) {
            socket.setSoTimeout(30000);
            InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream();
            out.write(encrypt(sendInfo(0, "Ditto Link", "", "Request files", 0), password));
            byte[] encryptedDrop = encrypt(drop, password);
            out.write(encrypt(sendInfo(2, "", "", "CF_HDROP", encryptedDrop.length), password));
            out.write(encryptedDrop);
            out.write(encrypt(sendInfo(3, "", "", "CF_HDROP", encryptedDrop.length), password));
            out.write(encrypt(sendInfo(4, "", "", "", 0), password));
            out.write(encrypt(sendInfo(6, "", "", "", 0), password)); out.flush();

            while (true) {
                Info info = readInfo(in, password);
                if (info.type == 4) break;
                if (info.type != 2) continue;
                String fileName = new File(info.description.replace('\\', '/')).getName();
                if (fileName.isEmpty()) fileName = "received-file";
                File target = new File(directory, fileName);
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                try (FileOutputStream file = new FileOutputStream(target)) {
                    byte[] block = new byte[65536]; int remaining = info.parameter1;
                    while (remaining > 0) {
                        int n = Math.min(block.length, remaining); byte[] bytes = readExact(in, n);
                        file.write(bytes); md5.update(bytes); remaining -= n;
                    }
                }
                Info end = readInfo(in, password);
                if (end.type != 3) throw new java.io.IOException("Missing file transfer end marker");
                received.add(target);
            }
        }
        return received;
    }

    private static final class Info {
        int type, parameter1; String description;
    }

    private static Info readInfo(InputStream in, String password) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(decrypt(readExact(in, 508), password)).order(ByteOrder.LITTLE_ENDIAN);
        Info info = new Info(); info.type = b.getInt(4);
        byte[] desc = new byte[250]; System.arraycopy(b.array(), 48, desc, 0, desc.length);
        int end = 0; while (end < desc.length && desc[end] != 0) end++;
        info.description = new String(desc, 0, end, StandardCharsets.UTF_8).trim();
        info.parameter1 = b.getInt(300);
        return info;
    }

    private static void sendFileData(OutputStream out, String password, java.util.List<FilePart> files) throws Exception {
        out.write(encrypt(sendInfo(0, "", "", "", files.size()), password));
        for (FilePart file : files) {
            out.write(encrypt(sendInfo(2, "", "", file.name, (int)file.size), password));
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            try (InputStream input = new FileInputStream(file.file)) {
                byte[] buf = new byte[65536]; int n; long remaining = file.size;
                while (remaining > 0 && (n = input.read(buf, 0, (int)Math.min(buf.length, remaining))) != -1) {
                    out.write(buf, 0, n); md5.update(buf, 0, n); remaining -= n;
                }
                if (remaining != 0) throw new java.io.EOFException("File changed while sending");
            }
            byte[] end = sendInfo(3, "", "", "", 0);
            byte[] digest = hex(md5.digest()).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(digest, 0, end, 308, digest.length);
            out.write(encrypt(end, password));
        }
        out.write(encrypt(sendInfo(4, "", "", "", 0), password)); out.flush();
    }

    private static byte[] sendInfo(int type, String computer, String ip, String desc, int parameter) throws Exception {
        return sendInfo(type, computer, ip, desc, parameter, 0);
    }

    private static byte[] sendInfo(int type, String computer, String ip, String desc, int parameter, int respondPort) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(360).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(360).putInt(type).putInt(1);
        putString(b, ip, 20); putString(b, computer, 16); putString(b, desc, 250);
        b.position(b.position() + 2);
        b.putInt(parameter).putInt(0); b.position(b.position() + 32);
        b.put((byte)1).put((byte)0).putShort((short)respondPort); b.position(b.position() + 16);
        return b.array();
    }

    private static void putString(ByteBuffer b, String value, int size) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(bytes.length, size - 1);
        b.put(bytes, 0, n); b.position(b.position() + size - n);
    }

    private static byte[] encrypt(byte[] input, String password) throws Exception {
        byte[] masterSeed = random(16), iv = random(16), seed2 = random(32);
        byte[] masterKey = sha256(password.getBytes(StandardCharsets.UTF_8));
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(seed2, "AES"));
        for (int i = 0; i < ROUND_COUNT; i++) masterKey = ecb.doFinal(masterKey);
        byte[] finalKey = sha256(join(masterSeed, sha256(masterKey)));
        byte[] contentHash = sha256(input);
        ByteBuffer h = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        h.position(32); h.putInt(0x139C5AFE).putInt(0xBF3562DA);
        h.put(masterSeed).put(iv).put(contentHash).put(seed2).putInt(ROUND_COUNT);
        byte[] header = h.array();
        byte[] headerHash = sha256(java.util.Arrays.copyOfRange(header, 32, HEADER_SIZE));
        System.arraycopy(headerHash, 0, header, 0, 32);
        Cipher cbc = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cbc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(finalKey, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cbc.doFinal(input);
        return join(header, encrypted);
    }

    private static byte[] decrypt(byte[] input, String password) throws Exception {
        if (input.length <= HEADER_SIZE) throw new IllegalArgumentException("Invalid encrypted Ditto message");
        byte[] header = java.util.Arrays.copyOfRange(input, 0, HEADER_SIZE);
        byte[] storedHash = java.util.Arrays.copyOfRange(header, 0, 32);
        byte[] calculatedHash = sha256(java.util.Arrays.copyOfRange(header, 32, HEADER_SIZE));
        if (!MessageDigest.isEqual(storedHash, calculatedHash)) throw new SecurityException("Invalid Ditto header");
        ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (h.getInt(32) != 0x139C5AFE || h.getInt(36) != 0xBF3562DA) throw new SecurityException("Invalid Ditto signature");
        byte[] seed = new byte[16], iv = new byte[16], contentHash = new byte[32], seed2 = new byte[32];
        h.position(40); h.get(seed); h.get(iv); h.get(contentHash); h.get(seed2);
        int rounds = h.getInt();
        if (rounds < 1 || rounds > 1000000) throw new SecurityException("Invalid Ditto key rounds");
        byte[] transformed = sha256(transform(seed2, sha256(password.getBytes(StandardCharsets.UTF_8)), rounds));
        byte[] key = sha256(join(seed, transformed));
        Cipher cbc = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cbc.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] clear = cbc.doFinal(input, HEADER_SIZE, input.length - HEADER_SIZE);
        if (!MessageDigest.isEqual(contentHash, sha256(clear))) throw new SecurityException("Invalid Ditto content hash");
        return clear;
    }

    private static byte[] transform(byte[] seed, byte[] key, int rounds) throws Exception {
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(seed, "AES"));
        for (int i = 0; i < rounds; i++) key = ecb.doFinal(key);
        return key;
    }

    private static byte[] random(int n) { byte[] b = new byte[n]; RANDOM.nextBytes(b); return b; }
    private static byte[] sha256(byte[] b) throws Exception { return MessageDigest.getInstance("SHA-256").digest(b); }
    private static byte[] join(byte[] a, byte[] b) {
        byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length); return out;
    }
    private DittoProtocol() {}
}
