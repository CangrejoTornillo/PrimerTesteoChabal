import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel Tower Defense v3 - Actualización Satélite, Daño BTD6 & Bugfixes
 * Compilar: javac PixelTowerDefense.java
 * Ejecutar: java PixelTowerDefense
 */
public class PixelTowerDefense {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Pixel Tower Defense");
            GamePanel p = new GamePanel();
            f.add(p);
            f.pack();
            f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}

/** Utilidades de dibujo pixel art e interfaz. */
final class Gfx {
    private Gfx() { }

    static void sprite(Graphics2D g, String[] rows, String keys, Color[] cols, int cx, int cy, int sc) {
        int ox = cx - rows[0].length() * sc / 2, oy = cy - rows.length * sc / 2;
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++) {
                int k = keys.indexOf(rows[j].charAt(i));
                if (k >= 0) { g.setColor(cols[k]); g.fillRect(ox + i * sc, oy + j * sc, sc, sc); }
            }
    }

    static void txt(Graphics2D g, String s, int x, int y, int size, Color c) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, size));
        g.setColor(c);
        g.drawString(s, x, y);
    }

    static void center(Graphics2D g, String s, int cx, int y, int size, Color c) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, size));
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    static void bar(Graphics2D g, int x, int y, int w, int h, double frac) {
        g.setColor(new Color(20, 20, 20)); g.fillRect(x - 1, y - 1, w + 2, h + 2);
        g.setColor(new Color(130, 25, 25)); g.fillRect(x, y, w, h);
        g.setColor(new Color(70, 220, 90)); g.fillRect(x, y, (int) (w * Math.max(0, Math.min(1, frac))), h);
    }

    static void btn(Graphics2D g, Rectangle r, String s, boolean on, Color c) {
        g.setColor(on ? c : new Color(75, 70, 85)); g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(on ? c.brighter() : new Color(100, 95, 110)); g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        center(g, s, r.x + r.width / 2, r.y + r.height / 2 + 5, 11, on ? Color.WHITE : new Color(150, 145, 160));
    }

    static void upgradeCard(Graphics2D g, Rectangle r, String title, int current, int next, String effect, int cost, boolean on, Color c) {
        Color bg = on ? new Color(49, 45, 61) : new Color(57, 53, 67);
        g.setColor(bg); g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        g.setColor(on ? c : new Color(92, 87, 105));
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 10, 10);

        g.setColor(c); g.fillRoundRect(r.x + 7, r.y + 7, 7, r.height - 14, 6, 6);
        txt(g, title, r.x + 21, r.y + 17, 10, Color.WHITE);

        String lvl = cost > 0 ? "Niv " + current + " → " + next : "Niv MAX";
        txt(g, lvl, r.x + 21, r.y + 31, 9, new Color(185, 180, 200));
        txt(g, effect, r.x + 21, r.y + 42, 9, on ? new Color(225, 220, 235) : new Color(150, 145, 165));

        if (cost > 0) {
            g.setColor(on ? c : new Color(80, 75, 90));
            g.fillRoundRect(r.x + r.width - 82, r.y + 8, 71, 27, 8, 8);
            center(g, cost + "g", r.x + r.width - 46, r.y + 26, 10, on ? Color.WHITE : new Color(150, 145, 160));
        } else {
            center(g, "MAX", r.x + r.width - 47, r.y + 26, 10, new Color(165, 160, 175));
        }
    }
}

final class Waypoint {
    final int x, y;
    Waypoint(int x, int y) { this.x = x; this.y = y; }
}

final class ShopButton {
    final int x, y, w, h, type;
    ShopButton(int x, int y, int w, int h, int type) { this.x = x; this.y = y; this.w = w; this.h = h; this.type = type; }
    boolean hit(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
}

/** Efectos visuales de destellos, rayos y explosiones. */
final class Fx {
    final double x, y, r;
    final int max;
    final Color c;
    int life;

    Fx(double x, double y, double r, int life, Color c) {
        this.x = x; this.y = y; this.r = r; this.life = life; this.max = life; this.c = c;
    }

    void draw(Graphics2D g) {
        double a = (double) life / max;
        int rr = (int) (r * (1.0 - 0.55 * a)), al = (int) (170 * a);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), al));
        g.fillOval((int) x - rr, (int) y - rr, 2 * rr, 2 * rr);
        g.setColor(new Color(255, 255, 255, al));
        g.drawOval((int) x - rr, (int) y - rr, 2 * rr, 2 * rr);
    }
}

/** Objeto contenedor para encolar apariciones de enemigos. */
final class EnemySpawn {
    final int type;
    final boolean camo, regen;
    EnemySpawn(int type, boolean camo, boolean regen) {
        this.type = type; this.camo = camo; this.regen = regen;
    }
}

/** Enemigos (Slimes): 13 tipos con sistema de capas BTD6. */
final class Enemy {
    static final int ROJO = 0, AZUL = 1, VERDE = 2, AMARILLO = 3, ROSA = 4, NEGRO = 5, BLANCO = 6, PURPURA = 7, CEBRA = 8, PLOMO = 9, ARCOIRIS = 10, CERAMICO = 11, MOAB = 12;

