import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class DieToLive extends JPanel implements Runnable, MouseListener, MouseMotionListener, KeyListener {
    // Game Constants
    private static final int GRID_SIZE = 15;
    private static final int CELL_SIZE = 50;
    private static final int BASE_HEALTH = 100;
    private static final int START_COINS = 100;
    
    // Game State
    private enum GameState { BUILD, COMBAT, PHASE2, GAME_OVER, WIN }
    private GameState state = GameState.BUILD;
    private int wave = 0;
    private int coins = START_COINS;
    private int baseHealth = BASE_HEALTH;
    private boolean isPaused = true;
    
    // Tower Data
    private enum TowerType { ARROW, BOMB, ICE, MINIGUN }
    private TowerType selectedTower = TowerType.ARROW;
    private final Map<Point, Tower> towers = new HashMap<>();
    private Tower draggedTower = null;
    private Point dragPoint = null;
    private Tower selectedTowerInstance = null;
    private Point selectedTowerPos = null;
    
    // Zombie Data
    private final List<Zombie> zombies = new ArrayList<>();
    private final int[][] path = {{0,7}, {14,7}}; // Simple horizontal path
    private int zombiesToSpawn = 0;
    private int zombieSpawnTimer = 0;
    
    // Projectiles
    private final List<Projectile> projectiles = new ArrayList<>();
    
    // Phase 2
    private int clickDamage = 1;
    private int clickUpgradeLevel = 0;
    private boolean phase2Transition = false;
    private float phase2EffectAlpha = 0f;
    
    // UI
    private final Color validColor = new Color(0, 255, 0, 100);
    private final Color invalidColor = new Color(255, 0, 0, 100);
    private Point hoverCell = new Point(-1, -1);
    
    // Tower costs
    private final int ARROW_COST = 30;
    private final int BOMB_COST = 40;
    private final int ICE_COST = 25;
    private final int MINIGUN_COST = 300; // Increased cost
    
    // Game fonts
    private final Font titleFont = new Font("Arial", Font.BOLD, 36);
    private final Font infoFont = new Font("Arial", Font.PLAIN, 20);
    private final Font waveFont = new Font("Arial", Font.BOLD, 28);
    
    // Zombie types
    private enum ZombieType {
        BASIC(10, 1, 2, Color.GRAY, 20),
        SPEEDY(7, 2, 1, Color.CYAN, 15),
        ABNORMAL(15, 3, 1, Color.RED, 40),
        CHARGED(20, 5, 1, Color.YELLOW, 60);
        
        final int reward;
        final int damage;
        final int speed;
        final Color color;
        final int health;
        
        ZombieType(int reward, int damage, int speed, Color color, int health) {
            this.reward = reward;
            this.damage = damage;
            this.speed = speed;
            this.color = color;
            this.health = health;
        }
    }
    
    // Particle effects
    private final List<Particle> particles = new ArrayList<>();
    
    // Path grid for validation
    private final boolean[][] pathGrid = new boolean[GRID_SIZE][GRID_SIZE];
    
    // Tower images
    private Image arrowImage, bombImage, iceImage, minigunImage;
    
    // Popup state
    private Rectangle popupRect = null;
    
    public DieToLive() {
        setPreferredSize(new Dimension(GRID_SIZE * CELL_SIZE, GRID_SIZE * CELL_SIZE + 100));
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        setFocusable(true);
        
        // Initialize path grid
        for (int i = 0; i < path.length - 1; i++) {
            int[] p1 = path[i];
            int[] p2 = path[i+1];
            if (p1[0] == p2[0]) { // Vertical
                int minY = Math.min(p1[1], p2[1]);
                int maxY = Math.max(p1[1], p2[1]);
                for (int y = minY; y <= maxY; y++) {
                    pathGrid[p1[0]][y] = true;
                }
            } else { // Horizontal
                int minX = Math.min(p1[0], p2[0]);
                int maxX = Math.max(p1[0], p2[0]);
                for (int x = minX; x <= maxX; x++) {
                    pathGrid[x][p1[1]] = true;
                }
            }
        }
        
        // Load tower images
        try {
            arrowImage = ImageIO.read(new File("arrow_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
            bombImage = ImageIO.read(new File("bomb_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
            iceImage = ImageIO.read(new File("ice_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
            minigunImage = ImageIO.read(new File("minigun_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
        } catch (IOException e) {
            System.out.println("Tower images not found, using default graphics");
            arrowImage = bombImage = iceImage = minigunImage = null;
        }
        
        new Thread(this).start();
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        final double ns = 1000000000.0 / 60.0; // 60 updates per second
        double delta = 0;
        
        while (true) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;
            
            while (delta >= 1) {
                tick();
                delta--;
            }
            
            repaint();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void tick() {
        // Update projectiles
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext();) {
            Projectile p = it.next();
            p.update();
            if (p.hasReachedTarget()) {
                p.hitTarget();
                it.remove();
            } else if (p.isOffScreen()) {
                it.remove();
            }
        }
        
        // Update particles
        for (Iterator<Particle> it = particles.iterator(); it.hasNext();) {
            Particle p = it.next();
            p.update();
            if (!p.isAlive()) {
                it.remove();
            }
        }
        
        // Wave progression
        if (!isPaused && state == GameState.COMBAT && zombies.isEmpty() && zombiesToSpawn == 0) {
            state = GameState.BUILD;
            coins += 30 + 15 * wave; // Wave completion bonus
            isPaused = true;
        }

        // Spawn zombies
        if (state == GameState.COMBAT && zombiesToSpawn > 0) {
            zombieSpawnTimer++;
            if (zombieSpawnTimer >= 30) { // Spawn every half second
                spawnZombie();
                zombieSpawnTimer = 0;
            }
        }

        // Update zombies
        Iterator<Zombie> zit = zombies.iterator();
        while (zit.hasNext()) {
            Zombie z = zit.next();
            z.update();
            
            // Check if reached base
            if (z.x <= CELL_SIZE) {
                baseHealth -= z.damage;
                createBloodEffect(z.x, z.y);
                zit.remove();
                if (baseHealth <= 0) {
                    handleBaseDestroyed();
                }
            }
        }

        // Update towers
        for (Map.Entry<Point, Tower> entry : towers.entrySet()) {
            Point pos = entry.getKey();
            Tower t = entry.getValue();
            t.update(zombies, pos, projectiles);
        }

        // Phase 2 transition effect
        if (phase2Transition) {
            phase2EffectAlpha += 0.02f;
            if (phase2EffectAlpha >= 1.0f) {
                phase2Transition = false;
                state = GameState.PHASE2;
                clickDamage = 1; // Reset click damage for phase 2
                clickUpgradeLevel = 0;
                
                // Add some particles for effect
                for (int i = 0; i < 100; i++) {
                    particles.add(new Particle(GRID_SIZE * CELL_SIZE / 2, GRID_SIZE * CELL_SIZE / 2, Color.RED));
                }
            }
        }
        
        // Win condition
        if (wave >= 40) {
            state = GameState.WIN;
        }
    }

    private void spawnZombie() {
        ZombieType type;
        if (wave < 5) {
            type = ZombieType.BASIC;
        } else if (wave < 10) {
            type = wave % 2 == 0 ? ZombieType.BASIC : ZombieType.SPEEDY;
        } else if (wave < 15) {
            type = wave % 3 == 0 ? ZombieType.BASIC : wave % 3 == 1 ? ZombieType.SPEEDY : ZombieType.ABNORMAL;
        } else {
            int rand = (int)(Math.random() * 4);
            type = ZombieType.values()[rand];
        }
        
        zombies.add(new Zombie(type));
        zombiesToSpawn--;
    }
    
    private void createBloodEffect(float x, float y) {
        for (int i = 0; i < 20; i++) {
            particles.add(new Particle(x, y, Color.RED));
        }
    }

    private void handleBaseDestroyed() {
        if (wave >= 20) {
            phase2Transition = true;
            baseHealth = 50; // Give player some health for phase 2
        } else {
            state = GameState.GAME_OVER;
        }
    }

    private void startNextWave() {
        if (state == GameState.GAME_OVER || state == GameState.WIN) return;
        
        wave++;
        zombiesToSpawn = 5 + wave; // Scaling difficulty
        state = GameState.COMBAT;
        isPaused = false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw background
        g2d.setColor(new Color(20, 30, 20));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw grid
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                g2d.setColor(new Color(40, 50, 40));
                g2d.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                g2d.setColor(new Color(30, 40, 30));
                g2d.drawRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }

        // Draw path
        g2d.setColor(new Color(100, 70, 30)); // Brown path
        for (int i = 0; i < path.length - 1; i++) {
            int[] p1 = path[i];
            int[] p2 = path[i+1];
            
            // Connect path segments
            if (p1[0] == p2[0]) { // Vertical
                int minY = Math.min(p1[1], p2[1]);
                int maxY = Math.max(p1[1], p2[1]);
                for (int y = minY; y <= maxY; y++) {
                    g2d.fillRect(p1[0] * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            } else { // Horizontal
                int minX = Math.min(p1[0], p2[0]);
                int maxX = Math.max(p1[0], p2[0]);
                for (int x = minX; x <= maxX; x++) {
                    g2d.fillRect(x * CELL_SIZE, p1[1] * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            }
        }

        // Draw base
        g2d.setColor(new Color(180, 0, 0));
        g2d.fillRect(0, 7 * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("BASE", 5, 7 * CELL_SIZE + 25);

        // Draw towers
        for (Map.Entry<Point, Tower> entry : towers.entrySet()) {
            entry.getValue().draw(g2d, entry.getKey());
        }

        // Draw projectiles
        for (Projectile p : projectiles) {
            p.draw(g2d);
        }
        
        // Draw zombies
        for (Zombie z : zombies) {
            z.draw(g2d);
        }
        
        // Draw particles
        for (Particle p : particles) {
            p.draw(g2d);
        }

        // Draw drag preview
        if (dragPoint != null && draggedTower != null) {
            boolean valid = isValidPlacement(dragPoint);
            g2d.setColor(valid ? validColor : invalidColor);
            g2d.fillRect(dragPoint.x * CELL_SIZE, dragPoint.y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            draggedTower.draw(g2d, dragPoint);
        }

        // Draw hover preview
        if (hoverCell.x >= 0 && draggedTower == null) {
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.fillRect(hoverCell.x * CELL_SIZE, hoverCell.y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        }

        // UI Panel
        g2d.setColor(new Color(30, 30, 30, 220));
        g2d.fillRect(0, GRID_SIZE * CELL_SIZE, getWidth(), 100);
        
        // Draw UI text
        g2d.setColor(Color.WHITE);
        g2d.setFont(infoFont);
        g2d.drawString("Coins: " + coins, 20, GRID_SIZE * CELL_SIZE + 30);
        g2d.drawString("Wave: " + wave + "/40", 20, GRID_SIZE * CELL_SIZE + 60);
        g2d.drawString("Base: " + baseHealth, 20, GRID_SIZE * CELL_SIZE + 90);
        
        // Draw tower info
        g2d.drawString("Selected: " + selectedTower, 200, GRID_SIZE * CELL_SIZE + 30);
        String clickInfo = state == GameState.PHASE2 ? "Click Dmg: " + clickDamage : "";
        g2d.drawString(clickInfo, 200, GRID_SIZE * CELL_SIZE + 60);
        
        // Draw controls
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        g2d.drawString("1: Arrow ($" + ARROW_COST + ")", 400, GRID_SIZE * CELL_SIZE + 30);
        g2d.drawString("2: Bomb ($" + BOMB_COST + ")", 400, GRID_SIZE * CELL_SIZE + 50);
        g2d.drawString("3: Ice ($" + ICE_COST + ")", 400, GRID_SIZE * CELL_SIZE + 70);
        g2d.drawString("4: Minigun ($" + MINIGUN_COST + ")", 400, GRID_SIZE * CELL_SIZE + 90);
        g2d.drawString("T: Upgrade  Z: Sell  SPACE: Start/Pause", 600, GRID_SIZE * CELL_SIZE + 30);
        
        // Draw game state messages
        if (state == GameState.GAME_OVER) {
            drawScreenOverlay(g2d, new Color(100, 0, 0, 150));
            g2d.setColor(Color.WHITE);
            g2d.setFont(titleFont);
            drawCenteredString(g2d, "GAME OVER", GRID_SIZE * CELL_SIZE / 2);
            g2d.setFont(infoFont);
            drawCenteredString(g2d, "You survived " + wave + " waves", GRID_SIZE * CELL_SIZE / 2 + 40);
            drawCenteredString(g2d, "Press R to restart", GRID_SIZE * CELL_SIZE / 2 + 80);
        } else if (state == GameState.WIN) {
            drawScreenOverlay(g2d, new Color(0, 100, 0, 150));
            g2d.setColor(Color.WHITE);
            g2d.setFont(titleFont);
            drawCenteredString(g2d, "YOU WIN!", GRID_SIZE * CELL_SIZE / 2);
            g2d.setFont(infoFont);
            drawCenteredString(g2d, "You survived all 40 waves!", GRID_SIZE * CELL_SIZE / 2 + 40);
            drawCenteredString(g2d, "Press R to restart", GRID_SIZE * CELL_SIZE / 2 + 80);
        } else if (isPaused && state == GameState.BUILD) {
            g2d.setColor(new Color(255, 255, 0, 150));
            g2d.fillRect(0, 0, getWidth(), 50);
            g2d.setColor(Color.BLACK);
            g2d.setFont(waveFont);
            drawCenteredString(g2d, "Press SPACE to start wave " + (wave + 1), 25);
        }
        
        // Phase 2 effect
        if (phase2Transition || state == GameState.PHASE2) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, phase2EffectAlpha));
            g2d.setColor(new Color(255, 0, 0, 100));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            
            // Draw fire effect borders
            g2d.setColor(new Color(255, 100, 0));
            for (int i = 0; i < 20; i++) {
                int height = 10 + (int)(Math.random() * 30);
                g2d.fillRect(i * 40, 0, 20, height);
                g2d.fillRect(i * 40, getHeight() - height, 20, height);
            }
            g2d.setComposite(AlphaComposite.SrcOver);
            
            // Draw phase 2 message
            if (state == GameState.PHASE2) {
                g2d.setColor(new Color(255, 255, 0));
                g2d.setFont(waveFont);
                drawCenteredString(g2d, "PHASE 2 ACTIVATED! CLICK ZOMBIES!", 50);
            }
        }
        
        // Draw tower popup if needed
        if (selectedTowerInstance != null && selectedTowerPos != null) {
            popupRect = drawTowerPopup(g2d, selectedTowerPos, selectedTowerInstance);
        } else {
            popupRect = null;
        }
    }
    
    private Rectangle drawTowerPopup(Graphics2D g2d, Point pos, Tower tower) {
        int popupX = pos.x * CELL_SIZE;
        int popupY = pos.y * CELL_SIZE - 150;
        
        // Adjust position if near edge
        if (popupX < 0) popupX = 0;
        if (popupX > getWidth() - 200) popupX = getWidth() - 200;
        if (popupY < 0) popupY = pos.y * CELL_SIZE + CELL_SIZE;
        
        // Draw popup background
        g2d.setColor(new Color(50, 50, 70));
        g2d.fillRect(popupX, popupY, 200, 150);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(popupX, popupY, 200, 150);
        
        // Draw tower info
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString(tower.name + " (Lv " + tower.level + ")", popupX + 10, popupY + 20);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Damage: " + tower.damage, popupX + 10, popupY + 40);
        g2d.drawString("Range: " + tower.range, popupX + 10, popupY + 60);
        g2d.drawString("Fire Rate: " + (60.0f / tower.maxCooldown) + "/sec", popupX + 10, popupY + 80);
        
        // Draw upgrade button
        g2d.setColor(new Color(60, 180, 60));
        g2d.fillRect(popupX + 10, popupY + 100, 80, 30);
        g2d.setColor(Color.WHITE);
        int upgradeCost = tower.getUpgradeCost();
        String upgradeText = upgradeCost > 0 ? "Upgrade ($" + upgradeCost + ")" : "Max Level";
        g2d.drawString(upgradeText, popupX + 15, popupY + 120);
        
        // Draw sell button
        g2d.setColor(new Color(180, 60, 60));
        g2d.fillRect(popupX + 110, popupY + 100, 80, 30);
        g2d.setColor(Color.WHITE);
        int sellValue = (int)(tower.cost * 0.6);
        g2d.drawString("Sell ($" + sellValue + ")", popupX + 120, popupY + 120);
        
        return new Rectangle(popupX, popupY, 200, 150);
    }
    
    private void drawScreenOverlay(Graphics2D g2d, Color color) {
        g2d.setColor(color);
        g2d.fillRect(0, 0, getWidth(), getHeight());
    }
    
    private void drawCenteredString(Graphics2D g2d, String text, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        g2d.drawString(text, x, y);
    }

    private boolean isValidPlacement(Point p) {
        // Check if within bounds
        if (p.x < 0 || p.y < 0 || p.x >= GRID_SIZE || p.y >= GRID_SIZE) return false;
        
        // Check if on path
        if (pathGrid[p.x][p.y]) return false;
        
        // Check if occupied
        return !towers.containsKey(p);
    }

    // Tower classes
    private abstract class Tower {
        int damage;
        int range;
        int cost;
        int level = 1;
        int cooldown = 0;
        int maxCooldown;
        Color color;
        String name;
        Image image;
        
        abstract int getUpgradeCost();
        abstract void upgrade();
        
        void update(List<Zombie> zombies, Point towerPos, List<Projectile> projectiles) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            
            // Find target
            Zombie target = null;
            float minDistance = Float.MAX_VALUE;
            
            for (Zombie z : zombies) {
                float distance = (float)Math.sqrt(
                    Math.pow(towerPos.x * CELL_SIZE + CELL_SIZE/2 - z.x, 2) + 
                    Math.pow(towerPos.y * CELL_SIZE + CELL_SIZE/2 - z.y, 2)
                );
                
                if (distance < range * CELL_SIZE && distance < minDistance) {
                    minDistance = distance;
                    target = z;
                }
            }
            
            if (target != null) {
                // Create projectile
                projectiles.add(new Projectile(
                    towerPos.x * CELL_SIZE + CELL_SIZE/2,
                    towerPos.y * CELL_SIZE + CELL_SIZE/2,
                    target,
                    damage,
                    color
                ));
                
                cooldown = maxCooldown;
            }
        }
        
        void draw(Graphics2D g, Point pos) {
            // Draw tower image if available
            if (image != null) {
                g.drawImage(image, pos.x * CELL_SIZE + 5, pos.y * CELL_SIZE + 5, null);
            } else {
                // Fallback to colored square
                g.setColor(color);
                g.fillRect(pos.x * CELL_SIZE + 5, pos.y * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
            }
            
            // Draw level indicator
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString("Lv " + level, pos.x * CELL_SIZE + 10, pos.y * CELL_SIZE + 20);
        }
    }

    private class ArrowTower extends Tower {
        ArrowTower() {
            damage = 2;
            range = 5;
            cost = ARROW_COST;
            maxCooldown = 30;
            color = Color.GREEN;
            name = "Arrow Tower";
            image = arrowImage;
        }
        
        @Override
        int getUpgradeCost() {
            if (level == 1) return 20;
            if (level == 2) return 50;
            return 0; // Max level
        }
        
        @Override
        void upgrade() {
            if (level == 1 && coins >= 20) {
                coins -= 20;
                damage = 4;
                level = 2;
            } else if (level == 2 && coins >= 50) {
                coins -= 50;
                damage = 8;
                level = 3;
            }
        }
    }

    private class BombTower extends Tower {
        BombTower() {
            damage = 5;
            range = 4;
            cost = BOMB_COST;
            maxCooldown = 60;
            color = Color.ORANGE;
            name = "Bomb Tower";
            image = bombImage;
        }
        
        @Override
        int getUpgradeCost() {
            return 0; // No upgrades
        }
        
        @Override
        void upgrade() {
            // No upgrades
        }
    }

    private class IceTower extends Tower {
        float freezeTime;
        
        IceTower() {
            damage = 1;
            freezeTime = 0.3f;
            range = 4;
            cost = ICE_COST;
            maxCooldown = 20;
            color = Color.CYAN;
            name = "Ice Tower";
            image = iceImage;
        }
        
        @Override
        int getUpgradeCost() {
            if (level == 1) return 60;
            if (level == 2) return 45;
            if (level == 3) return 50;
            if (level == 4) return 130;
            return 0; // Max level
        }
        
        @Override
        void upgrade() {
            if (level == 1 && coins >= 60) {
                coins -= 60;
                damage = 2;
                freezeTime = 1.0f;
                level = 2;
            } else if (level == 2 && coins >= 45) {
                coins -= 45;
                damage = 4;
                freezeTime = 1.5f;
                level = 3;
            } else if (level == 3 && coins >= 50) {
                coins -= 50;
                damage = 5;
                freezeTime = 2.0f;
                level = 4;
            } else if (level == 4 && coins >= 130) {
                coins -= 130;
                damage = 10;
                freezeTime = 2.5f;
                level = 5;
            }
        }
    }

    private class MiniGunnerTower extends Tower {
        MiniGunnerTower() {
            damage = 3;
            range = 4; // Reduced range
            cost = MINIGUN_COST;
            maxCooldown = 5;
            color = Color.MAGENTA;
            name = "Minigunner";
            image = minigunImage;
        }
        
        @Override
        int getUpgradeCost() {
            if (level == 1) return 200;
            if (level == 2) return 250;
            if (level == 3) return 300;
            if (level == 4) return 400;
            return 0; // Max level
        }
        
        @Override
        void upgrade() {
            if (level == 1 && coins >= 200) {
                coins -= 200;
                damage = 7;
                level = 2;
            } else if (level == 2 && coins >= 250) {
                coins -= 250;
                damage = 11;
                level = 3;
            } else if (level == 3 && coins >= 300) {
                coins -= 300;
                damage = 14;
                level = 4;
            } else if (level == 4 && coins >= 400) {
                coins -= 400;
                damage = 20;
                level = 5;
            }
        }
    }

    private class Projectile {
        float x, y;
        Zombie target;
        int damage;
        Color color;
        float speed = 8.0f;
        
        Projectile(float startX, float startY, Zombie target, int damage, Color color) {
            this.x = startX;
            this.y = startY;
            this.target = target;
            this.damage = damage;
            this.color = color;
        }
        
        void update() {
            if (target == null || target.health <= 0) return;
            
            float dx = target.x - x;
            float dy = target.y - y;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            
            if (distance < speed) {
                x = target.x;
                y = target.y;
            } else {
                x += (dx / distance) * speed;
                y += (dy / distance) * speed;
            }
        }
        
        boolean hasReachedTarget() {
            if (target == null) return true;
            float dx = target.x - x;
            float dy = target.y - y;
            return (dx * dx + dy * dy) < 100; // 10px radius
        }
        
        boolean isOffScreen() {
            return x < 0 || x > getWidth() || y < 0 || y > getHeight();
        }
        
        void hitTarget() {
            if (target != null) {
                target.health -= damage;
                if (target.health <= 0) {
                    coins += target.type.reward;
                    zombies.remove(target);
                    createBloodEffect(target.x, target.y);
                }
                
                // Create hit effect
                for (int i = 0; i < 5; i++) {
                    particles.add(new Particle(x, y, color));
                }
            }
        }
        
        void draw(Graphics2D g) {
            g.setColor(color);
            g.fillOval((int)x - 5, (int)y - 5, 10, 10);
        }
    }

    private class Zombie {
        ZombieType type;
        float x, y;
        int health;
        int damage;
        float speed;
        float baseSpeed;
        float slowTimer = 0;
        
        Zombie(ZombieType type) {
            this.type = type;
            this.health = type.health;
            this.damage = type.damage;
            this.speed = type.speed;
            this.baseSpeed = type.speed;
            this.x = GRID_SIZE * CELL_SIZE;
            this.y = 7 * CELL_SIZE + CELL_SIZE/2;
        }
        
        void slowDown(float duration) {
            speed = baseSpeed * 0.3f;
            slowTimer = duration * 60; // Convert seconds to frames
        }
        
        void update() {
            if (slowTimer > 0) {
                slowTimer--;
                if (slowTimer == 0) {
                    speed = baseSpeed;
                }
            }
            
            x -= speed;
        }
        
        void draw(Graphics2D g) {
            g.setColor(type.color);
            g.fillOval((int)x - 15, (int)y - 15, 30, 30);
            
            // Draw health bar
            g.setColor(Color.RED);
            g.fillRect((int)x - 15, (int)y - 25, 30, 5);
            g.setColor(Color.GREEN);
            float healthPercent = (float)health / type.health;
            g.fillRect((int)x - 15, (int)y - 25, (int)(30 * healthPercent), 5);
            
            // Draw zombie type indicator
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            String symbol = "";
            switch(type) {
                case BASIC: symbol = "B"; break;
                case SPEEDY: symbol = "S"; break;
                case ABNORMAL: symbol = "A"; break;
                case CHARGED: symbol = "C"; break;
            }
            g.drawString(symbol, (int)x - 5, (int)y + 5);
        }
    }
    
    private class Particle {
        float x, y;
        float vx, vy;
        Color color;
        int life;
        
        Particle(float x, float y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.vx = (float)(Math.random() * 4 - 2);
            this.vy = (float)(Math.random() * 4 - 2);
            this.life = 20 + (int)(Math.random() * 30);
        }
        
        void update() {
            x += vx;
            y += vy;
            vy += 0.1; // Gravity
            life--;
        }
        
        boolean isAlive() {
            return life > 0;
        }
        
        void draw(Graphics2D g) {
            float alpha = (float)life / 50;
            if (alpha > 1) alpha = 1;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255)));
            g.fillOval((int)x, (int)y, 5, 5);
        }
    }

    // Input handling
    @Override
    public void mouseMoved(MouseEvent e) {
        hoverCell = new Point(e.getX() / CELL_SIZE, e.getY() / CELL_SIZE);
    }
    
    @Override
    public void mouseDragged(MouseEvent e) {
        if (draggedTower != null) {
            dragPoint = new Point(e.getX() / CELL_SIZE, e.getY() / CELL_SIZE);
        }
    }
    
    @Override
    public void mousePressed(MouseEvent e) {
        int gridX = e.getX() / CELL_SIZE;
        int gridY = e.getY() / CELL_SIZE;
        Point gridPoint = new Point(gridX, gridY);
        
        if (SwingUtilities.isLeftMouseButton(e)) {
            // Close popup if clicked outside of it
            if (popupRect != null && !popupRect.contains(e.getPoint())) {
                selectedTowerInstance = null;
                selectedTowerPos = null;
                popupRect = null;
            }
            
            if (state == GameState.PHASE2) {
                // Handle click damage to zombies
                for (Zombie z : new ArrayList<>(zombies)) {
                    double dist = Math.sqrt(Math.pow(z.x - e.getX(), 2) + Math.pow(z.y - e.getY(), 2));
                    if (dist < 30) {
                        z.health -= clickDamage;
                        coins += 2; // Small coin reward for clicks
                        
                        // Create hit effect
                        for (int i = 0; i < 5; i++) {
                            particles.add(new Particle(e.getX(), e.getY(), Color.YELLOW));
                        }
                        
                        if (z.health <= 0) {
                            coins += z.type.reward;
                            zombies.remove(z);
                            createBloodEffect(z.x, z.y);
                        }
                    }
                }
            } else if (draggedTower == null) {
                // Start dragging a new tower if we have enough money
                int cost = 0;
                switch (selectedTower) {
                    case ARROW: cost = ARROW_COST; break;
                    case BOMB: cost = BOMB_COST; break;
                    case ICE: cost = ICE_COST; break;
                    case MINIGUN: cost = MINIGUN_COST; break;
                }
                
                if (coins >= cost) {
                    switch (selectedTower) {
                        case ARROW: draggedTower = new ArrowTower(); break;
                        case BOMB: draggedTower = new BombTower(); break;
                        case ICE: draggedTower = new IceTower(); break;
                        case MINIGUN: draggedTower = new MiniGunnerTower(); break;
                    }
                    dragPoint = gridPoint;
                }
            } else {
                // Place tower
                if (isValidPlacement(dragPoint)) {
                    towers.put(dragPoint, draggedTower);
                    coins -= draggedTower.cost;
                }
                draggedTower = null;
                dragPoint = null;
            }
        } else if (SwingUtilities.isRightMouseButton(e)) {
            // Close any existing popup
            selectedTowerInstance = null;
            selectedTowerPos = null;
            
            // Select tower for popup if clicked on one
            if (towers.containsKey(gridPoint)) {
                selectedTowerInstance = towers.get(gridPoint);
                selectedTowerPos = gridPoint;
            }
        }
    }
    
    @Override
    public void mouseReleased(MouseEvent e) {
        // Handle popup button clicks
        if (selectedTowerInstance != null && selectedTowerPos != null && popupRect != null) {
            int popupX = popupRect.x;
            int popupY = popupRect.y;
            
            int mouseX = e.getX();
            int mouseY = e.getY();
            
            // Check if upgrade button clicked
            if (mouseX > popupX + 10 && mouseX < popupX + 90 && 
                mouseY > popupY + 100 && mouseY < popupY + 130) {
                
                int upgradeCost = selectedTowerInstance.getUpgradeCost();
                if (upgradeCost > 0 && coins >= upgradeCost) {
                    selectedTowerInstance.upgrade();
                }
            }
            
            // Check if sell button clicked
            if (mouseX > popupX + 110 && mouseX < popupX + 190 && 
                mouseY > popupY + 100 && mouseY < popupY + 130) {
                
                int sellValue = (int)(selectedTowerInstance.cost * 0.6);
                coins += sellValue;
                towers.remove(selectedTowerPos);
                selectedTowerInstance = null;
                selectedTowerPos = null;
                popupRect = null;
            }
        }
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_1:
                selectedTower = TowerType.ARROW;
                break;
            case KeyEvent.VK_2:
                selectedTower = TowerType.BOMB;
                break;
            case KeyEvent.VK_3:
                selectedTower = TowerType.ICE;
                break;
            case KeyEvent.VK_4:
                selectedTower = TowerType.MINIGUN;
                break;
            case KeyEvent.VK_SPACE:
                if (state == GameState.BUILD) {
                    startNextWave();
                } else if (state == GameState.COMBAT) {
                    isPaused = !isPaused;
                }
                break;
            case KeyEvent.VK_T:
                if (selectedTowerInstance != null) {
                    int upgradeCost = selectedTowerInstance.getUpgradeCost();
                    if (upgradeCost > 0 && coins >= upgradeCost) {
                        selectedTowerInstance.upgrade();
                    }
                } else if (state == GameState.PHASE2 && coins >= 50) {
                    // Upgrade click damage in phase 2
                    coins -= 50;
                    clickUpgradeLevel++;
                    clickDamage = 1 + 3 * clickUpgradeLevel;
                }
                break;
            case KeyEvent.VK_Z:
                if (selectedTowerInstance != null) {
                    // Sell tower for 60% of cost
                    int refund = (int)(selectedTowerInstance.cost * 0.6);
                    coins += refund;
                    
                    // Find and remove the tower
                    for (Iterator<Map.Entry<Point, Tower>> it = towers.entrySet().iterator(); it.hasNext();) {
                        Map.Entry<Point, Tower> entry = it.next();
                        if (entry.getValue() == selectedTowerInstance) {
                            it.remove();
                            break;
                        }
                    }
                    selectedTowerInstance = null;
                    selectedTowerPos = null;
                    popupRect = null;
                }
                break;
            case KeyEvent.VK_R:
                // Restart game
                if (state == GameState.GAME_OVER || state == GameState.WIN) {
                    resetGame();
                }
                break;
            case KeyEvent.VK_ESCAPE:
                // Close popup
                selectedTowerInstance = null;
                selectedTowerPos = null;
                popupRect = null;
                break;
        }
    }
    
    private void resetGame() {
        state = GameState.BUILD;
        wave = 0;
        coins = START_COINS;
        baseHealth = BASE_HEALTH;
        towers.clear();
        zombies.clear();
        projectiles.clear();
        particles.clear();
        selectedTowerInstance = null;
        selectedTowerPos = null;
        popupRect = null;
        isPaused = true;
        phase2Transition = false;
        phase2EffectAlpha = 0f;
    }
    
    // Unused event methods
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("Die to Live");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new DieToLive());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
