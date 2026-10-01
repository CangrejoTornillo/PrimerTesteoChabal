import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mini Asteroids en pixel art.
 *  - Nave: 4 pixeles
 *  - Piedra pequena: 1 pixel
 *  - Piedra grande: 3x3 pixeles (al romperse se divide en piedras pequenas)
 *
 * Controles: flechas izq/der = girar, flecha arriba = propulsor,
 *            ESPACIO = disparar, R = reiniciar, ESC = salir
 *
 * Compilar: javac Asteroids.java
 * Ejecutar: java Asteroids
 */
public class Asteroids extends JPanel implements ActionListener, KeyListener {

    // Resolucion "logica" (pixeles del juego) y escala de la ventana
    static final int W = 200, H = 150, SCALE = 5;

    static class Obj {
        double x, y, vx, vy;
        boolean big;   // solo para piedras
        int life;      // solo para balas
    }

    final Random rnd = new Random();
    final BufferedImage screen = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    final boolean[] keys = new boolean[256];

    // Nave
    double sx, sy, svx, svy, angle;
    int invulnerable, cooldown, lives, score, level;
    boolean gameOver;

    final List<Obj> rocks = new ArrayList<>();
    final List<Obj> bullets = new ArrayList<>();

    public Asteroids() {
        setPreferredSize(new Dimension(W * SCALE, H * SCALE));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        newGame();
        new Timer(16, this).start(); // ~60 FPS
    }

    // ---------- Estado del juego ----------

    void newGame() {
        lives = 3;
        score = 0;
        level = 1;
        gameOver = false;
        bullets.clear();
        spawnShip();
        spawnRocks(4);
    }

    void spawnShip() {
        sx = W / 2.0;
        sy = H / 2.0;
        svx = svy = 0;
        angle = -Math.PI / 2; // apuntando hacia arriba
        invulnerable = 120;
    }

    void spawnRocks(int n) {
        rocks.clear();
        for (int i = 0; i < n; i++) {
            Obj r = new Obj();
            // Lejos de la nave
            do {
                r.x = rnd.nextDouble() * W;
                r.y = rnd.nextDouble() * H;
            } while (dist(r.x, r.y, sx, sy) < 50);
            double a = rnd.nextDouble() * Math.PI * 2;
            double speed = 0.15 + rnd.nextDouble() * 0.2;
            r.vx = Math.cos(a) * speed;
            r.vy = Math.sin(a) * speed;
            r.big = true;
            rocks.add(r);
        }
    }