    static final double[] HP = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 10, 200};
    static final int[] REWARD = {1, 2, 3, 4, 5, 6, 6, 6, 10, 12, 20, 35, 200};
    static final int[] LIVES_COST = {1, 2, 3, 4, 5, 11, 11, 11, 23, 23, 47, 104, 600};
    static final double[] SPEED = {1.5, 1.8, 2.1, 3.2, 3.8, 1.8, 1.8, 3.2, 2.0, 1.0, 2.2, 2.5, 0.8};

    static final Color[] COL = {
        new Color(220, 50, 50),   // 0 Rojo
        new Color(60, 120, 235),  // 1 Azul
        new Color(50, 180, 70),   // 2 Verde
        new Color(245, 205, 40),  // 3 Amarillo
        new Color(245, 130, 170), // 4 Rosa
        new Color(50, 50, 60),    // 5 Negro
        new Color(230, 230, 240), // 6 Blanco
        new Color(140, 60, 200),  // 7 Púrpura
        new Color(180, 180, 190), // 8 Cebra
        new Color(110, 120, 130), // 9 Plomo
        new Color(255, 120, 180), // 10 Arcoíris
        new Color(160, 90, 50),   // 11 Cerámico
        new Color(40, 80, 180)    // 12 MOAB
    };

    static final String[] SLIME = {"...oooo...", "..ommmmo..", ".ommhhmmo.", "ommmmmmmmo", "omwkmmwkmo", "ommmmmmmmo", "ommmmmmmmo", ".oooooooo."};

    final GamePanel game;
    int type, initialType;
    double speed, maxHp, hp, x, y, dist, slowF = 1;
    int idx = 1, slowT, bossT, regenTimer;
    boolean leaked, popped, camo, regen, revealed;

    Enemy(GamePanel game, int type, boolean camo, boolean regen, int wave) {
        this.game = game;
        this.type = type;
        this.initialType = type;
        this.camo = camo;
        this.regen = regen;
        this.revealed = !camo;

        Waypoint s = game.path.get(0);
        x = s.x; y = s.y;
        maxHp = HP[type];
        if (type == MOAB) maxHp += (wave - 40) * 50;
        hp = maxHp;
        speed = SPEED[type];
    }

    boolean isBoss() { return type == MOAB; }

    double getRadius() {
        if (type == MOAB) return 36;
        if (type == CERAMICO || type == PLOMO) return 15;
        return 11;
    }

    void setType(int newType) {
        this.type = newType;
        this.maxHp = HP[newType];
        this.hp = maxHp;
        this.speed = SPEED[newType];
    }

    /**
     * Daño por capas con propagación correcta del daño sobrante.
     * Un golpe que rompe una capa no se duplica entre los hijos:
     * el daño sobrante se reparte entre ellos y se propaga recursivamente.
     */
    void hurt(double d, Tower srcTower) {
        int damage = (int) Math.floor(Math.max(1, d));
        applyDamage(damage, srcTower, game.pendingEnemies);
    }

    /** Aplica daño y devuelve el daño que no pudo consumir esta rama. */
    private int applyDamage(int damage, Tower srcTower, List<Enemy> output) {
        if (damage <= 0 || hp <= 0) return damage;

        // Inmunidades
        if (type == PLOMO) {
            if (srcTower != null && (srcTower.type == 0 || srcTower.type == 1 || srcTower.type == 2)) {
                if (srcTower.lv[0] < 4) return 0; // Armas afiladas rebotan salvo que estén mejoradas
            }
        }
        if (type == NEGRO && srcTower != null && srcTower.type == 3) return 0; // Inmune a explosiones
        if (type == BLANCO && srcTower != null && srcTower.type == 4) return 0; // Inmune a magia

        regenTimer = 0;

        // MOAB: HP real, sin hijos afectados por el golpe.
        if (type == MOAB) {
            hp -= damage;
            if (hp <= 0) hp = 0;
            return 0;
        }

        // CERÁMICO: primero hay que romper los 10 HP de escudo.
        if (type == CERAMICO) {
            int absorbed = Math.min(damage, (int) hp);
            hp -= absorbed;
            int overflow = damage - absorbed;
            if (hp > 0) return 0;

            hp = 0;
            popped = true;
            game.gold += REWARD[CERAMICO];

            // Solo el daño que sobra del escudo pasa a los tres Arcoíris.
            return splitDamage(new int[]{ARCOIRIS, ARCOIRIS, ARCOIRIS}, overflow, srcTower, output);
        }

        int remaining = damage;
        while (remaining > 0 && hp > 0) {
            remaining--;
            game.gold += REWARD[type];

            switch (type) {
                case ARCOIRIS:
                    hp = 0; popped = true;
                    return splitDamage(new int[]{CEBRA, CEBRA, CEBRA}, remaining, srcTower, output);
                case CEBRA:
                    hp = 0; popped = true;
                    return splitDamage(new int[]{NEGRO, NEGRO, BLANCO, BLANCO}, remaining, srcTower, output);
                case PLOMO:
                    hp = 0; popped = true;
                    return splitDamage(new int[]{NEGRO, NEGRO, NEGRO}, remaining, srcTower, output);
                case PURPURA:
                    hp = 0; popped = true;
                    return splitDamage(new int[]{ROSA, ROSA, ROSA, ROSA}, remaining, srcTower, output);
                case BLANCO:
                case NEGRO:
                    hp = 0; popped = true;
                    return splitDamage(new int[]{ROSA, ROSA, ROSA}, remaining, srcTower, output);
                case ROSA: setType(AMARILLO); break;
                case AMARILLO: setType(VERDE); break;
                case VERDE: setType(AZUL); break;
                case AZUL: setType(ROJO); break;
                case ROJO:
                    hp = 0; popped = true;
                    break;
            }
        }
        return remaining;
    }

    /**
     * Reparte un único fondo de daño entre los hijos, de forma equilibrada.
     * Por ejemplo, 5 puntos entre 2 hijos = 2 y 3, nunca 5 y 5.
     * Si una rama genera más hijos, el sobrante sigue propagándose solo por esa rama.
     */
    private int splitDamage(int[] childTypes, int damage, Tower srcTower, List<Enemy> output) {
        if (damage <= 0) {
            for (int i = 0; i < childTypes.length; i++) spawnChild(childTypes[i], i * 6, output);
            return 0;
        }

        int remaining = damage;
        int childrenLeft = childTypes.length;

        for (int i = 0; i < childTypes.length; i++) {
            Enemy child = createChild(childTypes[i], i * 6);

            // Reparto equilibrado del daño que queda: 5 -> 2 + 3.
            int share = (remaining + childrenLeft - 1) / childrenLeft;
            int overflow = child.applyDamage(share, srcTower, output);
            remaining -= share - overflow;
            childrenLeft--;

            if (child.hp > 0 && !child.leaked) output.add(child);
        }
        return remaining;
    }

    private Enemy createChild(int cType, int offset) {
        Enemy child = new Enemy(game, cType, camo, regen, game.waves.wave);
        child.x = this.x - offset * 0.2;
        child.y = this.y - offset * 0.2;
        child.dist = Math.max(0, this.dist - offset);
        child.idx = this.idx;
        child.revealed = this.revealed;
        return child;
    }

    private void spawnChild(int cType, int offset, List<Enemy> output) {
        output.add(createChild(cType, offset));
    }

    void slow(int ticks, double f) {
        if (isBoss()) f = 1 - (1 - f) * 0.5;
        slowF = f; slowT = Math.max(slowT, ticks);
    }

    double curSpeed() { return speed * (slowT > 0 ? slowF : 1); }

    private boolean step(double d) {
        while (d > 0) {
            Waypoint w = game.path.get(idx);
            double dx = w.x - x, dy = w.y - y, len = Math.hypot(dx, dy);
            if (len <= d) {
                x = w.x; y = w.y; dist += len; d -= len;
                if (++idx >= game.path.size()) return true;
            } else {
                x += dx / len * d; y += dy / len * d; dist += d;
                return false;
            }
        }
        return false;
    }

    boolean move() {
        if (hp <= 0) return false;
        bossT++;
        regenTimer++;

        // Mecánica Regenerativa (Si pasa 3s sin recibir daño, sube de capa)
        if (regen && regenTimer >= 180 && type < initialType) {
            regenTimer = 0;
            type++;
            maxHp = HP[type];
            hp = maxHp;
            speed = SPEED[type];
            game.fx.add(new Fx(x, y, 16, 12, new Color(80, 240, 120)));
        }

        double sp = curSpeed();
        if (slowT > 0) slowT--;
        return step(sp);
    }

    double[] predict(double ticks) {
        double ox = x, oy = y, od = dist;
        int oi = idx;
        step(curSpeed() * ticks);
        double[] p = {x, y};
        x = ox; y = oy; dist = od; idx = oi;
        return p;
    }

    void pop(List<Enemy> pending) {
        if (type == MOAB) {
            for (int i = 0; i < 4; i++) {
                Enemy child = new Enemy(game, CERAMICO, camo, regen, game.waves.wave);
                child.x = this.x - i * 2; child.y = this.y - i * 2;
                child.dist = Math.max(0, this.dist - i * 6); child.idx = this.idx;
                pending.add(child);
            }
        }
    }

    void draw(Graphics2D g) {
        int cx = (int) x, cy = (int) y;
        if (isBoss()) { drawBoss(g, cx, cy); return; }

        Color m = COL[type];
        int sc = (type == CERAMICO || type == PLOMO) ? 4 : 3;
        Gfx.sprite(g, SLIME, "omhwk", new Color[]{m.darker().darker(), m, m.brighter(), Color.WHITE, Color.BLACK}, cx, cy, sc);

        if (type == PLOMO) {
            g.setColor(new Color(200, 210, 220, 180));
            g.fillRect(cx - 8, cy - 8, 4, 4);
        } else if (type == CEBRA) {
            g.setColor(Color.WHITE); g.fillRect(cx - 6, cy - 4, 12, 3);
            g.setColor(Color.BLACK); g.fillRect(cx - 6, cy, 12, 3);
        } else if (type == ARCOIRIS) {
            g.setColor(Color.RED); g.fillRect(cx - 8, cy - 6, 16, 2);
            g.setColor(Color.YELLOW); g.fillRect(cx - 8, cy - 4, 16, 2);
            g.setColor(Color.CYAN); g.fillRect(cx - 8, cy - 2, 16, 2);
        }

        if (camo && !revealed) {
            g.setColor(new Color(30, 90, 30, 160));
            g.fillRect(cx - 10, cy - 12, 20, 4);
        }
        if (regen) {
            g.setColor(new Color(230, 40, 80));
            g.fillOval(cx - 3, cy - 16, 6, 6);
        }

        if (slowT > 0) { g.setColor(new Color(140, 215, 255, 110)); g.fillOval(cx - 14, cy - 14, 28, 28); }
        if (hp < maxHp) Gfx.bar(g, cx - 12, cy - 20, 24, 3, hp / maxHp);
    }

    private void drawBoss(Graphics2D g, int cx, int cy) {
        Color m = COL[MOAB];
        Gfx.sprite(g, SLIME, "omhwk", new Color[]{m.darker().darker(), m, m.brighter(), Color.WHITE, Color.BLACK}, cx, cy, 9);
        g.setColor(new Color(255, 210, 50));
        g.fillRect(cx - 18, cy - 44, 36, 10);
        for (int i = 0; i < 3; i++) g.fillRect(cx - 18 + i * 14, cy - 54, 8, 10);
        if (slowT > 0) { g.setColor(new Color(140, 215, 255, 100)); g.fillOval(cx - 48, cy - 38, 96, 76); }
        Gfx.bar(g, cx - 50, cy - 68, 100, 8, hp / maxHp);
    }
}

