import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * 程序化生成 Z80Zhealthbar 自制贴图（替代不可复用的来源素材，见 docs/third-party-licenses.md）。
 * 输出：assets/z80zhealthbar/textures/gui/style_a_bars.png（512x40）
 *
 * 布局：
 *   y 0..11 : 4 种样式 A 外框变体（各 128x12；2px 双层边框 + 着色内槽，变体间差异显著）
 *   y 16..19: 4 条白色填充条（渲染期按血量状态着色）
 *   y 24..27: 4 条低透明度空槽条
 *
 * 变体配色（边框/槽底）：
 *   0 steel  钢灰  / 1 blood 血红 / 2 gold  金棕 / 3 arcane 奥蓝
 */
public class GenTextures {
    public static void main(String[] args) throws Exception {
        int sheetW = 512, sheetH = 40;
        BufferedImage img = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_ARGB);

        int[][] palettes = {
                {0xFF6E6E78, 0xFF2A2A30, 0xFFB8B8C4, 0xFF1D1D22}, // steel
                {0xFF9A3A34, 0xFF3A1412, 0xFFD08078, 0xFF2A1010}, // blood
                {0xFFB08A2E, 0xFF3A2E0E, 0xFFE8CC7A, 0xFF282008}, // gold
                {0xFF3A6E9A, 0xFF102434, 0xFF84B8D8, 0xFF0E1C2A}, // arcane
        };

        for (int v = 0; v < 4; v++) {
            int x0 = v * 128;
            drawFrame(img, x0, 0, 128, 12, palettes[v]);
            // 5px 填充条：渲染器 FILL_H=5 采样 v 16..21，条高必须同为 5px（4px 会采到底部透明行）
            drawFill(img, x0, 16, 128, 5, 0xFFFFFFFF, 0xFFD8D8D8);
            drawFill(img, x0, 24, 128, 4, 0x50202020, 0x38181818);
        }

        Path out = Path.of(args[0]);
        File f = out.toFile();
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("Wrote " + f);

        // 白色心形贴图（18x9 双区）：样式 2 心形牌匾渲染期分层染色（原版心形为彩色贴图不可染色）。
        // 轮廓取自原版 icons.png 两个图标的 alpha 蒙版：
        //   左半区 (0..8,0..8)  = 容器心 9x9、54 像素（比实心心大 1 圈——原版的"边框"正是
        //                        容器心叠在实心心后面露出的轮廓）
        //   右半区 (9..17,0..8) = 实心心 7x7、34 像素
        // 高光点 (2,2) 由渲染器用提亮色单独补绘（乘性染色贴图无法自带高光）
        BufferedImage heart = new BufferedImage(18, 9, BufferedImage.TYPE_INT_ARGB);
        String[] containerMask = {
                "..XX.XX..",
                ".XXXXXXX.",
                "XXXXXXXXX",
                "XXXXXXXXX",
                "XXXXXXXXX",
                ".XXXXXXX.",
                "..XXXXX..",
                "...XXX...",
                "....X....",
        };
        String[] heartMask = {
                ".........",
                "..XX.XX..",
                ".XXXXXXX.",
                ".XXXXXXX.",
                ".XXXXXXX.",
                "..XXXXX..",
                "...XXX...",
                "....X....",
                ".........",
        };
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                if (containerMask[y].charAt(x) == 'X') heart.setRGB(x, y, 0xFFFFFFFF);
                if (heartMask[y].charAt(x) == 'X') heart.setRGB(9 + x, y, 0xFFFFFFFF);
            }
        }
        Path heartOut = args.length > 1 ? Path.of(args[1]) : out.resolveSibling("heart_white.png");
        heartOut.toFile().getParentFile().mkdirs();
        ImageIO.write(heart, "png", heartOut.toFile());
        System.out.println("Wrote " + heartOut);
    }


    /** 2px 外框（外层主色 + 内层高光/阴影）+ 着色槽底，四角透明做圆角感 */
    static void drawFrame(BufferedImage img, int x0, int y0, int w, int h, int[] pal) {
        int border = pal[0], dark = pal[1], hi = pal[2], slot = pal[3];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                boolean corner = (x < 2 || x >= w - 2) && (y < 2 || y >= h - 2);
                boolean edge2 = x < 2 || y < 2 || x >= w - 2 || y >= h - 2; // 2px 外框区
                int c;
                if (corner && (x == 0 || x == w - 1) && (y == 0 || y == h - 1)) {
                    c = 0; // 透明角
                } else if (edge2) {
                    boolean outermost = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                    if (outermost) {
                        c = border;
                    } else {
                        c = (y == 1 || x == 1) ? hi : dark; // 上左高光 / 下右阴影
                    }
                } else {
                    c = slot; // 着色槽底（变体区分度的主要来源）
                }
                if (c != 0) img.setRGB(x0 + x, y0 + y, c);
            }
        }
    }

    /** 填充条：主体 + 底部 1px 阴影 */
    static void drawFill(BufferedImage img, int x0, int y0, int w, int h, int main, int bottom) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x0 + x, y0 + y, y == h - 1 ? bottom : main);
            }
        }
    }
}
