import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

/**
 * Die to Live - Zombie Defense Game
 * 
 * A strategy game where players defend their base against waves of zombies.
 * Features tower placement, resource management, and a unique Phase 2 mechanic.
 * Players must survive 40 total waves to win.
 */
public class DieToLive extends JPanel implements Runnable, MouseListener, MouseMotionListener, KeyListener {
    // ====================== GAME CONSTANTS ======================
    private static final int GRID_SIZE = 15;          // Size of the game grid (15x15 cells)
    private static final int CELL_SIZE = 50;           // Size of each grid cell in pixels
    private static final int BASE_HEALTH = 100;        // Starting health for player's base
    private static final int START_COINS = 100;        // Starting coins for the player
    
    // ====================== GAME STATE VARIABLES ======================
    private enum GameState { BUILD, COMBAT, PHASE2, GAME_OVER, WIN } // Possible game states
    private GameState state = GameState.BUILD;         // Current game state
    private int wave = 0;                              // Current wave number
    private int coins = START_COINS;                   // Player's coin count
    private int baseHealth = BASE_HEALTH;              // Current base health
    private boolean isPaused = true;                   // Whether game is paused
    
    // ====================== TOWER SYSTEM ======================
    private enum TowerType { ARROW, BOMB, ICE, MINIGUN } // Available tower types
    private TowerType selectedTower = TowerType.ARROW; // Currently selected tower for placement
    private final Map<Point, Tower> towers = new HashMap<>(); // Map of placed towers (position -> tower)
    private Tower draggedTower = null;                 // Tower being dragged for placement
    private Point dragPoint = null;                    // Position where tower is being dragged
    private Tower selectedTowerInstance = null;        // Currently selected tower for upgrades
    private Point selectedTowerPos = null;             // Position of selected tower
    
    // ====================== ZOMBIE SYSTEM ======================
    private final List<Zombie> zombies = new ArrayList<>(); // List of active zombies
    // Twisted path that zombies follow (semicircle shape)
    private final int[][] path = {
        {14, 7}, {13, 7}, {12, 7}, {11, 6}, {10, 5}, {9, 4}, 
        {8, 4}, {7, 5}, {6, 6}, {5, 7}, {4, 7}, {3, 7}, 
        {2, 7}, {1, 7}, {0, 7}
    };
    private int zombiesToSpawn = 0;                    // Zombies remaining to spawn in current wave
    private int zombieSpawnTimer = 0;                  // Timer between zombie spawns
    
    // ====================== PROJECTILE SYSTEM ======================
    private final List<Projectile> projectiles = new ArrayList<>(); // Active projectiles
    
    // ====================== PHASE 2 MECHANICS ======================
    private int clickDamage = 1;                       // Damage per click in Phase 2
    private int clickUpgradeLevel = 0;                 // Current upgrade level for click damage
    private boolean phase2Transition = false;          // Whether Phase 2 transition is happening
    private float phase2EffectAlpha = 0f;              // Alpha value for Phase 2 visual effect
    
    // ====================== UI VARIABLES ======================
    private final Color validColor = new Color(0, 255, 0, 100);   // Color for valid placement
    private final Color invalidColor = new Color(255, 0, 0, 100); // Color for invalid placement
    private Point hoverCell = new Point(-1, -1);       // Grid cell currently hovered by mouse
    
    // ====================== TOWER ECONOMY ======================
    private final int ARROW_COST = 30;     // Cost to place Arrow Tower
    private final int BOMB_COST = 40;      // Cost to place Bomb Tower
    private final int ICE_COST = 25;       // Cost to place Ice Tower
    private final int MINIGUN_COST = 300;   // Cost to place Minigun Tower
    
    // ====================== FONTS ======================
    private final Font titleFont = new Font("Arial", Font.BOLD, 36); // Font for titles
    private final Font infoFont = new Font("Arial", Font.PLAIN, 20); // Font for info text
    private final Font waveFont = new Font("Arial", Font.BOLD, 28);  // Font for wave text
    
    // ====================== ZOMBIE TYPES ======================
    /**
     * Enum defining different zombie types with their attributes:
     * - reward: Coins earned when killed
     * - damage: Damage dealt to base
     * - speed: Movement speed
     * - color: Visual color
     * - health: Hit points
     */
    private enum ZombieType {
        BASIC(10, 1, 1, Color.GRAY, 20),     // Basic zombie
        SPEEDY(7, 2, 2, Color.CYAN, 15),      // Fast but weak
        ABNORMAL(15, 3, 1, Color.RED, 40),    // Strong against bomb towers
        CHARGED(20, 5, 1, Color.YELLOW, 60);   // Tough and powerful
        
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
    
    // ====================== VISUAL EFFECTS ======================
    private final List<Particle> particles = new ArrayList<>(); // List of active particles
    
    // ====================== PATH VALIDATION ======================
    private final boolean[][] pathGrid = new boolean[GRID_SIZE][GRID_SIZE]; // Grid marking path cells
    