/** Torres: Arco, Espada, Lanza, Cañón, Báculo, Lupa y Satélite. */
final class Tower {
    static final int MAXLV = 6, SPLASH = 60;
    static final String[] NAMES = {"Arco", "Espada", "Lanza", "Ca\u00f1\u00f3n", "B\u00e1culo", "Lupa", "Sat\u00e9lite"};
    static final String[] DESC = {"R\u00e1pido, 1 obj", "Tajo circular", "Perfora l\u00ednea",
            "Da\u00f1o \u00e1rea", "Ralentiza", "Revela y da\u00f1a", "L\u00e1ser orbital"};
    static final int[] COST = {100, 150, 200, 300, 250, 180, 350};
    // Balance: todas las torres parten de 1 de daño.
    static final double[] DMG = {1, 1, 1, 1, 1, 1, 1};

    // El alcance se mantiene sin cambios.
    static final double[] RNG = {150, 85, 230, 180, 160, 170, 1200};

    // Mayor CD = ataques más lentos.
    static final double[] CD = {60, 100, 140, 180, 100, 60, 1};

    // Coste de las 5 mejoras: nivel 1->2, 2->3, ..., 5->6.
    static final int[][] UPGRADE_COST = {
            {100, 500, 2000, 15000, 100000}, // Arco
            {120, 550, 2200, 16000, 105000}, // Espada
            {130, 600, 2400, 17000, 110000}, // Lanza
            {150, 700, 2800, 19000, 120000}, // Cañón
            {110, 500, 2100, 15500, 100000}, // Báculo
            {120, 550, 2300, 16500, 105000}, // Lupa
            {150, 700, 2800, 20000, 120000}  // Satélite
    };
    static final Color[] ACC = {new Color(80, 200, 90), new Color(220, 70, 70), new Color(80, 140, 240),
            new Color(235, 140, 40), new Color(190, 90, 230), new Color(240, 220, 80), new Color(0, 220, 255)};

    final GamePanel game;
    final int type;
    final double x, y;
    final int[] lv = {1, 1, 1}; // 0: Daño/Efecto, 1: Alcance, 2: Cadencia
    double ang = -Math.PI / 2;
    int cd, slash, spent;

    // Campos de Satélite
    Enemy focusTarget;
    int focusTicks;
    double dmgAcc;

    Tower(GamePanel game, int type, int x, int y) {
        this.game = game; this.type = type; this.x = x; this.y = y; spent = COST[type];
    }

    // El daño sube exactamente +1 por cada nivel de daño: nivel 1=1, ..., nivel 6=6.
    double dmg(int l) { return DMG[type] + (l - 1); }
    double range(int l) { return RNG[type] * (1 + 0.12 * (l - 1)); }
    double cool(int l) { return Math.max(4, CD[type] * Math.pow(0.86, l - 1)); }
    double dmg() { return dmg(lv[0]); }
    double range() { return range(lv[1]); }
    double cool() { return cool(lv[2]); }
    int upCost(int k) { return UPGRADE_COST[type][lv[k] - 1]; }

