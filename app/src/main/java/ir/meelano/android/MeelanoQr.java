package ir.meelano.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * مولد QR «پخش درخشان» — پیاده‌سازی مستقل (بدون هیچ کتابخانهٔ بیرونی) در حالت «بایت» با
 * سطوح تصحیح خطای L/M/Q/H و نسخه‌های ۱ تا ۱۰ (تا ۲۷۰ بایت داده).
 *
 * <p>چرا لازم است؟ سامانهٔ مودیان روی صورتحساب‌های الکترونیکی QR می‌خواهد، برچسب انبار هم
 * برای شناسهٔ کالا/سری/تاریخ انقضا به QR نیاز دارد و بارکدخوان‌های ارزان انبار فقط
 * Code128/EAN را می‌خوانند؛ هر دو موتور در خود برنامه هستند تا اپ آفلاین هم کار کند.</p>
 *
 * <p>الگوریتم این کلاس با کتابخانهٔ مرجع مقایسه شده است: tools/qr_reference_check.py دقیقاً
 * همین منطق را اجرا می‌کند و ماتریس خروجی را ماژول‌به‌ماژول با کتابخانهٔ qrcode (برای ۴ سطح
 * خطا، ۸ ماسک و چند نسخه) مقایسه می‌کند. جزئیات در docs/.</p>
 */
final class MeelanoQr {
    private MeelanoQr() { }

    static final int MAX_VERSION = 10;

    /** ماتریس نهایی QR. */
    static final class Code {
        final boolean[][] modules;
        final int version, mask, size;
        Code(boolean[][] modules, int version, int mask) {
            this.modules = modules;
            this.version = version;
            this.mask = mask;
            this.size = modules.length;
        }
        boolean at(int row, int col) { return modules[row][col]; }
    }

    /** ساختار هر سطر: {ec_per_block, count1, data1, count2, data2} — بر اساس استاندارد ISO/IEC 18004. */
    private static final int[][][] RS = {
            {{7,1,19,0,0}, {10,1,34,0,0}, {15,1,55,0,0}, {20,1,80,0,0}, {26,1,108,0,0}, {18,2,68,0,0}, {20,2,78,0,0}, {24,2,97,0,0}, {30,2,116,0,0}, {18,2,68,2,69}},   // L
            {{10,1,16,0,0}, {16,1,28,0,0}, {26,1,44,0,0}, {18,2,32,0,0}, {24,2,43,0,0}, {16,4,27,0,0}, {18,4,31,0,0}, {22,2,38,2,39}, {22,3,36,2,37}, {26,4,43,1,44}},   // M
            {{13,1,13,0,0}, {22,1,22,0,0}, {18,2,17,0,0}, {26,2,24,0,0}, {18,2,15,2,16}, {24,4,19,0,0}, {18,2,14,4,15}, {22,4,18,2,19}, {20,4,16,4,17}, {24,6,19,2,20}},   // Q
            {{17,1,9,0,0}, {28,1,16,0,0}, {22,2,13,0,0}, {16,4,9,0,0}, {22,2,11,2,12}, {28,4,15,0,0}, {26,4,13,1,14}, {26,4,14,2,15}, {24,4,12,4,13}, {28,6,15,2,16}}   // H
    };

    private static final int[][] ALIGNMENT = {
            {},
            {6, 18},
            {6, 22},
            {6, 26},
            {6, 30},
            {6, 34},
            {6, 22, 38},
            {6, 24, 42},
            {6, 26, 46},
            {6, 28, 50}
    };

    private static final int[] TOTAL_CODEWORDS = {26, 44, 70, 100, 134, 172, 196, 242, 292, 346};

    /** ترتیب سطح‌ها در RS: L=0, M=1, Q=2, H=3 */
    private static final int[] FORMAT_EC_BITS = {1, 0, 3, 2};

    private static int levelIndex(char level) {
        if (level == 'L' || level == 'l') return 0;
        if (level == 'Q' || level == 'q') return 2;
        if (level == 'H' || level == 'h') return 3;
        return 1; // M پیش‌فرض
    }


    // ------------------------------------------------------------ API
    /** حداکثر بایت داده در یک نسخه و سطح مشخص. */
    static int capacityBytes(int version, char level) {
        int[] g = RS[levelIndex(level)][Math.max(1, Math.min(MAX_VERSION, version))];
        int totalData = g[1] * g[2] + g[3] * g[4];
        int header = 4 + (version <= 9 ? 8 : 16);
        return (totalData * 8 - header) / 8;
    }