    // ====================== TOWER IMAGES ======================
    private Image arrowImage, bombImage, iceImage, minigunImage; // Images for towers
    private Image backgroundImage; // Background image
    
    // ====================== POPUP MANAGEMENT ======================
    private Rectangle popupRect = null; // Rectangle area of the active tower popup
    
    // ====================== CONSTRUCTOR ======================
    public DieToLive() {
        // Set panel size based on grid dimensions
        setPreferredSize(new Dimension(GRID_SIZE * CELL_SIZE, GRID_SIZE * CELL_SIZE + 100));
        
        // Register input listeners
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        setFocusable(true); // Ensure panel can receive key events
        
        // Initialize path grid - mark cells that are part of the zombie path
        for (int[] point : path) {
            int x = point[0];
            int y = point[1];
            if (x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE) {
                pathGrid[x][y] = true;
            }
        }
        
        // Load tower images if available
        try {
            arrowImage = ImageIO.read(new File("arrow_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
            bombImage = ImageIO.read(new File("bomb_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
            iceImage = ImageIO.read(new File("ice_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
            minigunImage = ImageIO.read(new File("minigun_tower.png")).getScaledInstance(CELL_SIZE - 10, CELL_SIZE - 10, Image.SCALE_SMOOTH);
        } catch (IOException e) {
            System.out.println("Tower images not found, using default graphics");
            arrowImage = bombImage = iceImage = minigunImage = null;
        }
        
        // Load background image if available
        try {
            backgroundImage = ImageIO.read(new File("background.jpg"));
        } catch (IOException e) {
            System.out.println("Background image not found, using solid color");
            backgroundImage = null;
        }
        
        // Start game loop thread
        new Thread(this).start();
    }

    // ====================== GAME LOOP ======================
    @Override
    public void run() {
        long lastTime = System.nanoTime();
        final double ns = 1000000000.0 / 60.0; // Nanoseconds per frame (60 FPS)
        double delta = 0;
        
        // Main game loop
        while (true) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;
            
            // Update game state based on elapsed time
            while (delta >= 1) {
                if (!isPaused) tick(); // Only update when not paused
                delta--;
            }
            
            repaint(); // Refresh display
            try {
                Thread.sleep(2); // Prevent thread from hogging CPU
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // ====================== GAME LOGIC UPDATE ======================
    /**
     * Updates all game objects and state
     */
    private void tick() {
        // Update projectiles
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext();) {
            Projectile p = it.next();
            p.update();
            if (p.hasReachedTarget()) {
                p.hitTarget(); // Apply damage when projectile hits
                it.remove();
            } else if (p.isOffScreen()) {
                it.remove(); // Clean up off-screen projectiles
            }
        }
        
        // Update particles
        for (Iterator<Particle> it = particles.iterator(); it.hasNext();) {
            Particle p = it.next();
            p.update();
            if (!p.isAlive()) {
                it.remove(); // Remove dead particles
            }
        }
        
        // Wave progression logic
        if (state == GameState.COMBAT && zombies.isEmpty() && zombiesToSpawn == 0) {
            // Return to build phase after clearing wave
            state = GameState.BUILD;
            coins += 30 + 15 * wave; // Reward coins for surviving the wave
            isPaused = true;
        }

        // Zombie spawning logic
        if (state == GameState.COMBAT && zombiesToSpawn > 0) {
            zombieSpawnTimer++;
            if (zombieSpawnTimer >= 30) { // Spawn every half second (60 FPS)
                spawnZombie();
                zombieSpawnTimer = 0;
            }
        }

        // Update zombies
        Iterator<Zombie> zit = zombies.iterator();
        while (zit.hasNext()) {
            Zombie z = zit.next();
            z.update();
            
            // Check if zombie reached base
            if (z.pathIndex >= path.length - 1) {
                baseHealth -= z.damage; // Damage the base
                createBloodEffect(z.x, z.y); // Visual effect
                zit.remove(); // Remove zombie
                
                // Check if base destroyed
                if (baseHealth <= 0) {
                    handleBaseDestroyed();
                }
            }
        }

        // Update towers - make them shoot at zombies
        for (Map.Entry<Point, Tower> entry : towers.entrySet()) {
            Point pos = entry.getKey();
            Tower t = entry.getValue();
            t.update(zombies, pos, projectiles);
        }

        // Phase 2 transition effect
        if (phase2Transition) {
            phase2EffectAlpha += 0.02f; // Increase effect opacity
            if (phase2EffectAlpha >= 1.0f) {
                phase2Transition = false;
                state = GameState.PHASE2; // Enter Phase 2
                clickDamage = 1; // Reset click damage
                clickUpgradeLevel = 0;
                
                // Add transition particles
                for (int i = 0; i < 100; i++) {
                    particles.add(new Particle(GRID_SIZE * CELL_SIZE / 2, GRID_SIZE * CELL_SIZE / 2, Color.RED));
                }
            }
        }
        
        // Win condition check
        if (wave >= 40) {
            state = GameState.WIN;
            isPaused = true;
        }
    }

    // ====================== GAME MECHANICS ======================
    /**
     * Spawns a new zombie based on current wave difficulty
     */
    private void spawnZombie() {
        ZombieType type;
        // Determine zombie type based on wave progression
        if (wave < 5) {
            type = ZombieType.BASIC;
        } else if (wave < 10) {
            type = wave % 2 == 0 ? ZombieType.BASIC : ZombieType.SPEEDY;
        } else if (wave < 15) {
            type = wave % 3 == 0 ? ZombieType.BASIC : 
                   wave % 3 == 1 ? ZombieType.SPEEDY : ZombieType.ABNORMAL;
        } else {
            int rand = (int)(Math.random() * 4);
            type = ZombieType.values()[rand]; // Random zombie type
        }
        
        zombies.add(new Zombie(type)); // Add new zombie
        zombiesToSpawn--; // Decrement spawn counter
    }
    
    /**
     * Creates a blood effect at the specified position
     */
    private void createBloodEffect(float x, float y) {
        for (int i = 0; i < 20; i++) {
            particles.add(new Particle(x, y, Color.RED)); // Add blood particles
        }
    }

    /**
     * Handles base destruction - triggers Phase 2 or game over
     */
    private void handleBaseDestroyed() {
        if (wave >= 20) {
            phase2Transition = true; // Trigger Phase 2 transition
            baseHealth = 50; // Give player health for Phase 2
        } else {
            state = GameState.GAME_OVER; // Game over if base destroyed before wave 20
            isPaused = true;
        }
    }

    /**
     * Starts the next wave of zombies
     */
    private void startNextWave() {
        if (state == GameState.GAME_OVER || state == GameState.WIN) return;
        
        wave++; // Advance wave counter
        zombiesToSpawn = 5 + wave; // Scale difficulty with wave number
        state = GameState.COMBAT; // Enter combat state
        isPaused = false; // Unpause game
    }

    // ====================== RENDERING ======================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw background
        if (backgroundImage != null) {
            g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
        } else {
            // Fallback to solid color
            g2d.setColor(new Color(15, 25, 15)); // Dark forest green
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
        
        // Draw grid cells with improved colors
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                // Create grass-like pattern
                Color grassColor = new Color(40, 60, 30);
                if ((x + y) % 2 == 0) {
                    grassColor = new Color(35, 55, 25); // Alternate shade
                }
                g2d.setColor(grassColor);
                g2d.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                
                // Draw subtle grid lines
                g2d.setColor(new Color(30, 45, 20)); // Dark green grid lines
                g2d.drawRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }

        // Draw zombie path with improved coloring
        g2d.setColor(new Color(100, 80, 40)); // Rich brown path color
        for (int i = 0; i < path.length - 1; i++) {
            int[] p1 = path[i];
            int[] p2 = path[i+1];
            
            // Connect path segments
            if (p1[0] == p2[0]) { // Vertical segment
                int minY = Math.min(p1[1], p2[1]);
                int maxY = Math.max(p1[1], p2[1]);
                for (int y = minY; y <= maxY; y++) {
                    g2d.fillRect(p1[0] * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            } else { // Horizontal segment
                int minX = Math.min(p1[0], p2[0]);
                int maxX = Math.max(p1[0], p2[0]);
                for (int x = minX; x <= maxX; x++) {
                    g2d.fillRect(x * CELL_SIZE, p1[1] * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            }
        }
        
        // Draw path borders
        g2d.setColor(new Color(70, 50, 20)); // Darker brown for borders
        for (int[] point : path) {
            int x = point[0];
            int y = point[1];
            g2d.drawRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        }

        // Draw player base with improved design
        g2d.setColor(new Color(180, 0, 0)); // Base red
        g2d.fillRect(0, 7 * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        
        // Draw base details
        g2d.setColor(new Color(150, 0, 0)); // Darker red for details
        g2d.fillRect(0, 7 * CELL_SIZE, 10, CELL_SIZE);
        g2d.fillRect(CELL_SIZE - 10, 7 * CELL_SIZE, 10, CELL_SIZE);
        g2d.fillRect(0, 7 * CELL_SIZE + CELL_SIZE/2 - 5, CELL_SIZE, 10);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("BASE", 5, 7 * CELL_SIZE + 25); // Base label

        // Draw all towers
        for (Map.Entry<Point, Tower> entry : towers.entrySet()) {
            entry.getValue().draw(g2d, entry.getKey());
        }

        // Draw all projectiles
        for (Projectile p : projectiles) {
            p.draw(g2d);
        }
        
        // Draw all zombies
        for (Zombie z : zombies) {
            z.draw(g2d);
        }
        
        // Draw all particles
        for (Particle p : particles) {
            p.draw(g2d);
        }

        // Draw tower drag preview
        if (dragPoint != null && draggedTower != null) {
            boolean valid = isValidPlacement(dragPoint);
            g2d.setColor(valid ? validColor : invalidColor); // Green/red based on validity
            g2d.fillRect(dragPoint.x * CELL_SIZE, dragPoint.y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            draggedTower.draw(g2d, dragPoint); // Draw the tower preview
        }

        // Draw cell hover highlight
        if (hoverCell.x >= 0 && draggedTower == null) {
            g2d.setColor(new Color(255, 255, 255, 50)); // Semi-transparent white
            g2d.fillRect(hoverCell.x * CELL_SIZE, hoverCell.y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        }

        // Draw UI panel at bottom
        g2d.setColor(new Color(30, 30, 30, 220)); // Semi-transparent dark background
        g2d.fillRect(0, GRID_SIZE * CELL_SIZE, getWidth(), 100);
        
        // Draw UI text
        g2d.setColor(Color.WHITE);
        g2d.setFont(infoFont);
        g2d.drawString("Coins: " + coins, 20, GRID_SIZE * CELL_SIZE + 30); // Coin counter
        g2d.drawString("Wave: " + wave + "/40", 20, GRID_SIZE * CELL_SIZE + 60); // Wave counter
        g2d.drawString("Base: " + baseHealth, 20, GRID_SIZE * CELL_SIZE + 90); // Health counter
        
        // Draw tower info
        g2d.drawString("Selected: " + selectedTower, 200, GRID_SIZE * CELL_SIZE + 30); // Selected tower
        String clickInfo = state == GameState.PHASE2 ? "Click Dmg: " + clickDamage : ""; // Phase 2 damage
        g2d.drawString(clickInfo, 200, GRID_SIZE * CELL_SIZE + 60);
        
        // Draw controls help text
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        g2d.drawString("1: Arrow ($" + ARROW_COST + ")", 400, GRID_SIZE * CELL_SIZE + 30);
        g2d.drawString("2: Bomb ($" + BOMB_COST + ")", 400, GRID_SIZE * CELL_SIZE + 50);
        g2d.drawString("3: Ice ($" + ICE_COST + ")", 400, GRID_SIZE * CELL_SIZE + 70);
        g2d.drawString("4: Minigun ($" + MINIGUN_COST + ")", 400, GRID_SIZE * CELL_SIZE + 90);
        g2d.drawString("T: Upgrade  Z: Sell  SPACE: Start/Pause", 600, GRID_SIZE * CELL_SIZE + 30);
        
        // Draw game state messages
        if (state == GameState.GAME_OVER) {
            drawScreenOverlay(g2d, new Color(100, 0, 0, 150)); // Red overlay
            g2d.setColor(Color.WHITE);
            g2d.setFont(titleFont);
            drawCenteredString(g2d, "GAME OVER", GRID_SIZE * CELL_SIZE / 2); // Game over text
            g2d.setFont(infoFont);
            drawCenteredString(g2d, "You survived " + wave + " waves", GRID_SIZE * CELL_SIZE / 2 + 40);
            drawCenteredString(g2d, "Press R to restart", GRID_SIZE * CELL_SIZE / 2 + 80);
        } else if (state == GameState.WIN) {
            drawScreenOverlay(g2d, new Color(0, 100, 0, 150)); // Green overlay
            g2d.setColor(Color.WHITE);
            g2d.setFont(titleFont);
            drawCenteredString(g2d, "YOU WIN!", GRID_SIZE * CELL_SIZE / 2); // Win text
            g2d.setFont(infoFont);
            drawCenteredString(g2d, "You survived all 40 waves!", GRID_SIZE * CELL_SIZE / 2 + 40);
            drawCenteredString(g2d, "Press R to restart", GRID_SIZE * CELL_SIZE / 2 + 80);
        } else if (isPaused && state == GameState.BUILD) {
            // Draw wave start prompt
            g2d.setColor(new Color(255, 200, 0, 180)); // Gold overlay
            g2d.fillRect(0, 0, getWidth(), 50);
            g2d.setColor(Color.BLACK);
            g2d.setFont(waveFont);
            drawCenteredString(g2d, "Press SPACE to start wave " + (wave + 1), 25);
        }
        
        // Draw Phase 2 visual effects
        if (phase2Transition || state == GameState.PHASE2) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, phase2EffectAlpha));
            g2d.setColor(new Color(255, 0, 0, 100)); // Red tint
            g2d.fillRect(0, 0, getWidth(), getHeight());
            
            // Draw fire effect borders
            g2d.setColor(new Color(255, 100, 0)); // Orange fire
            for (int i = 0; i < 20; i++) {
                int height = 10 + (int)(Math.random() * 30); // Random flame height
                g2d.fillRect(i * 40, 0, 20, height); // Top border
                g2d.fillRect(i * 40, getHeight() - height, 20, height); // Bottom border
            }
            g2d.setComposite(AlphaComposite.SrcOver); // Reset transparency
            
            // Draw Phase 2 message
            if (state == GameState.PHASE2) {
                g2d.setColor(new Color(255, 255, 0)); // Yellow text
                g2d.setFont(waveFont);
                drawCenteredString(g2d, "PHASE 2 ACTIVATED! CLICK ZOMBIES!", 50);
            }
        }
        
        // Draw tower popup if a tower is selected
        if (selectedTowerInstance != null && selectedTowerPos != null) {
            popupRect = drawTowerPopup(g2d, selectedTowerPos, selectedTowerInstance);
        } else {
            popupRect = null; // No active popup
        }
    }
    
    // ====================== UI HELPERS ======================
    /**
     * Draws a screen overlay (used for game over and win states)
     */
    private void drawScreenOverlay(Graphics2D g2d, Color color) {
        g2d.setColor(color);
        g2d.fillRect(0, 0, getWidth(), getHeight());
    }
    
    /**
     * Draws centered text at specified vertical position
     */
    private void drawCenteredString(Graphics2D g2d, String text, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2; // Calculate center position
        g2d.drawString(text, x, y);
    }

    /**
     * Draws tower information popup
     * @return Rectangle representing popup area
     */
    private Rectangle drawTowerPopup(Graphics2D g2d, Point pos, Tower tower) {
        // Calculate popup position (above tower)
        int popupX = pos.x * CELL_SIZE;
        int popupY = pos.y * CELL_SIZE - 150;
        
        // Adjust position if near screen edge
        if (popupX < 0) popupX = 0;
        if (popupX > getWidth() - 200) popupX = getWidth() - 200;
        if (popupY < 0) popupY = pos.y * CELL_SIZE + CELL_SIZE;
        
        // Draw popup background
        g2d.setColor(new Color(50, 50, 70, 220)); // Semi-transparent dark blue-gray
        g2d.fillRect(popupX, popupY, 200, 150);
        g2d.setColor(new Color(100, 100, 150)); // Light blue border
        g2d.drawRect(popupX, popupY, 200, 150);
        
        // Draw tower information
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString(tower.name + " (Lv " + tower.level + ")", popupX + 10, popupY + 20);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Damage: " + tower.damage, popupX + 10, popupY + 40);
        g2d.drawString("Range: " + tower.range, popupX + 10, popupY + 60);
        g2d.drawString("Fire Rate: " + (60.0f / tower.maxCooldown) + "/sec", popupX + 10, popupY + 80);
        
        // Draw upgrade button
        g2d.setColor(new Color(60, 180, 60)); // Green button
        g2d.fillRect(popupX + 10, popupY + 100, 80, 30);
        g2d.setColor(Color.WHITE);
        int upgradeCost = tower.getUpgradeCost();
        String upgradeText = upgradeCost > 0 ? "Upgrade ($" + upgradeCost + ")" : "Max Level";
        g2d.drawString(upgradeText, popupX + 15, popupY + 120);
        
        // Draw sell button
        g2d.setColor(new Color(180, 60, 60)); // Red button
        g2d.fillRect(popupX + 110, popupY + 100, 80, 30);
        g2d.setColor(Color.WHITE);
        int sellValue = (int)(tower.cost * 0.6); // 60% refund
        g2d.drawString("Sell ($" + sellValue + ")", popupX + 120, popupY + 120);
        
        return new Rectangle(popupX, popupY, 200, 150); // Return popup area
    }
    
    // ====================== GAME LOGIC HELPERS ======================
    /**
     * Checks if a tower can be placed at given grid position
     * @return true if placement is valid
     */
    private boolean isValidPlacement(Point p) {
        // Check grid bounds
        if (p.x < 0 || p.y < 0 || p.x >= GRID_SIZE || p.y >= GRID_SIZE) return false;
        
        // Check if on zombie path
        if (pathGrid[p.x][p.y]) return false;
        
        // Check if cell is occupied
        return !towers.containsKey(p);
    }

    // ====================== TOWER CLASSES ======================
    /**
     * Abstract base class for all towers
     */
    private abstract class Tower {
        int damage;         // Damage per shot
        int range;          // Attack range in grid cells
        int cost;           // Placement cost
        int level = 1;      // Current upgrade level
        int cooldown = 0;   // Current cooldown timer
        int maxCooldown;    // Cooldown between attacks
        Color color;        // Default color (if no image)
        String name;        // Tower name
        Image image;        // Custom image (if available)
        
        /**
         * @return Cost to upgrade, or 0 if max level
         */
        abstract int getUpgradeCost();
        
        /**
         * Upgrades the tower if possible
         */
        abstract void upgrade();
        
        /**
         * Updates tower state and attacks zombies
         */
        void update(List<Zombie> zombies, Point towerPos, List<Projectile> projectiles) {
            // Handle cooldown
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            
            // Find closest zombie in range
            Zombie target = null;
            float minDistance = Float.MAX_VALUE;
            
            for (Zombie z : zombies) {
                // Calculate distance to zombie
                float distance = (float)Math.sqrt(
                    Math.pow(towerPos.x * CELL_SIZE + CELL_SIZE/2 - z.x, 2) + 
                    Math.pow(towerPos.y * CELL_SIZE + CELL_SIZE/2 - z.y, 2)
                );
                
                // Check if in range and closest
                if (distance < range * CELL_SIZE && distance < minDistance) {
                    minDistance = distance;
                    target = z;
                }
            }
            
            // Attack if target found
            if (target != null) {
                // Create projectile aimed at target
                projectiles.add(new Projectile(
                    towerPos.x * CELL_SIZE + CELL_SIZE/2, // Tower center X
                    towerPos.y * CELL_SIZE + CELL_SIZE/2, // Tower center Y
                    target,
                    damage,
                    color
                ));
                
                cooldown = maxCooldown; // Reset cooldown
            }
        }
        
        /**
         * Draws the tower at given grid position
         */
        void draw(Graphics2D g, Point pos) {
            // Draw custom image if available
            if (image != null) {
                g.drawImage(image, pos.x * CELL_SIZE + 5, pos.y * CELL_SIZE + 5, null);
            } else {
                // Fallback to colored rectangle
                g.setColor(color);
                g.fillRect(pos.x * CELL_SIZE + 5, pos.y * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
            }
            
            // Draw level indicator
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString("Lv " + level, pos.x * CELL_SIZE + 10, pos.y * CELL_SIZE + 20);
        }
    }

    // Arrow Tower implementation
    private class ArrowTower extends Tower {
        ArrowTower() {
            damage = 2;
            range = 5;
            cost = ARROW_COST;
            maxCooldown = 30; // Shoots every 0.5 seconds (60 FPS)
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

    // Bomb Tower implementation
    private class BombTower extends Tower {
        BombTower() {
            damage = 5;
            range = 4;
            cost = BOMB_COST;
            maxCooldown = 60; // Shoots every 1 second
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
            // No upgrades available
        }
    }

    // Ice Tower implementation
    private class IceTower extends Tower {
        float freezeTime; // Duration of slow effect
        
        IceTower() {
            damage = 1;
            freezeTime = 0.3f; // Seconds
            range = 4;
            cost = ICE_COST;
            maxCooldown = 20; // Shoots every ~0.33 seconds
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

    // Minigun Tower implementation
    private class MiniGunnerTower extends Tower {
        MiniGunnerTower() {
            damage = 3;
            range = 4; // Reduced range for balance
            cost = MINIGUN_COST;
            maxCooldown = 5; // Very fast shooting (12 shots/second)
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

    // ====================== PROJECTILE CLASS ======================
    /**
     * Represents a projectile fired by a tower
     */
    private class Projectile {
        float x, y;         // Current position
        Zombie target;      // Target zombie
        int damage;         // Damage to deal
        Color color;        // Visual color
        float speed = 8.0f; // Movement speed
        
        Projectile(float startX, float startY, Zombie target, int damage, Color color) {
            this.x = startX;
            this.y = startY;
            this.target = target;
            this.damage = damage;
            this.color = color;
        }
        
        /**
         * Updates projectile position
         */
        void update() {
            if (target == null || target.health <= 0) return;
            
            // Calculate direction to target
            float dx = target.x - x;
            float dy = target.y - y;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            
            // Move toward target
            if (distance < speed) {
                // Reached target
                x = target.x;
                y = target.y;
            } else {
                // Move in target direction
                x += (dx / distance) * speed;
                y += (dy / distance) * speed;
            }
        }
        
        /**
         * @return true if projectile reached its target
         */
        boolean hasReachedTarget() {
            if (target == null) return true;
            float dx = target.x - x;
            float dy = target.y - y;
            return (dx * dx + dy * dy) < 100; // Within 10px radius
        }
        
        /**
         * @return true if projectile is off-screen
         */
        boolean isOffScreen() {
            return x < 0 || x > getWidth() || y < 0 || y > getHeight();
        }
        
        /**
         * Applies damage to target and creates hit effect
         */
        void hitTarget() {
            if (target != null) {
                target.health -= damage; // Apply damage
                
                // Handle zombie death
                if (target.health <= 0) {
                    coins += target.type.reward; // Reward coins
                    zombies.remove(target); // Remove zombie
                    createBloodEffect(target.x, target.y); // Blood effect
                }
                
                // Create hit particles
                for (int i = 0; i < 5; i++) {
                    particles.add(new Particle(x, y, color));
                }
            }
        }
        
        /**
         * Draws the projectile
         */
        void draw(Graphics2D g) {
            g.setColor(color);
            g.fillOval((int)x - 5, (int)y - 5, 10, 10); // 10px circle
        }
    }

    // ====================== ZOMBIE CLASS ======================
    /**
     * Represents a zombie enemy
     */
    private class Zombie {
        ZombieType type;    // Zombie type
        float x, y;         // Current position
        int health;         // Current health
        int damage;         // Damage to base
        float speed;        // Current movement speed
        float baseSpeed;    // Base movement speed
        float slowTimer = 0; // Slow effect timer
        int pathIndex = 0;  // Current position in path
        
        Zombie(ZombieType type) {
            this.type = type;
            this.health = type.health;
            this.damage = type.damage;
            this.speed = type.speed;
            this.baseSpeed = type.speed;
            // Start position at first path point
            this.x = path[0][0] * CELL_SIZE + CELL_SIZE/2;
            this.y = path[0][1] * CELL_SIZE + CELL_SIZE/2;
        }
        
        /**
         * Applies slow effect to zombie
         * @param duration Slow duration in seconds
         */
        void slowDown(float duration) {
            speed = baseSpeed * 0.3f; // Reduce speed to 30%
            slowTimer = duration * 60; // Convert seconds to frames (60 FPS)
        }
        
        /**
         * Updates zombie position and state
         */
        void update() {
            // Handle slow effect timer
            if (slowTimer > 0) {
                slowTimer--;
                if (slowTimer == 0) {
                    speed = baseSpeed; // Restore normal speed
                }
            }
            
            // Move along the path
            if (pathIndex < path.length - 1) {
                int nextX = path[pathIndex + 1][0] * CELL_SIZE + CELL_SIZE/2;
                int nextY = path[pathIndex + 1][1] * CELL_SIZE + CELL_SIZE/2;
                
                // Calculate direction to next point
                float dx = nextX - x;
                float dy = nextY - y;
                float distance = (float)Math.sqrt(dx * dx + dy * dy);
                
                // Move toward next point
                if (distance < speed) {
                    // Reached next point
                    x = nextX;
                    y = nextY;
                    pathIndex++;
                } else {
                    // Move in direction of next point
                    x += (dx / distance) * speed;
                    y += (dy / distance) * speed;
                }
            }
        }
        
        /**
         * Draws the zombie
         */
        void draw(Graphics2D g) {
            g.setColor(type.color);
            g.fillOval((int)x - 15, (int)y - 15, 30, 30); // 30px circle
            
            // Draw health bar background
            g.setColor(Color.RED);
            g.fillRect((int)x - 15, (int)y - 25, 30, 5);
            
            // Draw health bar foreground
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
    
    // ====================== PARTICLE CLASS ======================
    /**
     * Represents a visual particle effect
     */
    private class Particle {
        float x, y;         // Position
        float vx, vy;       // Velocity
        Color color;        // Particle color
        int life;           // Remaining lifetime
        
        Particle(float x, float y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.vx = (float)(Math.random() * 4 - 2); // Random X velocity (-2 to 2)
            this.vy = (float)(Math.random() * 4 - 2); // Random Y velocity (-2 to 2)
            this.life = 20 + (int)(Math.random() * 30); // Random lifetime (20-50 frames)
        }
        
        /**
         * Updates particle position and state
         */
        void update() {
            x += vx; // Apply velocity
            y += vy;
            vy += 0.1; // Gravity effect
            life--; // Decrease lifetime
        }
        
        /**
         * @return true if particle is still active
         */
        boolean isAlive() {
            return life > 0;
        }
        
        /**
         * Draws the particle with fading effect
         */
        void draw(Graphics2D g) {
            float alpha = (float)life / 50; // Calculate fade based on remaining life
            if (alpha > 1) alpha = 1;
            // Apply alpha to color
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255)));
            g.fillOval((int)x, (int)y, 5, 5); // 5px circle
        }
    }

    // ====================== INPUT HANDLING ======================
    @Override
    public void mouseMoved(MouseEvent e) {
        // Update hovered grid cell
        hoverCell = new Point(e.getX() / CELL_SIZE, e.getY() / CELL_SIZE);
    }
    
    @Override
    public void mouseDragged(MouseEvent e) {
        // Update drag position if dragging a tower
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
            
            // Phase 2 click damage
            if (state == GameState.PHASE2) {
                boolean hitZombie = false;
                for (Zombie z : new ArrayList<>(zombies)) {
                    // Calculate distance to zombie
                    double dist = Math.sqrt(Math.pow(z.x - e.getX(), 2) + Math.pow(z.y - e.getY(), 2));
                    if (dist < 30) { // Within 30px radius
                        z.health -= clickDamage; // Apply damage
                        coins += 2; // Small reward for clicking
                        hitZombie = true;
                        
                        // Create click effect particles
                        for (int i = 0; i < 5; i++) {
                            particles.add(new Particle(e.getX(), e.getY(), Color.YELLOW));
                        }
                        
                        // Handle zombie death
                        if (z.health <= 0) {
                            coins += z.type.reward; // Full reward
                            zombies.remove(z);
                            createBloodEffect(z.x, z.y);
                        }
                    }
                }
                
                // Visual feedback for missed clicks
                if (!hitZombie) {
                    for (int i = 0; i < 3; i++) {
                        particles.add(new Particle(e.getX(), e.getY(), Color.WHITE));
                    }
                }
            } else if (draggedTower == null) {
                // Start dragging a new tower
                int cost = 0;
                switch (selectedTower) {
                    case ARROW: cost = ARROW_COST; break;
                    case BOMB: cost = BOMB_COST; break;
                    case ICE: cost = ICE_COST; break;
                    case MINIGUN: cost = MINIGUN_COST; break;
                }
                
                // Check if player can afford tower
                if (coins >= cost) {
                    // Create new tower instance
                    switch (selectedTower) {
                        case ARROW: draggedTower = new ArrowTower(); break;
                        case BOMB: draggedTower = new BombTower(); break;
                        case ICE: draggedTower = new IceTower(); break;
                        case MINIGUN: draggedTower = new MiniGunnerTower(); break;
                    }
                    dragPoint = gridPoint; // Set initial drag position
                }
            } else {
                // Place dragged tower
                if (isValidPlacement(dragPoint)) {
                    towers.put(dragPoint, draggedTower); // Add to tower map
                    coins -= draggedTower.cost; // Deduct cost
                }
                // Reset drag state
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
            if (mouseX >= popupX + 10 && mouseX <= popupX + 90 && 
                mouseY >= popupY + 100 && mouseY <= popupY + 130) {
                
                int upgradeCost = selectedTowerInstance.getUpgradeCost();
                if (upgradeCost > 0 && coins >= upgradeCost) {
                    selectedTowerInstance.upgrade(); // Perform upgrade
                }
            }
            
            // Check if sell button clicked
            if (mouseX >= popupX + 110 && mouseX <= popupX + 190 && 
                mouseY >= popupY + 100 && mouseY <= popupY + 130) {
                
                int sellValue = (int)(selectedTowerInstance.cost * 0.6); // 60% refund
                coins += sellValue;
                towers.remove(selectedTowerPos); // Remove tower
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
                selectedTower = TowerType.ARROW; // Select Arrow Tower
                break;
            case KeyEvent.VK_2:
                selectedTower = TowerType.BOMB; // Select Bomb Tower
                break;
            case KeyEvent.VK_3:
                selectedTower = TowerType.ICE; // Select Ice Tower
                break;
            case KeyEvent.VK_4:
                selectedTower = TowerType.MINIGUN; // Select Minigun Tower
                break;
            case KeyEvent.VK_SPACE:
                if (state == GameState.BUILD) {
                    startNextWave(); // Start next wave
                } else if (state == GameState.COMBAT) {
                    isPaused = !isPaused; // Toggle pause
                }
                break;
            case KeyEvent.VK_T:
                if (selectedTowerInstance != null) {
                    // Upgrade selected tower
                    int upgradeCost = selectedTowerInstance.getUpgradeCost();
                    if (upgradeCost > 0 && coins >= upgradeCost) {
                        selectedTowerInstance.upgrade();
                    }
                } else if (state == GameState.PHASE2 && coins >= 50) {
                    // Upgrade click damage in Phase 2
                    coins -= 50;
                    clickUpgradeLevel++;
                    clickDamage = 1 + 3 * clickUpgradeLevel;
                }
                break;
            case KeyEvent.VK_Z:
                if (selectedTowerInstance != null) {
                    // Sell selected tower
                    int refund = (int)(selectedTowerInstance.cost * 0.6); // 60% refund
                    coins += refund;
                    
                    // Find and remove the tower
                    for (Iterator<Map.Entry<Point, Tower>> it = towers.entrySet().iterator(); it.hasNext();) {
                        Map.Entry<Point, Tower> entry = it.next();
                        if (entry.getValue() == selectedTowerInstance) {
                            it.remove();
                            break;
                        }
                    }
                    // Reset selection
                    selectedTowerInstance = null;
                    selectedTowerPos = null;
                    popupRect = null;
                }
                break;
            case KeyEvent.VK_R:
                // Restart game if game over or won
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
    
    // ====================== GAME MANAGEMENT ======================
    /**
     * Resets the game to initial state
     */
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
        clickDamage = 1;
        clickUpgradeLevel = 0;
    }
    
    // ====================== UNUSED EVENT METHODS ======================
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}
    
    // ====================== MAIN METHOD ======================
    public static void main(String[] args) {
        JFrame frame = new JFrame("Die to Live"); // Create game window
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new DieToLive()); // Add game panel
        frame.pack(); // Size window to fit game
        frame.setLocationRelativeTo(null); // Center window
        frame.setVisible(true); // Show window
    }
}
