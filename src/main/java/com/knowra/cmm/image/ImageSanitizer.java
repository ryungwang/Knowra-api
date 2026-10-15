package com.knowra.cmm.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * 올린 사진에서 <b>위치정보(EXIF GPS)와 기기 정보를 지우고</b>, 너무 큰 사진은 줄인다.
 *
 * <p>휴대폰 사진에는 찍은 곳의 GPS 좌표가 들어 있다. 지우지 않으면 사진을 올린 사람의 집 위치가 누구에게나
 * 내려간다. 형식마다 방법이 다르다:
 * <ul>
 *   <li><b>JPEG</b> — 다시 그려 저장한다(메타데이터가 통째로 빠진다). 돌려 찍은 사진은 EXIF 의 방향값으로만
 *       세워져 있으므로 <b>먼저 그 방향대로 돌려 놓고</b> 지운다 — 안 그러면 사진이 옆으로 눕는다.
 *       긴 변이 {@link #MAX_SIDE} 보다 크면 줄인다(원본 그대로면 피드가 무겁다).</li>
 *   <li><b>PNG</b> — 다시 그리지 않고 eXIf·텍스트 덩어리만 뺀다(무손실 그대로).</li>
 *   <li><b>WebP</b> — EXIF·XMP 덩어리만 뺀다. 자바 기본으로는 WebP 를 그릴 수 없다.</li>
 *   <li><b>GIF</b> — 위치정보를 담는 자리가 없다. 움직이는 그림이 깨지지 않게 그대로 둔다.</li>
 * </ul>
 * 읽을 수 없는 파일은 거절한다(확장자만 이미지인 다른 파일).
 */
public final class ImageSanitizer {

    /** 긴 변 상한(px) — 레티나 화면 글 폭의 두 배쯤 */
    public static final int MAX_SIDE = 2560;
    /** 디코딩 폭탄 방지 — 작은 파일이 수억 화소로 풀려 메모리를 다 먹는 걸 막는다 */
    static final long MAX_PIXELS = 50_000_000L;
    private static final float JPEG_QUALITY = 0.88f;

    private ImageSanitizer() {}

    public static byte[] clean(byte[] in, String ext) throws IOException {
        return switch (ext) {
            case "jpg", "jpeg" -> jpeg(in);
            case "png" -> png(in);
            case "webp" -> webp(in);
            default -> in;
        };
    }

    // ── JPEG ───────────────────────────────────────────────────────────────
    static byte[] jpeg(byte[] in) throws IOException {
        int orientation = exifOrientation(in);
        BufferedImage img = read(in);
        img = orient(img, orientation);
        img = shrink(img);
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {   // 알파·CMYK·회색조를 JPEG 이 받는 모양으로
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, java.awt.Color.WHITE, null);
            g.dispose();
            img = rgb;
        }
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            w.setOutput(ios);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(JPEG_QUALITY);
            w.write(null, new IIOImage(img, null, null), p);   // 메타데이터 null — EXIF 를 싣지 않는다
        } finally {
            w.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] in) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(in))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) throw new IOException("이미지를 읽을 수 없습니다.");
            ImageReader r = it.next();
            try {
                r.setInput(iis, true, true);
                if ((long) r.getWidth(0) * r.getHeight(0) > MAX_PIXELS) throw new IOException("이미지가 너무 큽니다.");
                BufferedImage img = r.read(0);
                if (img == null) throw new IOException("이미지를 읽을 수 없습니다.");
                return img;
            } finally {
                r.dispose();
            }
        }
    }

    static BufferedImage shrink(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= MAX_SIDE) return img;
        double k = (double) MAX_SIDE / longSide;
        int nw = Math.max(1, (int) Math.round(w * k)), nh = Math.max(1, (int) Math.round(h * k));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, nw, nh, java.awt.Color.WHITE, null);
        g.dispose();
        return out;
    }

    /** EXIF 방향값(1~8)대로 돌리거나 뒤집는다. 1·모르는 값이면 그대로 */
    static BufferedImage orient(BufferedImage img, int o) {
        if (o < 2 || o > 8) return img;
        int w = img.getWidth(), h = img.getHeight();
        boolean swap = o >= 5;
        AffineTransform t = new AffineTransform();
        switch (o) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return img; }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    /** JPEG 의 APP1(Exif) 에서 방향 태그(0x0112)를 읽는다. 없거나 깨졌으면 1 */
    static int exifOrientation(byte[] b) {
        try {
            if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return 1;
            int i = 2;
            while (i + 4 <= b.length) {
                if ((b[i] & 0xFF) != 0xFF) return 1;
                int marker = b[i + 1] & 0xFF;
                if (marker == 0xDA || marker == 0xD9) return 1;   // 그림 데이터 시작 — EXIF 는 그 앞에만 있다
                int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
                if (marker == 0xE1 && len >= 8 && i + 2 + len <= b.length
                        && new String(b, i + 4, 4, StandardCharsets.ISO_8859_1).equals("Exif")) {
                    int tiff = i + 10;
                    boolean le = b[tiff] == 'I';
                    int ifd = tiff + u32(b, tiff + 4, le);
                    int n = u16(b, ifd, le);
                    for (int k = 0; k < n; k++) {
                        int e = ifd + 2 + k * 12;
                        if (u16(b, e, le) == 0x0112) return u16(b, e + 8, le);
                    }
                    return 1;
                }
                i += 2 + len;
            }
        } catch (RuntimeException ignore) { /* 깨진 EXIF — 돌리지 않는다 */ }
        return 1;
    }

    private static int u16(byte[] b, int i, boolean le) {
        return le ? (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) : ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static int u32(byte[] b, int i, boolean le) {
        return le ? u16(b, i, true) | (u16(b, i + 2, true) << 16) : (u16(b, i, false) << 16) | u16(b, i + 2, false);
    }

    // ── PNG ────────────────────────────────────────────────────────────────
    /** 위치·작성자·기기 정보가 들어갈 수 있는 덩어리 */
    private static final Set<String> PNG_DROP = Set.of("eXIf", "tEXt", "iTXt", "zTXt", "tIME");
    private static final byte[] PNG_SIG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    static byte[] png(byte[] in) throws IOException {
        if (in.length < 8 || !java.util.Arrays.equals(java.util.Arrays.copyOf(in, 8), PNG_SIG))
            throw new IOException("이미지를 읽을 수 없습니다.");
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(PNG_SIG);
        int i = 8;
        while (i + 12 <= in.length) {
            int len = u32(in, i, false);
            if (len < 0 || i + 12L + len > in.length) throw new IOException("이미지를 읽을 수 없습니다.");
            String type = new String(in, i + 4, 4, StandardCharsets.ISO_8859_1);
            if (!PNG_DROP.contains(type)) out.write(in, i, 12 + len);
            i += 12 + len;
            if (type.equals("IEND")) break;
        }
        return out.toByteArray();
    }

    /** 테스트용 — PNG 덩어리 하나를 CRC 까지 맞춰 만든다 */
    static byte[] pngChunk(String type, byte[] data) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int n = data.length;
        o.write(n >>> 24); o.write(n >>> 16); o.write(n >>> 8); o.write(n);
        byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
        o.writeBytes(t);
        o.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        long c = crc.getValue();
        o.write((int) (c >>> 24)); o.write((int) (c >>> 16)); o.write((int) (c >>> 8)); o.write((int) c);
        return o.toByteArray();
    }

    // ── WebP ───────────────────────────────────────────────────────────────
    static byte[] webp(byte[] in) throws IOException {
        if (in.length < 12 || !new String(in, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")
                || !new String(in, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP"))
            throw new IOException("이미지를 읽을 수 없습니다.");
        ByteArrayOutputStream body = new ByteArrayOutputStream(in.length);
        body.writeBytes("WEBP".getBytes(StandardCharsets.ISO_8859_1));
        int i = 12;
        while (i + 8 <= in.length) {
            String type = new String(in, i, 4, StandardCharsets.ISO_8859_1);
            int len = u32(in, i + 4, true);
            int padded = len + (len & 1);
            if (len < 0 || i + 8L + padded > in.length) throw new IOException("이미지를 읽을 수 없습니다.");
            if (type.equals("EXIF") || type.equals("XMP ")) { i += 8 + padded; continue; }
            int at = body.size();
            body.write(in, i, 8 + padded);
            if (type.equals("VP8X")) {   // 확장 머리글의 「EXIF 있음(0x08)·XMP 있음(0x04)」 표시를 끈다
                byte[] cur = body.toByteArray();
                cur[at + 8] = (byte) (cur[at + 8] & ~0x0C);
                body.reset();
                body.writeBytes(cur);
            }
            i += 8 + padded;
        }
        byte[] b = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + 8);
        out.writeBytes("RIFF".getBytes(StandardCharsets.ISO_8859_1));
        int size = b.length;
        out.write(size); out.write(size >>> 8); out.write(size >>> 16); out.write(size >>> 24);
        out.writeBytes(b);
        return out.toByteArray();
    }
}
