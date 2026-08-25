package com.gamergaming.taczweaponblueprints.client;

/** Pure pan/zoom transform for the Research Bench tree canvas. */
public final class ResearchTreeViewport {
    /** Small enough for the packaged 54-node tree to fit in the bench viewport. */
    public static final double MIN_SCALE = 0.25D;
    public static final double MAX_SCALE = 1.5D;
    public static final double SCALE_STEP = 0.25D;
    private static final double MIN_FIT_SCALE = 1.0D / 1_000_000.0D;

    private int viewportWidth;
    private int viewportHeight;
    private int canvasWidth;
    private int canvasHeight;
    private double panX;
    private double panY;
    private double scale = 1.0D;

    public void configure(int viewportWidth, int viewportHeight, int canvasWidth, int canvasHeight) {
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
        this.canvasWidth = Math.max(0, canvasWidth);
        this.canvasHeight = Math.max(0, canvasHeight);
        clamp();
    }

    /** Replaces the content bounds while retaining this presentation's last viewport size. */
    public void replaceCanvas(int canvasWidth, int canvasHeight, boolean fit) {
        this.canvasWidth = Math.max(0, canvasWidth);
        this.canvasHeight = Math.max(0, canvasHeight);
        if (fit) {
            fit();
        } else {
            clamp();
        }
    }

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    public double scale() {
        return scale;
    }

    public Snapshot snapshot() {
        return new Snapshot(panX, panY, scale);
    }

    public void restore(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Research Tree viewport snapshot cannot be null");
        }
        panX = snapshot.panX();
        panY = snapshot.panY();
        scale = Math.max(MIN_FIT_SCALE, Math.min(snapshot.scale(), MAX_SCALE));
        clamp();
    }

    public double canvasX(double viewportX) {
        return panX + viewportX / scale;
    }

    public double canvasY(double viewportY) {
        return panY + viewportY / scale;
    }

    public int viewportX(double canvasX) {
        return (int) Math.round((canvasX - panX) * scale);
    }

    public int viewportY(double canvasY) {
        return (int) Math.round((canvasY - panY) * scale);
    }

    public void panByScreenDelta(double deltaX, double deltaY) {
        panX -= deltaX / scale;
        panY -= deltaY / scale;
        clamp();
    }

    public void zoomAt(double wheelDelta, double viewportX, double viewportY) {
        if (wheelDelta == 0.0D) {
            return;
        }
        if (scale < MIN_SCALE && wheelDelta < 0.0D) {
            return;
        }
        double anchorX = canvasX(viewportX);
        double anchorY = canvasY(viewportY);
        double nextScale = scale < MIN_SCALE
                ? MIN_SCALE
                : scale + Math.copySign(SCALE_STEP, wheelDelta);
        scale = clampScale(nextScale);
        panX = anchorX - viewportX / scale;
        panY = anchorY - viewportY / scale;
        clamp();
    }

    public void focus(double x, double y, double width, double height) {
        double visibleWidth = viewportWidth / scale;
        double visibleHeight = viewportHeight / scale;
        panX = x + width / 2.0D - visibleWidth / 2.0D;
        panY = y + height / 2.0D - visibleHeight / 2.0D;
        clamp();
    }

    public void fit() {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            scale = 1.0D;
            panX = 0.0D;
            panY = 0.0D;
            return;
        }
        scale = clampFitScale(Math.min(
                1.0D,
                Math.min(viewportWidth / (double) canvasWidth, viewportHeight / (double) canvasHeight)));
        focus(0, 0, canvasWidth, canvasHeight);
    }

    public void fit(double x, double y, double width, double height) {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || x < 0.0D || y < 0.0D
                || width <= 0.0D || height <= 0.0D
                || x + width > canvasWidth || y + height > canvasHeight) {
            throw new IllegalArgumentException("invalid Research Tree focus bounds");
        }
        scale = clampFitScale(Math.min(
                1.0D,
                Math.min(viewportWidth / width, viewportHeight / height)));
        focus(x, y, width, height);
    }

    public boolean intersects(double x, double y, double width, double height) {
        double visibleWidth = viewportWidth / scale;
        double visibleHeight = viewportHeight / scale;
        return x + width >= panX && x <= panX + visibleWidth
                && y + height >= panY && y <= panY + visibleHeight;
    }

    private void clamp() {
        double visibleWidth = viewportWidth / scale;
        double visibleHeight = viewportHeight / scale;
        panX = clampAxis(panX, canvasWidth, visibleWidth);
        panY = clampAxis(panY, canvasHeight, visibleHeight);
    }

    private static double clampAxis(double value, double canvasSize, double visibleSize) {
        if (canvasSize <= visibleSize) {
            return (canvasSize - visibleSize) / 2.0D;
        }
        return Math.max(0.0D, Math.min(value, canvasSize - visibleSize));
    }

    private static double clampScale(double value) {
        return Math.max(MIN_SCALE, Math.min(value, MAX_SCALE));
    }

    private static double clampFitScale(double value) {
        return Math.max(MIN_FIT_SCALE, Math.min(value, MAX_SCALE));
    }

    public record Snapshot(double panX, double panY, double scale) {
        public Snapshot {
            if (!Double.isFinite(panX) || !Double.isFinite(panY)
                    || !Double.isFinite(scale) || scale <= 0.0D) {
                throw new IllegalArgumentException("invalid Research Tree viewport snapshot");
            }
        }
    }
}