    static double dist(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    static double wrap(double v, double max) {
        if (v < 0) return v + max;
        if (v >= max) return v - max;
        return v;
    }

    // ---------- Logica ----------

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameOver) update();
        repaint();
    }

    void update() {
        // Control de la nave
        if (keys[KeyEvent.VK_LEFT]) angle -= 0.07;
        if (keys[KeyEvent.VK_RIGHT]) angle += 0.07;
        if (keys[KeyEvent.VK_UP]) {
            svx += Math.cos(angle) * 0.03;
            svy += Math.sin(angle) * 0.03;
        }
        svx *= 0.995;
        svy *= 0.995;
        sx = wrap(sx + svx, W);
        sy = wrap(sy + svy, H);
        if (invulnerable > 0) invulnerable--;

        // Disparo
        if (cooldown > 0) cooldown--;
        if (keys[KeyEvent.VK_SPACE] && cooldown == 0) {
            Obj b = new Obj();
            b.x = sx + Math.cos(angle) * 3;
            b.y = sy + Math.sin(angle) * 3;
            b.vx = svx + Math.cos(angle) * 2.5;
            b.vy = svy + Math.sin(angle) * 2.5;
            b.life = 60;
            bullets.add(b);
            cooldown = 12;
        }

        // Balas
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Obj b = bullets.get(i);
            b.x = wrap(b.x + b.vx, W);
            b.y = wrap(b.y + b.vy, H);
            if (--b.life <= 0) bullets.remove(i);
        }

        // Piedras
        for (Obj r : rocks) {
            r.x = wrap(r.x + r.vx, W);
            r.y = wrap(r.y + r.vy, H);
        }

        // Bala vs piedra
        outer:
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Obj b = bullets.get(i);
            for (int j = rocks.size() - 1; j >= 0; j--) {
                Obj r = rocks.get(j);
                double radius = r.big ? 2.5 : 1.5;
                if (dist(b.x, b.y, r.x, r.y) < radius) {
                    bullets.remove(i);
                    rocks.remove(j);
                    if (r.big) {
                        score += 20;
                        // Se rompe en 2-3 piedras pequenas
                        int pieces = 2 + rnd.nextInt(2);
                        for (int k = 0; k < pieces; k++) {
                            Obj s = new Obj();
                            s.x = r.x;
                            s.y = r.y;
                            double a = rnd.nextDouble() * Math.PI * 2;
                            double speed = 0.3 + rnd.nextDouble() * 0.4;
                            s.vx = Math.cos(a) * speed;
                            s.vy = Math.sin(a) * speed;
                            s.big = false;
                            rocks.add(s);
                        }
                    } else {
                        score += 50;
                    }
                    continue outer;
                }
            }
        }

        // Nave vs piedra
        if (invulnerable == 0) {
            for (Obj r : rocks) {
                double radius = (r.big ? 2.0 : 1.0) + 1.5;
                if (dist(sx, sy, r.x, r.y) < radius) {
                    lives--;
                    if (lives <= 0) gameOver = true;
                    else spawnShip();
                    break;
                }
            }
        }

        // Nivel completado
        if (rocks.isEmpty() && !gameOver) {
            level++;
            spawnRocks(3 + level);
            invulnerable = 120;
        }
    }

    // ---------- Dibujo ----------

    void plot(int x, int y, int rgb) {
        x = ((x % W) + W) % W;
        y = ((y % H) + H) % H;
        screen.setRGB(x, y, rgb);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);

        // Limpiar pantalla logica
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) screen.setRGB(x, y, 0x000000);

        // Piedras
        for (Obj r : rocks) {
            int cx = (int) Math.round(r.x), cy = (int) Math.round(r.y);
            if (r.big) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++) plot(cx + dx, cy + dy, 0xAAAAAA);
            } else {
                plot(cx, cy, 0xFFFFFF);
            }
        }

        // Balas (1 pixel)
        for (Obj b : bullets) plot((int) Math.round(b.x), (int) Math.round(b.y), 0xFFFF00);

        // Nave: 4 pixeles (morro, dos alas y centro), rotados segun el angulo
        boolean blink = invulnerable > 0 && (invulnerable / 6) % 2 == 0;
        if (!gameOver && !blink) {
            double[][] pts = {{2, 0}, {0, 0}, {-1, -1}, {-1, 1}};
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int i = 0; i < pts.length; i++) {
                double px = pts[i][0] * cos - pts[i][1] * sin;
                double py = pts[i][0] * sin + pts[i][1] * cos;
                plot((int) Math.round(sx + px), (int) Math.round(sy + py),
                        i == 0 ? 0xFF4040 : 0x40FF40);
            }
        }

        // Escalar con vecino mas cercano (pixeles nitidos)
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(screen, 0, 0, W * SCALE, H * SCALE, null);

        // HUD
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        g.drawString("Puntos: " + score + "   Vidas: " + lives + "   Nivel: " + level, 10, 20);

        if (gameOver) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 40));
            drawCentered(g, "GAME OVER", getHeight() / 2 - 10);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
            drawCentered(g, "Pulsa R para reiniciar", getHeight() / 2 + 30);
        }
    }

    void drawCentered(Graphics2D g, String s, int y) {
        int w = g.getFontMetrics().stringWidth(s);
        g.drawString(s, (getWidth() - w) / 2, y);
    }

    // ---------- Teclado ----------

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k < keys.length) keys[k] = true;
        if (k == KeyEvent.VK_R) newGame();
        if (k == KeyEvent.VK_ESCAPE) System.exit(0);
    }

    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k < keys.length) keys[k] = false;
    }

    @Override public void keyTyped(KeyEvent e) { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Mini Asteroids");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            f.add(new Asteroids());
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
