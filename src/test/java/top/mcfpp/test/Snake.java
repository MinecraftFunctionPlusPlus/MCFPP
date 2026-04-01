package top.mcfpp.test;

import java.io.IOException;

public class Snake {
    // 地图尺寸
    static final int ROWS = 20;
    static final int COLS = 30;

    // 显示字符（确保控制台支持 UTF-8）
    static final char CH_EMPTY = '□';
    static final char CH_SNAKE = '■';
    static final char CH_FOOD  = '*';

    // 方向：0=UP,1=RIGHT,2=DOWN,3=LEFT
    static int dir = 1;

    // 蛇的身体位置循环队列（存 r*COLS + c）
    static final int MAXCELLS = ROWS * COLS;
    static int[] snake = new int[MAXCELLS];
    static int headIdx = 0; // 指向队列中头元素位置索引（包含）
    static int tailIdx = 0; // 指向队列中尾后位置索引（不包含）
    // 当前蛇长度 = (tailIdx - headIdx + MAXCELLS) % MAXCELLS
    static boolean[][] occupied = new boolean[ROWS][COLS];

    // 地图与食物位置
    static char[][] map = new char[ROWS][COLS];
    static int foodR = -1, foodC = -1;

    // 速度（毫秒每步）
    static final int STEP_MS = 200;

    // 简单伪随机（线性同余）
    static long rnd = System.currentTimeMillis() & 0x7FFFFFFF;
    static int nextInt(int bound) {
        rnd = (1103515245 * rnd + 12345) & 0x7FFFFFFF;
        return (int)(rnd % bound);
    }

    // 队列操作
    static int snakeLen() {
        return (tailIdx - headIdx + MAXCELLS) % MAXCELLS;
    }
    static void queueAdd(int v) {
        snake[tailIdx] = v;
        tailIdx = (tailIdx + 1) % MAXCELLS;
    }
    static int queueRemove() {
        int v = snake[headIdx];
        headIdx = (headIdx + 1) % MAXCELLS;
        return v;
    }

    // 初始化
    static void init() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                map[r][c] = CH_EMPTY;

        // 初始蛇：长度 3，居中，向右
        int midR = ROWS / 2;
        int midC = COLS / 2;
        int c1 = midC - 1, c2 = midC, c3 = midC + 1;
        headIdx = tailIdx = 0;
        queueAdd(midR * COLS + c1);
        queueAdd(midR * COLS + c2);
        queueAdd(midR * COLS + c3);
        for (int i = 0; i < ROWS; i++) for (int j = 0; j < COLS; j++) occupied[i][j] = false;
        occupied[midR][c1] = occupied[midR][c2] = occupied[midR][c3] = true;
        map[midR][c1] = map[midR][c2] = map[midR][c3] = CH_SNAKE;

        placeFood();
    }

    static void placeFood() {
        if (snakeLen() == ROWS * COLS) return; // 满格，无需放
        while (true) {
            int r = nextInt(ROWS);
            int c = nextInt(COLS);
            if (!occupied[r][c]) {
                foodR = r; foodC = c;
                map[r][c] = CH_FOOD;
                break;
            }
        }
    }

    // 渲染（清屏 + 打印）
    static void render(int score) {
        System.out.print("\033[H\033[2J"); System.out.flush();
        System.out.println("Score: " + score + "  (W/A/S/D to change direction)");
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) sb.append(map[r][c]);
            sb.append('\n');
        }
        System.out.print(sb);
    }

    // 移动一步，返回 false 表示游戏结束
    static boolean step() {
        // 当前头位置
        int headPos = snake[(tailIdx - 1 + MAXCELLS) % MAXCELLS];
        int hr = headPos / COLS, hc = headPos % COLS;
        int nr = hr, nc = hc;
        if (dir == 0) nr--;
        else if (dir == 1) nc++;
        else if (dir == 2) nr++;
        else nc--;

        // 出界 -> 失败
        if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) return false;

        // 若新位置为食物，则增长；否则会移除尾
        boolean grow = (nr == foodR && nc == foodC);

        // 如果新位置被身体占据且不是将要移除的尾，失败。
        // 计算尾位置（若不增长，则尾会被移除）
        int tailPos = snake[headIdx];
        int tailR = tailPos / COLS, tailC = tailPos % COLS;
        if (occupied[nr][nc]) {
            if (!( !grow && nr == tailR && nc == tailC )) {
                return false; // 撞到自己
            }
        }

        // 添加新头
        queueAdd(nr * COLS + nc);
        occupied[nr][nc] = true;
        map[nr][nc] = CH_SNAKE;

        if (grow) {
            // 吃到食物，更新分数并放新食物
            placeFood();
        } else {
            // 移除尾
            int removed = queueRemove();
            int rr = removed / COLS, rc = removed % COLS;
            occupied[rr][rc] = false;
            map[rr][rc] = CH_EMPTY;
        }
        return true;
    }

    // 非阻塞读取单字符（works on UNIX-like terminals if terminal configured）
    // 兼容性有限：在 Windows 的 cmd 可能不可用；若不可用可改为阻塞输入模式。
    static char readInputNonBlocking() {
        try {
            if (System.in.available() > 0) {
                int b = System.in.read();
                if (b == '\r') return 0;
                if (b == '\n') {
                    // 忽略换行，尝试读取下一个可用
                    if (System.in.available() > 0) b = System.in.read();
                    else return 0;
                }
                return (char)b;
            }
        } catch (IOException e) { /* ignore */ }
        return 0;
    }

    public static void main(String[] args) throws Exception {
        init();
        boolean alive = true;
        int score = snakeLen() - 3;
        render(score);

        // 主循环：固定时间步
        while (alive) {
            long t0 = System.currentTimeMillis();

            // 处理输入（可能无阻塞返回）
            char ch = readInputNonBlocking();
            if (ch != 0) {
                ch = Character.toLowerCase(ch);
                int newDir = dir;
                if (ch == 'w') newDir = 0;
                else if (ch == 'd') newDir = 1;
                else if (ch == 's') newDir = 2;
                else if (ch == 'a') newDir = 3;
                // 禁止直接反向
                if (Math.abs(newDir - dir) != 2) dir = newDir;
            }

            // 移动一步
            alive = step();
            score = snakeLen() - 3;
            render(score);

            // 控制步频
            long elapsed = System.currentTimeMillis() - t0;
            long wait = STEP_MS - elapsed;
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
            }
        }

        System.out.println("Game Over! Final score: " + (snakeLen() - 3));
    }
}
