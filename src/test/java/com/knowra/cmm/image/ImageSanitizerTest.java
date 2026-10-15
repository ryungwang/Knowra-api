package com.knowra.cmm.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageSanitizerTest {

    private static final String GPS = "GPS-37.5665N-126.9780E";

    /** 왼쪽 위 한 칸만 빨간 w×h 그림 — 돌렸을 때 그 칸이 어디로 가는지로 방향을 확인한다 */
    private static BufferedImage marked(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) img.setRGB(x, y, Color.WHITE.getRGB());
        img.setRGB(0, 0, Color.RED.getRGB());
        return img;
    }

    private static boolean red(BufferedImage img, int x, int y) {
        Color c = new Color(img.getRGB(x, y));
        return c.getRed() > 200 && c.getGreen() < 80;
    }

    @Test
    void 방향값대로_돌리면_빨간칸이_제자리로_간다() {
        int w = 4, h = 2;
        int[][] expect = {{}, {}, {w - 1, 0}, {w - 1, h - 1}, {0, h - 1}, {0, 0}, {h - 1, 0}, {h - 1, w - 1}, {0, w - 1}};
        for (int o = 2; o <= 8; o++) {
            BufferedImage out = ImageSanitizer.orient(marked(w, h), o);
            boolean swap = o >= 5;
            assertThat(out.getWidth()).as("방향 %d 폭", o).isEqualTo(swap ? h : w);
            assertThat(red(out, expect[o][0], expect[o][1])).as("방향 %d", o).isTrue();
        }
    }

    /** 실제 휴대폰 사진처럼 SOI 바로 뒤에 EXIF(방향 + GPS 처럼 보이는 글자)를 끼운 JPEG */
    private static byte[] jpegWithExif(BufferedImage img, int orientation) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", raw);
        byte[] jpg = raw.toByteArray();
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});      // 빅엔디언, IFD0 은 8
        tiff.writeBytes(new byte[]{0, 1});                               // 항목 1개
        tiff.writeBytes(new byte[]{0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0});
        tiff.writeBytes(new byte[]{0, 0, 0, 0});
        tiff.writeBytes(GPS.getBytes(StandardCharsets.US_ASCII));
        byte[] t = tiff.toByteArray();
        int len = 2 + 6 + t.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        out.write(0xFF); out.write(0xE1); out.write(len >>> 8); out.write(len);
        out.writeBytes("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(t);
        out.write(jpg, 2, jpg.length - 2);
        return out.toByteArray();
    }

    @Test
    void JPEG_은_위치정보가_빠지고_돌려찍은_사진이_선다() throws IOException {
        byte[] in = jpegWithExif(marked(40, 20), 6);
        assertThat(ImageSanitizer.exifOrientation(in)).isEqualTo(6);
        assertThat(new String(in, StandardCharsets.ISO_8859_1)).contains(GPS);

        byte[] out = ImageSanitizer.clean(in, "jpg");
        String s = new String(out, StandardCharsets.ISO_8859_1);
        assertThat(s).doesNotContain(GPS).doesNotContain("Exif");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out));
        assertThat(img.getWidth()).isEqualTo(20);   // 90도 돌아 세로가 됐다
        assertThat(img.getHeight()).isEqualTo(40);
    }

    @Test
    void 큰_사진은_긴변을_줄인다() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4000, 1000, BufferedImage.TYPE_INT_RGB), "jpeg", raw);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(ImageSanitizer.clean(raw.toByteArray(), "jpeg")));
        assertThat(img.getWidth()).isEqualTo(ImageSanitizer.MAX_SIDE);
        assertThat(img.getHeight()).isEqualTo(640);
    }

    @Test
    void PNG_는_정보_덩어리만_빼고_그림은_그대로() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ImageIO.write(marked(5, 3), "png", raw);
        byte[] png = raw.toByteArray();
        ByteArrayOutputStream withMeta = new ByteArrayOutputStream();
        withMeta.write(png, 0, 33);   // 서명 8 + IHDR 25
        withMeta.writeBytes(ImageSanitizer.pngChunk("eXIf", GPS.getBytes(StandardCharsets.US_ASCII)));
        withMeta.writeBytes(ImageSanitizer.pngChunk("tEXt", ("Comment\0" + GPS).getBytes(StandardCharsets.ISO_8859_1)));
        withMeta.write(png, 33, png.length - 33);

        byte[] out = ImageSanitizer.clean(withMeta.toByteArray(), "png");
        assertThat(new String(out, StandardCharsets.ISO_8859_1)).doesNotContain(GPS);
        assertThat(out).isEqualTo(png);   // 빼고 나면 원래 파일과 같다
    }

    @Test
    void WebP_는_EXIF_덩어리와_표시를_뺀다() throws IOException {
        byte[] vp8x = {'V', 'P', '8', 'X', 10, 0, 0, 0, 0x0C, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] exif = ("EXIF" + "\u0016\0\0\0" + GPS).getBytes(StandardCharsets.ISO_8859_1);   // 길이 22(짝수)
        byte[] vp8 = {'V', 'P', '8', 'L', 2, 0, 0, 0, 1, 2};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.ISO_8859_1));
        body.writeBytes(vp8x); body.writeBytes(exif); body.writeBytes(vp8);
        byte[] b = body.toByteArray();
        ByteArrayOutputStream in = new ByteArrayOutputStream();
        in.writeBytes("RIFF".getBytes(StandardCharsets.ISO_8859_1));
        in.write(b.length); in.write(0); in.write(0); in.write(0);
        in.writeBytes(b);

        byte[] out = ImageSanitizer.clean(in.toByteArray(), "webp");
        assertThat(new String(out, StandardCharsets.ISO_8859_1)).doesNotContain(GPS).doesNotContain("EXIF");
        assertThat(out[20] & 0x0C).isZero();                                  // VP8X 의 EXIF·XMP 표시 꺼짐
        assertThat((out[4] & 0xFF) + 8).isEqualTo(out.length);                // RIFF 크기를 다시 맞췄다
    }

    @Test
    void 이미지가_아닌_파일은_거절한다() {
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ImageSanitizer.clean(html, "jpg")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImageSanitizer.clean(html, "png")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImageSanitizer.clean(html, "webp")).isInstanceOf(IOException.class);
    }
}