    void update() {
        if (cd > 0) cd--;
        if (slash > 0) { slash--; ang += 0.5; }

        double r = range();

        // LUPA: Revela camuflaje y ataca activamente
        if (type == 5) {
            for (Enemy e : game.enemies) {
                if (e.hp > 0 && Math.hypot(e.x - x, e.y - y) <= r) {
                    e.revealed = true;
                }
            }
            if (cd <= 0) {
                Enemy tgt = null;
                for (Enemy e : game.enemies) {
                    if (e.hp > 0 && e.x >= 0 && Math.hypot(e.x - x, e.y - y) <= r) {
                        if (tgt == null || e.dist > tgt.dist) tgt = e;
                    }
                }
                if (tgt != null) {
                    cd = (int) cool();
                    ang = Math.atan2(tgt.y - y, tgt.x - x);
                    game.shots.add(new Projectile(game, this, tgt));
                }
            }
            return;
        }

        // SATÉLITE: L\u00e1ser Enfocado con Rampa de Carga
        if (type == 6) {
            if (focusTarget != null) {
                if (focusTarget.hp <= 0 || focusTarget.leaked || Math.hypot(focusTarget.x - x, focusTarget.y - y) > r) {
                    focusTarget = null;
                    focusTicks = 0;
                    dmgAcc = 0;
                }
            }
            if (focusTarget == null) {
                Enemy best = null;
                for (Enemy e : game.enemies) {
                    if (e.hp > 0 && e.x >= 0 && (e.revealed || canTargetCamo(e)) && Math.hypot(e.x - x, e.y - y) <= r) {
                        if (best == null || e.dist > best.dist) best = e;
                    }
                }
                if (best != focusTarget) {
                    focusTarget = best;
                    focusTicks = 0;
                    dmgAcc = 0;
                }
            }
            if (focusTarget != null) {
                ang = Math.atan2(focusTarget.y - y, focusTarget.x - x);
                focusTicks++;
                double sec = focusTicks / 60.0;
                if (sec >= 3.0) {
                    int fullSec = (int) Math.floor(sec); // 3, 4, 5...
                    double rate = (fullSec - 2) * dmg(); // La rampa escala con el nivel de daño
                    dmgAcc += rate / 60.0;
                    while (dmgAcc >= 1.0 && focusTarget != null && focusTarget.hp > 0) {
                        focusTarget.hurt(1, this);
                        dmgAcc -= 1.0;
                        if (focusTarget.hp <= 0) {
                            focusTarget = null;
                            focusTicks = 0;
                            dmgAcc = 0;
                            break;
                        }
                    }
                }
            }
            return;
        }

        if (cd > 0) return;

        Enemy tgt = null;
        for (Enemy e : game.enemies) {
            if (e.hp > 0 && e.x >= 0 && (e.revealed || canTargetCamo(e)) && Math.hypot(e.x - x, e.y - y) <= r) {
                if (tgt == null || e.dist > tgt.dist) tgt = e;
            }
        }
        if (tgt == null) return;

        cd = (int) cool();
        if (type == 1) { // Espada
            slash = 12;
            for (Enemy e : game.enemies)
                if (e.hp > 0 && (e.revealed || canTargetCamo(e)) && Math.hypot(e.x - x, e.y - y) <= r)
                    e.hurt(dmg(), this);
        } else {
            ang = Math.atan2(tgt.y - y, tgt.x - x);
            game.shots.add(new Projectile(game, this, tgt));
        }
    }

    boolean canTargetCamo(Enemy e) {
        if (!e.camo) return true;
        for (Tower t : game.towers) {
            if (t.type == 5 && Math.hypot(e.x - t.x, e.y - t.y) <= t.range()) return true;
        }
        return false;
    }

    void draw(Graphics2D g) {
        paint(g, type, lv, (int) x, (int) y, ang);
        if (type == 1 && slash > 0) {
            int r = (int) range();
            g.setColor(new Color(255, 255, 255, 150 * slash / 12));
            g.setStroke(new BasicStroke(4));
            g.drawOval((int) x - r, (int) y - r, 2 * r, 2 * r);
            g.setStroke(new BasicStroke(1));
        }

        // Renderizado del Láser de Satélite
        if (type == 6 && focusTarget != null && focusTarget.hp > 0) {
            int tx = (int) focusTarget.x, ty = (int) focusTarget.y, sx = (int) x, sy = (int) y;
            if (focusTicks < 180) {
                g.setColor(new Color(0, 240, 255, 140));
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 6}, 0));
                g.drawLine(sx, sy, tx, ty);
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(255, 220, 50, 180));
                g.drawOval(tx - 10, ty - 10, 20, 20);
                g.drawLine(tx - 14, ty, tx + 14, ty);
                g.drawLine(tx, ty - 14, tx, ty + 14);
            } else {
                double sec = focusTicks / 60.0;
                int bw = Math.min(14, 4 + (int) (sec - 3));
                g.setColor(new Color(0, 220, 255, 160));
                g.setStroke(new BasicStroke(bw + 4));
                g.drawLine(sx, sy, tx, ty);
                g.setColor(new Color(220, 255, 255));
                g.setStroke(new BasicStroke(bw));
                g.drawLine(sx, sy, tx, ty);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(Math.max(1, bw / 3)));
                g.drawLine(sx, sy, tx, ty);
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(255, 100, 255, 220));
                g.fillOval(tx - bw - 2, ty - bw - 2, (bw + 2) * 2, (bw + 2) * 2);
                g.setColor(Color.WHITE);
                g.fillOval(tx - bw / 2, ty - bw / 2, bw, bw);
            }
        }
    }

    static void paint(Graphics2D g, int type, int[] lv, int cx, int cy, double ang) {
        boolean maxed = lv[0] + lv[1] + lv[2] >= 3 * MAXLV;
        g.setColor(maxed ? new Color(255, 200, 40) : new Color(40, 40, 50)); g.fillRect(cx - 17, cy - 17, 34, 34);
        g.setColor(new Color(125, 125, 135)); g.fillRect(cx - 15, cy - 15, 30, 30);
        g.setColor(ACC[type]); g.fillRect(cx - 15, cy - 15, 6, 6); g.fillRect(cx + 9, cy - 15, 6, 6);
        weapon(g, type, cx, cy, ang, 1.0);
        Color[] bc = {new Color(255, 90, 90), new Color(90, 160, 255), new Color(110, 230, 110)};
        for (int k = 0; k < 3; k++) { g.setColor(bc[k]); g.fillRect(cx - 14, cy + 7 + k * 3, lv[k] * 5, 2); }
    }

    static void weapon(Graphics2D g0, int type, int cx, int cy, double ang, double sc) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy); g.rotate(ang); g.scale(sc, sc);
        switch (type) {
            case 0: // Arco
                g.setColor(new Color(130, 90, 50));
                for (int i = -9; i <= 9; i += 3) g.fillRect(9 - i * i / 9, i - 1, 3, 3);
                g.setColor(new Color(220, 220, 220)); g.fillRect(1, -9, 1, 18);
                break;
            case 1: // Espada
                g.setColor(new Color(215, 222, 232)); g.fillRect(3, -2, 15, 4);
                g.setColor(new Color(240, 190, 50)); g.fillRect(0, -6, 3, 12);
                break;
            case 2: // Lanza
                g.setColor(new Color(150, 105, 60)); g.fillRect(-12, -1, 26, 2);
                g.setColor(new Color(215, 220, 230)); g.fillRect(14, -3, 8, 6);
                break;
            case 3: // Cañón
                g.setColor(new Color(60, 60, 72)); g.fillRect(-8, -6, 22, 12);
                g.setColor(new Color(35, 35, 44)); g.fillRect(14, -7, 4, 14);
                break;
            case 4: // Báculo
                g.setColor(new Color(130, 90, 50)); g.fillRect(-12, -1, 24, 2);
                g.setColor(new Color(120, 230, 255)); g.fillOval(11, -4, 8, 8);
                break;
            case 5: // Lupa
                g.setColor(new Color(130, 85, 40)); g.fillRect(-8, -2, 12, 4);
                g.setColor(new Color(200, 200, 210)); g.drawOval(4, -8, 14, 14);
                g.setColor(new Color(140, 215, 255, 180)); g.fillOval(5, -7, 12, 12);
                g.setColor(Color.WHITE); g.fillRect(7, -5, 3, 3);
                break;
            case 6: // Satélite
                g.setColor(new Color(70, 75, 90)); g.fillRect(-8, -8, 16, 16);
                g.setColor(new Color(0, 200, 255)); g.fillRect(-4, -4, 8, 8);
                g.setColor(new Color(30, 80, 180)); g.fillRect(-14, -6, 5, 12); g.fillRect(9, -6, 5, 12);
                g.setColor(Color.WHITE); g.fillOval(4, -3, 6, 6);
                break;
        }
        g.dispose();
    }
}

