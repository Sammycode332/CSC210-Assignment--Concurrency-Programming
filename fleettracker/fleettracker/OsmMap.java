package fleettracker.fleettracker;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A map backdrop for the tracker, in order of preference:
 *
 *   1. A local {@code unilag.png} in the app folder (or on the classpath) -- used
 *      as-is with no network. Export one from openstreetmap.org for a fully
 *      offline, accurate map.
 *   2. Otherwise, live OpenStreetMap tiles for the University of Lagos (Akoka),
 *      fetched in the background and cached.
 *   3. If neither is available (offline, no local image), a plain backdrop with a
 *      hint, so the demo still runs.
 *
 * <p>Tiles are fetched off the Event Dispatch Thread and cached, so painting never
 * blocks on the network and no tile is ever re-requested. A valid User-Agent is
 * sent and attribution is drawn, per OpenStreetMap's tile usage policy.
 *
 * <p>View concern only -- holds no vehicle state.
 */
public final class OsmMap {

    // Campus centre (Akoka) and zoom. Nudge these to reframe the map.
    private static final double CENTER_LAT = 6.5150;
    private static final double CENTER_LON = 3.3960;
    private static final int ZOOM = 16;

    private static final int TILE = 256;
    private static final String TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String USER_AGENT = "UNILAG-FleetTracker/1.0 (CSC210 student project)";

    private final Image localImage = tryLoadLocal();
    private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> failedUntil = new ConcurrentHashMap<>();
    private final java.util.Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool;
    private final Runnable onTileLoaded;

    public OsmMap(Runnable onTileLoaded) {
        this.onTileLoaded = onTileLoaded;
        this.pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "osm-tile-fetch");
            t.setDaemon(true);
            return t;
        });
    }

    private static Image tryLoadLocal() {
        try {
            URL url = OsmMap.class.getResource("/unilag.png");
            if (url != null) return new ImageIcon(url).getImage();
            File f = new File("unilag.png");
            if (f.exists()) return new ImageIcon(f.getAbsolutePath()).getImage();
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---- slippy-map projection: lon/lat -> global pixel at ZOOM ----

    private static double lonToGlobalX(double lon) {
        return (lon + 180.0) / 360.0 * (1 << ZOOM) * TILE;
    }

    private static double latToGlobalY(double lat) {
        double r = Math.toRadians(lat);
        double y = (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2;
        return y * (1 << ZOOM) * TILE;
    }

    /** Draws the map centred on the campus, filling the (w x h) panel. */
    public void paint(Graphics2D g2, int w, int h) {
        // 1. Local image wins -- fully offline, no network.
        if (localImage != null) {
            g2.drawImage(localImage, 0, 0, w, h, null);
            return;
        }

        // 2. Live OSM tiles.
        g2.setColor(new Color(0xE9E6DF));
        g2.fillRect(0, 0, w, h);

        double centerX = lonToGlobalX(CENTER_LON);
        double centerY = latToGlobalY(CENTER_LAT);
        double originX = centerX - w / 2.0;   // global pixel at panel (0,0)
        double originY = centerY - h / 2.0;

        int n = 1 << ZOOM;
        int minTX = (int) Math.floor(originX / TILE), maxTX = (int) Math.floor((originX + w) / TILE);
        int minTY = (int) Math.floor(originY / TILE), maxTY = (int) Math.floor((originY + h) / TILE);

        int loaded = 0;
        boolean anyFailed = false;
        for (int tx = minTX; tx <= maxTX; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue;
                int drawX = (int) Math.round(tx * (long) TILE - originX);
                int drawY = (int) Math.round(ty * (long) TILE - originY);
                BufferedImage img = tile(tx, ty);
                if (img != null) {
                    g2.drawImage(img, drawX, drawY, null);
                    loaded++;
                } else {
                    if (failedUntil.containsKey(ZOOM + "/" + tx + "/" + ty)) anyFailed = true;
                    g2.setColor(new Color(0xDDD9D0));
                    g2.fillRect(drawX, drawY, TILE, TILE);
                }
            }
        }

        // 3. Nothing loaded yet -> tell the user what's happening.
        if (loaded == 0) {
            g2.setColor(new Color(0x333333));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            String msg = anyFailed
                    ? "Map unavailable offline \u2014 drop a unilag.png in the app folder to show a map without internet."
                    : "Loading OpenStreetMap tiles\u2026";
            g2.drawString(msg, 16, 24);
        }
        drawAttribution(g2, w, h);
    }

    private void drawAttribution(Graphics2D g2, int w, int h) {
        String text = "\u00A9 OpenStreetMap contributors";
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text), pad = 5;
        int x = w - tw - pad * 2 - 4, y = h - fm.getHeight() - 4;
        g2.setColor(new Color(255, 255, 255, 190));
        g2.fillRect(x, y, tw + pad * 2, fm.getHeight());
        g2.setColor(new Color(0x333333));
        g2.drawString(text, x + pad, y + fm.getAscent());
    }

    /** Returns a cached tile, or null while it is (re)fetched in the background. */
    private BufferedImage tile(int x, int y) {
        String key = ZOOM + "/" + x + "/" + y;
        BufferedImage img = cache.get(key);
        if (img != null) return img;

        Long until = failedUntil.get(key);
        if (until != null && System.currentTimeMillis() < until) return null; // backing off

        if (inFlight.add(key)) {
            pool.submit(() -> {
                try {
                    URL url = new URL(String.format(TILE_URL, ZOOM, x, y));
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setRequestProperty("User-Agent", USER_AGENT);
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    try (InputStream in = c.getInputStream()) {
                        BufferedImage t = ImageIO.read(in);
                        if (t != null) {
                            cache.put(key, t);
                            failedUntil.remove(key);
                        }
                    }
                } catch (Exception e) {
                    failedUntil.put(key, System.currentTimeMillis() + 20_000); // retry later, don't spam
                } finally {
                    inFlight.remove(key);
                    if (onTileLoaded != null) onTileLoaded.run();
                }
            });
        }
        return null;
    }
}