    /** کوچک‌ترین نسخه‌ای که داده در آن جا می‌شود (۰ = بزرگ‌تر از توان نسخه ۱۰). */
    static int bestVersion(int byteLength, char level) {
        for (int v = 1; v <= MAX_VERSION; v++) if (capacityBytes(v, level) >= byteLength) return v;
        return 0;
    }

    /** ساخت QR با انتخاب خودکار نسخه و ماسک. */
    static Code encode(String text, char level) {
        byte[] data = utf8(text);
        int version = bestVersion(data.length, level);
        if (version <= 0) return null;
        Code best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            Code code = build(data, version, level, mask);
            int score = penalty(code.modules);
            if (score < bestScore) { bestScore = score; best = code; }
        }
        return best;
    }

    /** ساخت QR با نسخه و ماسک مشخص (برای آزمون و مقایسه با مرجع). */
    static Code encode(String text, char level, int version, int mask) {
        return build(utf8(text), version, level, mask & 7);
    }

    static Code encodeBytes(byte[] data, char level) {
        int version = bestVersion(data.length, level);
        if (version <= 0) return null;
        Code best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            Code code = build(data, version, level, mask);
            int score = penalty(code.modules);
            if (score < bestScore) { bestScore = score; best = code; }
        }
        return best;
    }

    // ------------------------------------------------------------ ساخت
    private static Code build(byte[] data, int version, char level, int mask) {
        int size = 17 + 4 * version;
        boolean[][] m = new boolean[size][size];
        boolean[][] reserved = new boolean[size][size];
        finder(m, reserved, 0, 0, size);
        finder(m, reserved, 0, size - 7, size);
        finder(m, reserved, size - 7, 0, size);
        alignment(m, reserved, version, size);
        timing(m, reserved, size);
        reserveFormat(reserved, size);
        writeVersion(m, reserved, version, size);
        placeData(m, reserved, codewords(data, version, level), mask, size);
        writeFormat(m, level, mask, size);
        return new Code(m, version, mask);
    }

    private static void finder(boolean[][] m, boolean[][] r, int row, int col, int size) {
        for (int dr = -1; dr <= 7; dr++) {
            for (int dc = -1; dc <= 7; dc++) {
                int rr = row + dr, cc = col + dc;
                if (rr < 0 || cc < 0 || rr >= size || cc >= size) continue;
                r[rr][cc] = true;
                boolean dark = (dr >= 0 && dr <= 6 && (dc == 0 || dc == 6))
                        || (dc >= 0 && dc <= 6 && (dr == 0 || dr == 6))
                        || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4);
                m[rr][cc] = dark;
            }
        }
    }

    private static void alignment(boolean[][] m, boolean[][] r, int version, int size) {
        int[] pos = ALIGNMENT[version - 1];
        for (int a = 0; a < pos.length; a++) {
            for (int b = 0; b < pos.length; b++) {
                int cr = pos[a], cc = pos[b];
                if (r[cr][cc]) continue;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int rr = cr + dr, c2 = cc + dc;
                        if (rr < 0 || c2 < 0 || rr >= size || c2 >= size) continue;
                        r[rr][c2] = true;
                        m[rr][c2] = Math.max(Math.abs(dr), Math.abs(dc)) != 1;
                    }
                }
            }
        }
    }

    private static void timing(boolean[][] m, boolean[][] r, int size) {
        for (int i = 0; i < size; i++) {
            if (!r[6][i]) { r[6][i] = true; m[6][i] = i % 2 == 0; }
            if (!r[i][6]) { r[i][6] = true; m[i][6] = i % 2 == 0; }
        }
    }

    private static void reserveFormat(boolean[][] r, int size) {
        for (int i = 0; i < 9; i++) {
            if (!r[8][i]) r[8][i] = true;
            if (!r[i][8]) r[i][8] = true;
        }
        for (int i = 0; i < 8; i++) {
            r[8][size - 1 - i] = true;
            r[size - 1 - i][8] = true;
        }
        r[size - 8][8] = true;
    }

    private static void writeVersion(boolean[][] m, boolean[][] r, int version, int size) {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean bit = ((bits >> i) & 1) == 1;
            int a = size - 11 + i % 3, b = i / 3;
            m[b][a] = bit; r[b][a] = true;
            m[a][b] = bit; r[a][b] = true;
        }
    }

    private static void writeFormat(boolean[][] m, char level, int mask, int size) {
        int data = (FORMAT_EC_BITS[levelIndex(level)] << 3) | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i < 15; i++) {
            boolean bit = ((bits >> i) & 1) == 1;
            if (i < 6) m[i][8] = bit;
            else if (i < 8) m[i + 1][8] = bit;
            else if (i == 8) m[8][7] = bit;
            else m[8][14 - i] = bit;
            if (i < 8) m[8][size - 1 - i] = bit;
            else m[size - 15 + i][8] = bit;
        }
        m[size - 8][8] = true; // ماژول تیرهٔ ثابت
    }

    private static void placeData(boolean[][] m, boolean[][] r, int[] codewords, int mask, int size) {
        int bitIndex = 0;
        int totalBits = codewords.length * 8;
        int col = size - 1;
        boolean upward = true;
        while (col > 0) {
            if (col == 6) col--;
            for (int k = 0; k < size; k++) {
                int row = upward ? size - 1 - k : k;
                for (int j = 0; j < 2; j++) {
                    int c = col - j;
                    if (c < 0 || r[row][c]) continue;
                    boolean bit = false;
                    if (bitIndex < totalBits) bit = ((codewords[bitIndex >> 3] >> (7 - (bitIndex & 7))) & 1) == 1;
                    bitIndex++;
                    m[row][c] = bit ^ maskBit(mask, row, c);
                }
            }
            upward = !upward;
            col -= 2;
        }
    }

    private static boolean maskBit(int mask, int row, int col) {
        switch (mask) {
            case 0: return (row + col) % 2 == 0;
            case 1: return row % 2 == 0;
            case 2: return col % 3 == 0;
            case 3: return (row + col) % 3 == 0;
            case 4: return (row / 2 + col / 3) % 2 == 0;
            case 5: return (row * col) % 2 + (row * col) % 3 == 0;
            case 6: return ((row * col) % 2 + (row * col) % 3) % 2 == 0;
            default: return ((row + col) % 2 + (row * col) % 3) % 2 == 0;
        }
    }

    // ------------------------------------------------------------ کدواژه‌ها
    private static int[] codewords(byte[] data, int version, char level) {
        int[] g = RS[levelIndex(level)][version];
        int ecPerBlock = g[0];
        int totalData = g[1] * g[2] + g[3] * g[4];
        int[] bits = new int[totalData * 8];
        int n = 0;
        n = push(bits, n, 0b0100, 4);                       // حالت بایت
        n = push(bits, n, data.length, version <= 9 ? 8 : 16);
        for (byte b : data) n = push(bits, n, b & 0xFF, 8);
        if (n > totalData * 8) throw new IllegalArgumentException("داده برای این نسخه بزرگ است");
        for (int i = 0; i < 4 && n < totalData * 8; i++) bits[n++] = 0;  // پایان‌دهنده
        while (n % 8 != 0) bits[n++] = 0;
        int[] cw = new int[totalData];
        for (int i = 0; i < totalData; i++) {
            int v = 0;
            for (int b = 0; b < 8; b++) v = (v << 1) | (i * 8 + b < n ? bits[i * 8 + b] : 0);
            cw[i] = v;
        }
        int padIndex = 0;
        for (int i = n / 8; i < totalData; i++) cw[i] = (padIndex++ % 2 == 0) ? 0xEC : 0x11;
        // بلوک‌بندی + تصحیح خطا
        int[][] blockData = new int[g[1] + g[3]][];
        int[][] blockEc = new int[g[1] + g[3]][];
        int offset = 0, bi = 0;
        for (int group = 0; group < 2; group++) {
            int count = group == 0 ? g[1] : g[3];
            int words = group == 0 ? g[2] : g[4];
            for (int c = 0; c < count; c++) {
                int[] block = new int[words];
                System.arraycopy(cw, offset, block, 0, Math.min(words, Math.max(0, totalData - offset)));
                offset += words;
                blockData[bi] = block;
                blockEc[bi] = rsEncode(block, ecPerBlock);
                bi++;
            }
        }
        int[] out = new int[totalData + ecPerBlock * blockData.length];
        int p = 0, maxData = 0;
        for (int[] b : blockData) maxData = Math.max(maxData, b.length);
        for (int i = 0; i < maxData; i++) for (int[] b : blockData) if (i < b.length) out[p++] = b[i];
        for (int i = 0; i < ecPerBlock; i++) for (int[] b : blockEc) out[p++] = b[i];
        return out;
    }

    private static int push(int[] bits, int n, int value, int len) {
        for (int i = len - 1; i >= 0; i--) bits[n++] = (value >> i) & 1;
        return n;
    }

    private static int[] rsEncode(int[] data, int ecLen) {
        int[] gen = generator(ecLen);
        int[] res = new int[ecLen];
        for (int k = 0; k < data.length; k++) {
            int factor = (data[k] ^ res[0]) & 0xFF;
            System.arraycopy(res, 1, res, 0, ecLen - 1);
            res[ecLen - 1] = 0;
            if (factor != 0) for (int i = 0; i < ecLen; i++) res[i] ^= gfMul(gen[i + 1], factor);
        }
        return res;
    }

    private static int[] generator(int degree) {
        int[] poly = {1};
        for (int i = 0; i < degree; i++) {
            int[] next = new int[poly.length + 1];
            for (int j = 0; j < poly.length; j++) {
                next[j] ^= poly[j];
                next[j + 1] ^= gfMul(poly[j], GF_EXP[i]);
            }
            poly = next;
        }
        return poly;
    }

    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) GF_EXP[i] = GF_EXP[i - 255];
    }

    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return GF_EXP[GF_LOG[a] + GF_LOG[b]];
    }

    // ------------------------------------------------------------ جریمهٔ ماسک
    private static int penalty(boolean[][] m) {
        int size = m.length, score = 0;
        for (int i = 0; i < size; i++) {
            int runRow = 1, runCol = 1;
            for (int j = 1; j < size; j++) {
                if (m[i][j] == m[i][j - 1]) { runRow++; if (runRow == 5) score += 3; else if (runRow > 5) score++; }
                else runRow = 1;
                if (m[j][i] == m[j - 1][i]) { runCol++; if (runCol == 5) score += 3; else if (runCol > 5) score++; }
                else runCol = 1;
            }
        }
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                boolean v = m[r][c];
                if (v == m[r][c + 1] && v == m[r + 1][c] && v == m[r + 1][c + 1]) score += 3;
            }
        }
        int dark = 0;
        for (boolean[] row : m) for (boolean b : row) if (b) dark++;
        int percent = dark * 100 / (size * size);
        score += Math.abs(percent - 50) / 5 * 10;
        return score;
    }

    // ------------------------------------------------------------ رسم
    /** رسم QR در یک محدودهٔ مشخص از Canvas با حاشیهٔ سکوت مشخص. */
    static void draw(Canvas canvas, String text, RectF area, int darkColor, int lightColor, int quietModules) {
        int size = quietModules < 0 ? 2 : quietModules;
        Code code = encode(text, 'M');
        if (code == null) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (lightColor != Color.TRANSPARENT) {
            p.setColor(lightColor);
            canvas.drawRect(area, p);
        }
        float total = code.size + size * 2f;
        float unit = Math.min(area.width(), area.height()) / total;
        float left = area.left + (area.width() - unit * total) / 2f + unit * size;
        float top = area.top + (area.height() - unit * total) / 2f + unit * size;
        p.setColor(darkColor);
        for (int r = 0; r < code.size; r++) {
            int c = 0;
            while (c < code.size) {
                if (!code.modules[r][c]) { c++; continue; }
                int start = c;
                while (c < code.size && code.modules[r][c]) c++;
                canvas.drawRect(left + start * unit, top + r * unit, left + c * unit, top + (r + 1) * unit, p);
            }
        }
    }

    /** QR به‌صورت Bitmap برای چاپ، اشتراک‌گذاری و PDF. */
    static Bitmap bitmap(String text, int sizePx, int darkColor, int lightColor, int quietModules) {
        Code code = encode(text, 'M');
        if (code == null) return null;
        int size = Math.max(64, sizePx);
        int quiet = quietModules < 0 ? 2 : quietModules;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(lightColor);
        float total = code.size + quiet * 2f;
        float unit = size / total;
        Paint p = new Paint();
        p.setColor(darkColor);
        for (int r = 0; r < code.size; r++) {
            for (int c = 0; c < code.size; c++) {
                if (code.modules[r][c]) canvas.drawRect((c + quiet) * unit, (r + quiet) * unit, (c + quiet + 1) * unit, (r + quiet + 1) * unit, p);
            }
        }
        return bmp;
    }

    /** کمکی: رشتهٔ «۱۰۱۱۰» برای آزمون و اشتراک‌گذاری با مرجع پایتون. */
    static String toBinary(Code code) {
        if (code == null) return "";
        StringBuilder b = new StringBuilder();
        for (boolean[] row : code.modules) {
            for (boolean v : row) b.append(v ? '1' : '0');
            b.append('\n');
        }
        return b.toString();
    }

    private static byte[] utf8(String text) {
        try { return (text == null ? "" : text).getBytes("UTF-8"); }
        catch (Exception e) { return new byte[0]; }
    }

    /** صرفه‌جویی در حافظه: در Context های بدون دسترسی به فایل هم کار می‌کند. */
    static Bitmap bitmap(Context ctx, String text, int sizePx, int darkColor, int lightColor) {
        return bitmap(text, sizePx, darkColor, lightColor, 2);
    }
}