/** Proyectiles del juego. */
final class Projectile {
    final GamePanel game;
    final Tower parent;
    final int type;
    final List<Enemy> hit = new ArrayList<>();
    final Enemy target;
    double x, y, vx, vy, dmg, trav, max, tx, ty;
    int pierce;
    boolean dead;

    Projectile(GamePanel game, Tower t, Enemy tg) {
        this.game = game; this.parent = t; type = t.type; target = tg;
        x = t.x; y = t.y; dmg = t.dmg();
        double s = type == 0 ? 9 : type == 2 ? 13 : type == 3 ? 6 : type == 5 ? 14 : 7;
        if (type == 3) {
            double[] p = tg.predict(Math.hypot(tg.x - x, tg.y - y) / s);
            tx = p[0]; ty = p[1];
            max = Math.hypot(tx - x, ty - y);
        } else {
            tx = tg.x; ty = tg.y;
            max = t.range() * 1.5;
        }
        double a = Math.atan2(ty - y, tx - x);
        vx = Math.cos(a) * s; vy = Math.sin(a) * s;
        pierce = type == 2 ? 2 + t.lv[0] : 1;
    }

    void update() {
        if (type == 0 || type == 4 || type == 5) {
            if (target.hp <= 0) { dead = true; return; }
            double s = type == 0 ? 9 : type == 5 ? 14 : 7, dx = target.x - x, dy = target.y - y, d = Math.hypot(dx, dy);
            if (d <= s + 2) {
                target.hurt(dmg, parent);
                if (type == 4) {
                    target.slow(150, 0.5);
                    game.fx.add(new Fx(target.x, target.y, 24, 14, new Color(190, 110, 255)));
                } else if (type == 5) {
                    game.fx.add(new Fx(target.x, target.y, 18, 10, new Color(255, 240, 120)));
                }
                dead = true;
                return;
            }
            vx = dx / d * s; vy = dy / d * s;
        }

        x += vx; y += vy; trav += Math.hypot(vx, vy);

        if (type == 2) { // Lanza
            for (Enemy e : game.enemies)
                if (e.hp > 0 && !hit.contains(e) && Math.hypot(e.x - x, e.y - y) < e.getRadius() + 6) {
                    e.hurt(dmg, parent); hit.add(e);
                    if (--pierce <= 0) { dead = true; break; }
                }
            if (trav > max) dead = true;
        } else if (type == 3 && trav >= max) { // Cañón
            for (Enemy e : game.enemies) {
                if (e.hp > 0 && Math.hypot(e.x - tx, e.y - ty) <= Tower.SPLASH + e.getRadius()) {
                    if (e.type == Enemy.PLOMO) {
                        e.hurt(e.hp + 10, parent);
                    } else {
                        e.hurt(dmg, parent);
                    }
                }
            }
            game.fx.add(new Fx(tx, ty, Tower.SPLASH, 18, new Color(255, 150, 40)));
            dead = true;
        }
    }

    void draw(Graphics2D g0) {
        int px = (int) x, py = (int) y;
        if (type == 3) {
            double p = Math.min(1, trav / Math.max(1, max)), h = Math.sin(Math.PI * p) * 28;
            int by = (int) (py - h);
            g0.setColor(new Color(0, 0, 0, 80)); g0.fillOval(px - 5, py - 2, 10, 5);
            g0.setColor(new Color(40, 40, 48)); g0.fillOval(px - 5, by - 5, 10, 10);
            return;
        }
        if (type == 4) {
            g0.setColor(new Color(170, 90, 230, 110)); g0.fillOval(px - 8, py - 8, 16, 16);
            g0.setColor(new Color(120, 230, 255)); g0.fillOval(px - 5, py - 5, 10, 10);
            return;
        }
        if (type == 5) { // Rayo de Lupa
            g0.setColor(new Color(255, 240, 100, 200)); g0.fillOval(px - 6, py - 6, 12, 12);
            g0.setColor(Color.WHITE); g0.fillOval(px - 3, py - 3, 6, 6);
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y); g.rotate(Math.atan2(vy, vx));
        if (type == 0) {
            g.setColor(new Color(150, 110, 60)); g.fillRect(-8, -1, 12, 2);
            g.setColor(new Color(220, 60, 60)); g.fillRect(-9, -2, 3, 4);
        } else {
            g.setColor(new Color(150, 105, 60)); g.fillRect(-14, -1, 22, 2);
            g.setColor(new Color(225, 230, 240)); g.fillRect(8, -3, 4, 6);
        }
        g.dispose();
    }
}

/** Gestiona las 40 oleadas. */
final class WaveManager {
    static final int TOTAL = 40;
    final GamePanel game;
    final List<EnemySpawn> queue = new ArrayList<>();
    int wave, spawnTimer, autoPauseTimer;
    boolean active, auto;

    WaveManager(GamePanel game) { this.game = game; }

    void reset() { queue.clear(); wave = 0; active = false; autoPauseTimer = 0; spawnTimer = 0; }

    void start() {
        if (active || wave >= TOTAL) return;
        wave++; active = true; spawnTimer = 0; autoPauseTimer = 0;
        queue.clear();
        loadWaveSpawns(wave);
        if (wave == TOTAL) game.flash("\u00a1JEFE FINAL! Oleada 40");
    }

    void update() {
        if (active) {
            if (!queue.isEmpty() && --spawnTimer <= 0) {
                EnemySpawn s = queue.remove(0);
                game.enemies.add(new Enemy(game, s.type, s.camo, s.regen, wave));
                spawnTimer = (s.type == Enemy.MOAB) ? 90 : Math.max(6, 28 - wave / 2);
            }
            if (queue.isEmpty() && game.enemies.isEmpty()) {
                active = false;
                autoPauseTimer = 90;
                game.waveCleared();
            }
        } else if (auto && wave < TOTAL) {
            if (autoPauseTimer > 0) {
                autoPauseTimer--;
            } else {
                start();
            }
        }
    }

    private void loadWaveSpawns(int w) {
        switch (w) {
            case 1: add(Enemy.ROJO, 20); break;
            case 2: add(Enemy.ROJO, 35); break;
            case 3: add(Enemy.ROJO, 25); add(Enemy.AZUL, 5); break;
            case 4: add(Enemy.ROJO, 35); add(Enemy.AZUL, 18); break;
            case 5: add(Enemy.ROJO, 5); add(Enemy.AZUL, 27); break;
            case 6: add(Enemy.ROJO, 15); add(Enemy.AZUL, 15); add(Enemy.VERDE, 4); break;
            case 7: add(Enemy.ROJO, 20); add(Enemy.AZUL, 20); add(Enemy.VERDE, 5); break;
            case 8: add(Enemy.ROJO, 10); add(Enemy.AZUL, 20); add(Enemy.VERDE, 14); break;
            case 9: add(Enemy.VERDE, 30); break;
            case 10: add(Enemy.AZUL, 102); break;
            case 11: add(Enemy.ROJO, 10); add(Enemy.AZUL, 10); add(Enemy.VERDE, 12); add(Enemy.AMARILLO, 3); break;
            case 12: add(Enemy.AZUL, 15); add(Enemy.VERDE, 10); add(Enemy.AMARILLO, 5); break;
            case 13: add(Enemy.AZUL, 50); add(Enemy.VERDE, 23); break;
            case 14: add(Enemy.ROJO, 49); add(Enemy.AZUL, 15); add(Enemy.VERDE, 10); add(Enemy.AMARILLO, 9); break;
            case 15: add(Enemy.ROJO, 20); add(Enemy.AZUL, 15); add(Enemy.VERDE, 12); add(Enemy.AMARILLO, 10); add(Enemy.ROSA, 5); break;
            case 16: add(Enemy.VERDE, 40); add(Enemy.AMARILLO, 8); break;
            case 17: add(Enemy.AMARILLO, false, true, 12); break;
            case 18: add(Enemy.VERDE, 80); break;
            case 19: add(Enemy.VERDE, 10); add(Enemy.AMARILLO, 4); add(Enemy.AMARILLO, false, true, 5); add(Enemy.ROSA, 15); break;
            case 20: add(Enemy.NEGRO, 6); break;
            case 21: add(Enemy.AMARILLO, 40); add(Enemy.ROSA, 14); break;
            case 22: add(Enemy.BLANCO, 16); break;
            case 23: add(Enemy.NEGRO, 7); add(Enemy.BLANCO, 7); break;
            case 24: add(Enemy.AZUL, 20); add(Enemy.VERDE, true, false, 1); break;
            case 25: add(Enemy.AMARILLO, false, true, 25); add(Enemy.PURPURA, 10); break;
            case 26: add(Enemy.ROSA, 23); add(Enemy.CEBRA, 4); break;
            case 27: add(Enemy.ROJO, 100); add(Enemy.AZUL, 60); add(Enemy.VERDE, 45); add(Enemy.AMARILLO, 45); break;
            case 28: add(Enemy.PLOMO, 6); break;
            case 29: add(Enemy.AMARILLO, 50); add(Enemy.AMARILLO, false, true, 15); break;
            case 30: add(Enemy.PLOMO, 9); break;
            case 31: add(Enemy.NEGRO, 8); add(Enemy.BLANCO, 8); add(Enemy.CEBRA, 8); add(Enemy.CEBRA, false, true, 2); break;
            case 32: add(Enemy.NEGRO, 15); add(Enemy.BLANCO, 20); add(Enemy.PURPURA, 10); break;
            case 33: add(Enemy.ROJO, true, false, 20); add(Enemy.AMARILLO, true, false, 13); break;
            case 34: add(Enemy.AMARILLO, 160); add(Enemy.CEBRA, 6); break;
            case 35: add(Enemy.ROSA, 35); add(Enemy.NEGRO, 30); add(Enemy.BLANCO, 25); add(Enemy.ARCOIRIS, 5); break;
            case 36: add(Enemy.ROSA, 140); add(Enemy.VERDE, true, true, 20); break;
            case 37: add(Enemy.NEGRO, 25); add(Enemy.BLANCO, 25); add(Enemy.PLOMO, 15); add(Enemy.CEBRA, 10); add(Enemy.BLANCO, true, false, 7); break;
            case 38: add(Enemy.ROSA, 42); add(Enemy.BLANCO, 17); add(Enemy.PLOMO, 14); add(Enemy.CEBRA, 10); add(Enemy.CERAMICO, 2); break;
            case 39: add(Enemy.NEGRO, 10); add(Enemy.BLANCO, 10); add(Enemy.CEBRA, 20); add(Enemy.ARCOIRIS, 18); add(Enemy.ARCOIRIS, false, true, 2); break;
            case 40: add(Enemy.MOAB, 1); break;
        }
    }

    private void add(int t, int count) { add(t, false, false, count); }
    private void add(int t, boolean camo, boolean regen, int count) {
        for (int i = 0; i < count; i++) queue.add(new EnemySpawn(t, camo, regen));
    }
}

/** Panel principal y bucle del juego. */
final class GamePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    static final int BW = 800, BH = 640, PW = 280, PATH_W = 44;
    static final String[] HEART = {".rr.rr.", "rrrrrrr", "rrrrrrr", ".rrrrr.", "..rrr..", "...r..."};

    final List<Waypoint> path = new ArrayList<>();
    final List<Tower> towers = new ArrayList<>();
    final List<Enemy> enemies = new ArrayList<>();
    final List<Enemy> pendingEnemies = new ArrayList<>();
    final List<Projectile> shots = new ArrayList<>();
    final List<Fx> fx = new ArrayList<>();
    final ShopButton[] shop = new ShopButton[7];
    final Rectangle[] upBtn = new Rectangle[3];
    final Rectangle sellBtn = new Rectangle(BW + 14, 452, 252, 30), waveBtn = new Rectangle(BW + 10, 515, 260, 36),
            autoBtn = new Rectangle(BW + 10, 556, 260, 30),
            speedOffBtn = new Rectangle(BW + 10, 592, 82, 30),
            speed2Btn = new Rectangle(BW + 99, 592, 82, 30),
            speed10Btn = new Rectangle(BW + 188, 592, 82, 30),
            restartBtn = new Rectangle((BW + PW) / 2 - 110, 400, 220, 44);
    final WaveManager waves = new WaveManager(this);
    Image map;
    long seed;
    int lives, gold, speed = 1, placing = -1, mx, my, msgTimer;
    boolean gameOver, victory, dragMode;
    Tower selected;
    String msg = "";

    GamePanel() {
        setPreferredSize(new Dimension(BW + PW, BH));
        setFocusable(true);
        int[][] pts = {{-20, 80}, {700, 80}, {700, 230}, {100, 230}, {100, 380}, {700, 380}, {700, 530}, {-20, 530}};
        for (int[] p : pts) path.add(new Waypoint(p[0], p[1]));

        // Tienda: 7 torres en cuadrícula
        for (int i = 0; i < 7; i++) {
            int col = i % 3, row = i / 3;
            shop[i] = new ShopButton(BW + 14 + col * 84, 108 + row * 50, 78, 46, i);
        }
        for (int k = 0; k < 3; k++) upBtn[k] = new Rectangle(BW + 14, 296 + 52 * k, 252, 44);
        reset();

        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int px = e.getX(), py = e.getY();
                mx = px; my = py;
                if (gameOver || victory) { if (restartBtn.contains(px, py)) reset(); return; }
                if (SwingUtilities.isRightMouseButton(e)) { placing = -1; dragMode = false; selected = null; return; }
                if (px < BW) {
                    if (placing >= 0) { tryPlace(px, py); return; }
                    selected = null;
                    for (Tower t : towers) if (Math.hypot(t.x - px, t.y - py) <= 18) selected = t;
                    return;
                }
                for (ShopButton b : shop)
                    if (b.hit(px, py)) {
                        if (gold < Tower.COST[b.type]) { flash("Oro insuficiente"); return; }
                        placing = b.type; selected = null; dragMode = true;
                        return;
                    }
                if (selected != null) {
                    for (int k = 0; k < 3; k++) if (upBtn[k].contains(px, py)) { upgrade(k); return; }
                    if (sellBtn.contains(px, py)) {
                        gold += (int) (selected.spent * 0.7); towers.remove(selected); selected = null;
                        return;
                    }
                }
                if (waveBtn.contains(px, py)) waves.start();
                else if (autoBtn.contains(px, py)) waves.auto = !waves.auto;
                else if (speedOffBtn.contains(px, py)) speed = 1;
                else if (speed2Btn.contains(px, py)) speed = 2;
                else if (speed10Btn.contains(px, py)) speed = 10;
            }

            public void mouseReleased(MouseEvent e) {
                if (!dragMode) return;
                dragMode = false;
                if (e.getX() < BW && placing >= 0 && !tryPlace(e.getX(), e.getY())) placing = -1;
            }

            public void mouseMoved(MouseEvent e) { mx = e.getX(); my = e.getY(); }
            public void mouseDragged(MouseEvent e) { mx = e.getX(); my = e.getY(); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { placing = -1; dragMode = false; }
                else if (e.getKeyCode() == KeyEvent.VK_SPACE) waves.start();
                else if (e.getKeyCode() == KeyEvent.VK_A) waves.auto = !waves.auto;
                else if (e.getKeyCode() == KeyEvent.VK_1) speed = 1;
                else if (e.getKeyCode() == KeyEvent.VK_2) speed = 2;
                else if (e.getKeyCode() == KeyEvent.VK_0) speed = 10;
            }
        });
        new Timer(16, e -> { for (int i = 0; i < speed; i++) update(); repaint(); }).start();
    }

    void toggleSpeed() {
        if (speed == 1) speed = 2;
        else if (speed == 2) speed = 10;
        else speed = 1;
    }

    void reset() {
        towers.clear(); enemies.clear(); pendingEnemies.clear(); shots.clear(); fx.clear();
        waves.reset();
        lives = 150; gold = 350; gameOver = false; victory = false;
        selected = null; placing = -1; dragMode = false; speed = 1;
        flash("\u00a1Coloca torres para defender el camino!");
    }

    void flash(String s) { msg = s; msgTimer = 140; }

    void waveCleared() {
        int bonus = 100 + waves.wave * 12;
        gold += bonus;
        if (waves.wave >= WaveManager.TOTAL) victory = true;
        else flash("\u00a1Oleada " + waves.wave + " completada! +" + bonus + "g");
    }

    boolean onPath(double x, double y, double m) {
        for (int i = 0; i < path.size() - 1; i++) {
            Waypoint a = path.get(i), b = path.get(i + 1);
            double dx = Math.max(Math.max(Math.min(a.x, b.x) - x, 0), x - Math.max(a.x, b.x));
            double dy = Math.max(Math.max(Math.min(a.y, b.y) - y, 0), y - Math.max(a.y, b.y));
            if (Math.hypot(dx, dy) < PATH_W / 2.0 + m) return true;
        }
        return false;
    }

    boolean valid(int x, int y) {
        if (x < 18 || x > BW - 18 || y < 18 || y > BH - 18 || onPath(x, y, 18)) return false;
        for (Tower t : towers) if (Math.hypot(t.x - x, t.y - y) < 36) return false;
        return true;
    }

    boolean tryPlace(int x, int y) {
        if (!valid(x, y)) { flash("Zona no v\u00e1lida"); return false; }
        if (gold < Tower.COST[placing]) { flash("Oro insuficiente"); return false; }
        gold -= Tower.COST[placing];
        Tower t = new Tower(this, placing, x, y);
        towers.add(t); selected = t; placing = -1;
        return true;
    }

    void upgrade(int k) {
        Tower t = selected;
        if (t == null || t.lv[k] >= Tower.MAXLV) return;
        int c = t.upCost(k);
        if (gold < c) { flash("Oro insuficiente"); return; }
        gold -= c; t.spent += c; t.lv[k]++;
    }

    void update() {
        if (gameOver || victory) return;
        if (msgTimer > 0) msgTimer--;
        waves.update();

        // Limpiar aquí, antes de que las torres puedan generar hijos.
        // Antes se vaciaba después de disparar y se borraban inmediatamente.
        pendingEnemies.clear();

        for (Tower t : towers) t.update();
        for (Projectile p : shots) p.update();
        shots.removeIf(p -> p.dead);

        for (Enemy e : enemies) {
            if (e.move()) {
                lives -= Enemy.LIVES_COST[e.type];
                e.hp = 0;
                e.leaked = true;
            } else if (e.hp <= 0 && !e.leaked && !e.popped) {
                e.popped = true;
                gold += Enemy.REWARD[e.type];
                e.pop(pendingEnemies);
            }
        }
        enemies.removeIf(e -> e.hp <= 0);
        enemies.addAll(pendingEnemies);
        pendingEnemies.clear();

        for (Fx f : fx) f.life--;
        fx.removeIf(f -> f.life <= 0);
        if (lives <= 0) { lives = 0; gameOver = true; }
    }

    int rnd(int n) {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return (int) ((seed >>> 33) % n);
    }

    void drawMap(Graphics2D g) {
        seed = 7;
        Color[] gr = {new Color(78, 162, 62), new Color(72, 154, 57), new Color(86, 172, 68)};
        for (int y = 0; y < BH; y += 16)
            for (int x = 0; x < BW; x += 16) { g.setColor(gr[rnd(3)]); g.fillRect(x, y, 16, 16); }
        for (int pass = 0; pass < 2; pass++) {
            int h = PATH_W / 2 + (pass == 0 ? 3 : 0);
            g.setColor(pass == 0 ? new Color(96, 66, 38) : new Color(184, 140, 84));
            for (int i = 0; i < path.size() - 1; i++) {
                Waypoint a = path.get(i), b = path.get(i + 1);
                g.fillRect(Math.min(a.x, b.x) - h, Math.min(a.y, b.y) - h, Math.abs(a.x - b.x) + 2 * h, Math.abs(a.y - b.y) + 2 * h);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (map == null) {
            map = createImage(BW, BH);
            if (map != null) { Graphics2D mg = (Graphics2D) map.getGraphics(); drawMap(mg); mg.dispose(); }
        }
        if (map != null) g.drawImage(map, 0, 0, null); else drawMap(g);

        if (selected != null) {
            int r = (int) selected.range(), x = (int) selected.x, y = (int) selected.y;
            g.setColor(new Color(255, 255, 255, 45)); g.fillOval(x - r, y - r, 2 * r, 2 * r);
            g.setColor(new Color(255, 255, 255, 140)); g.drawOval(x - r, y - r, 2 * r, 2 * r);
        }
        for (Tower t : towers) t.draw(g);
        if (selected != null) { g.setColor(Color.YELLOW); g.drawRect((int) selected.x - 19, (int) selected.y - 19, 38, 38); }
        for (Enemy e : enemies) e.draw(g);
        for (Projectile p : shots) p.draw(g);
        for (Fx f : fx) f.draw(g);

        if (placing >= 0 && mx < BW && !gameOver && !victory) {
            boolean ok = valid(mx, my);
            int r = (int) Tower.RNG[placing];
            g.setColor(ok ? new Color(255, 255, 255, 45) : new Color(255, 0, 0, 70));
            g.fillOval(mx - r, my - r, 2 * r, 2 * r);
            Tower.paint(g, placing, new int[]{1, 1, 1}, mx, my, -0.6);
            if (!ok) { g.setColor(new Color(255, 40, 40)); g.drawRect(mx - 18, my - 18, 36, 36); }
        }
        if (msgTimer > 0) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
            int w = g.getFontMetrics().stringWidth(msg);
            g.setColor(new Color(0, 0, 0, 170)); g.fillRect(8, BH - 34, w + 16, 24);
            g.setColor(Color.WHITE); g.drawString(msg, 16, BH - 17);
        }
        drawPanel(g);
        if (gameOver || victory) {
            int cx = (BW + PW) / 2;
            g.setColor(new Color(0, 0, 0, 175)); g.fillRect(0, 0, BW + PW, BH);
            if (victory) {
                Gfx.center(g, "\u00a1VICTORIA!", cx, 290, 60, new Color(255, 215, 60));
                Gfx.center(g, "Completaste las 40 oleadas con " + lives + " vidas", cx, 335, 18, Color.WHITE);
            } else {
                Gfx.center(g, "GAME OVER", cx, 290, 60, new Color(230, 50, 50));
                Gfx.center(g, "Llegaste a la Oleada " + waves.wave + " / " + WaveManager.TOTAL, cx, 335, 18, Color.WHITE);
            }
            Gfx.btn(g, restartBtn, victory ? "Jugar de nuevo" : "Reiniciar", true, new Color(60, 150, 70));
        }
    }

    String upgradeValueText(Tower t, int k) {
        int l = t.lv[k];
        if (l >= Tower.MAXLV) {
            if (k == 0) return "MAX — daño " + (int) t.dmg();
            if (k == 1) return "MAX — alcance " + (int) t.range();
            return "MAX — CD " + String.format(java.util.Locale.US, "%.1f", t.cool());
        }
        if (k == 0) {
            int from = (int) t.dmg();
            return "Daño " + from + " → " + (from + 1) + "  (+1)";
        }
        if (k == 1) {
            int from = (int) Math.round(t.range());
            int to = (int) Math.round(t.range(l + 1));
            return "Alcance " + from + " → " + to + "  (+12%)";
        }
        String from = String.format(java.util.Locale.US, "%.1f", t.cool());
        String to = String.format(java.util.Locale.US, "%.1f", t.cool(l + 1));
        return "CD " + from + "s → " + to + "s  (-14%)";
    }

    void drawPanel(Graphics2D g) {
        g.setColor(new Color(38, 32, 48)); g.fillRect(BW, 0, PW, BH);
        g.setColor(new Color(110, 90, 140)); g.fillRect(BW, 0, 4, BH);
        Gfx.txt(g, "PIXEL TD", BW + 14, 26, 20, new Color(255, 210, 90));
        Gfx.sprite(g, HEART, "r", new Color[]{new Color(230, 50, 70)}, BW + 24, 46, 3);
        Gfx.txt(g, "Vidas: " + lives, BW + 42, 52, 14, Color.WHITE);
        Gfx.txt(g, "Oro: " + gold, BW + 170, 52, 14, new Color(255, 215, 60));
        Gfx.txt(g, "Oleada " + waves.wave + " / " + WaveManager.TOTAL, BW + 14, 76, 14, new Color(190, 220, 255));

        String st = waves.active ? "Enemigos rest: " + (waves.queue.size() + enemies.size()) : (waves.auto ? "Auto-Siguiente en breve..." : "Esperando inicio");
        Gfx.txt(g, st, BW + 14, 94, 11, new Color(190, 185, 205));

        int hover = placing;
        for (ShopButton b : shop) {
            boolean afford = gold >= Tower.COST[b.type], sel = placing == b.type;
            if (b.hit(mx, my)) hover = b.type;
            g.setColor(sel ? new Color(95, 80, 130) : new Color(60, 52, 78)); g.fillRect(b.x, b.y, b.w, b.h);
            g.setColor(sel ? Color.YELLOW : Tower.ACC[b.type].darker()); g.drawRect(b.x, b.y, b.w - 1, b.h - 1);
            Tower.weapon(g, b.type, b.x + 39, b.y + 18, -0.6, 1.0);
            Gfx.center(g, Tower.COST[b.type] + "g", b.x + b.w / 2, b.y + 40, 10, afford ? new Color(255, 215, 60) : new Color(170, 70, 70));
        }
        if (hover >= 0) Gfx.txt(g, Tower.NAMES[hover] + ": " + Tower.DESC[hover], BW + 10, 260, 10, new Color(220, 215, 235));

        g.setColor(new Color(52, 45, 68)); g.fillRect(BW + 6, 268, PW - 12, 238);
        if (selected != null) {
            Tower t = selected;
            Gfx.txt(g, Tower.NAMES[t.type], BW + 16, 286, 15, new Color(255, 210, 90));
            Gfx.txt(g, "MEJORAS", BW + 182, 286, 10, new Color(155, 150, 175));

            String[] an = {t.type == 5 ? "EFECTIVIDAD" : (t.type == 6 ? "RAMPA DAÑO" : "DAÑO"), "ALCANCE", "CADENCIA"};
            Color[] ac = {new Color(160, 55, 55), new Color(50, 95, 170), new Color(50, 145, 75)};
            for (int k = 0; k < 3; k++) {
                int l = t.lv[k];
                boolean more = l < Tower.MAXLV;
                int c = t.upCost(k);
                String value = upgradeValueText(t, k);
                Gfx.upgradeCard(g, upBtn[k], an[k], l, more ? l + 1 : l, value, more ? c : 0,
                        more && gold >= c, ac[k]);
            }
            Gfx.btn(g, sellBtn, "Vender   +" + (int) (t.spent * 0.7) + "g", true, new Color(150, 60, 60));
        } else {
            String[] h = {"Selecciona una torre para ver", "sus opciones o compra una nueva", "en la tienda.", "", "Lupa: Revela y da\u00f1a invisibles", "Sat\u00e9lite: L\u00e1ser rampa orbital", "ESPACIO: Siguiente Oleada", "1: Velocidad normal | 2: x2 | 0: x10"};
            for (int i = 0; i < h.length; i++) Gfx.txt(g, h[i], BW + 14, 288 + i * 18, 11, new Color(190, 185, 205));
        }

        Gfx.btn(g, waveBtn, waves.active ? "Oleada en curso..." : "Iniciar Siguiente Oleada", !waves.active && waves.wave < WaveManager.TOTAL, new Color(60, 150, 70));
        Gfx.btn(g, autoBtn, "Auto-Oleada: " + (waves.auto ? "ON" : "OFF"), true, waves.auto ? new Color(40, 150, 110) : new Color(110, 70, 70));

        Gfx.txt(g, "VELOCIDAD", BW + 10, 586, 9, new Color(170, 165, 185));
        Gfx.btn(g, speedOffBtn, "OFF", speed == 1, new Color(70, 90, 160));
        Gfx.btn(g, speed2Btn, "x2", speed == 2, new Color(50, 150, 80));
        Gfx.btn(g, speed10Btn, "x10", speed == 10, new Color(200, 70, 70));
    }
}