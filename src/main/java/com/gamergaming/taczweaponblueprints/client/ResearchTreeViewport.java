package com.gamergaming.taczweaponblueprints.client;

/** Pure pan/zoom transform for the Research Bench tree canvas. */
public final class ResearchTreeViewport {
    /** Manual overview floor for large add-on trees; Fit may still frame farther out. */
    public static final double MIN_SCALE = 0.15D;
    public static final double MAX_SCALE = 1.5D;
    public static final double SCALE_STEP = 0.25D;
    private static final double MIN_FIT_SCALE = 1.0D / 1_000_000.0D;
    private static final double EASING_SPEED = 14.0D;
    private static final double PAN_SNAP_DISTANCE = 0.01D;
    private static final double SCALE_SNAP_DISTANCE = 0.0001D;

    private int viewportWidth;
    private int viewportHeight;
    private int canvasWidth;
    private int canvasHeight;
    private double panX;
    private double panY;
    private double scale = 1.0D;
    private double targetPanX;
    private double targetPanY;
    private double targetScale = 1.0D;
    private Insets safeInsets = Insets.NONE;
    private boolean animated;

    public void configure(int viewportWidth, int viewportHeight, int canvasWidth, int canvasHeight) {
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
        this.canvasWidth = Math.max(0, canvasWidth);
        this.canvasHeight = Math.max(0, canvasHeight);
        clampBoth();
    }

    /** Replaces the content bounds while retaining this presentation's last viewport size. */
    public void replaceCanvas(int canvasWidth, int canvasHeight, boolean fit) {
        this.canvasWidth = Math.max(0, canvasWidth);
        this.canvasHeight = Math.max(0, canvasHeight);
        if (fit) {
            fit();
        } else {
            clampBoth();
        }
    }

    public void setAnimated(boolean animated) {
        this.animated = animated;
        if (!animated) {
            finishAnimation();
        }
    }

    public void setSafeInsets(Insets safeInsets) {
        if (safeInsets == null) {
            throw new IllegalArgumentException("Research Tree safe insets cannot be null");
        }
        this.safeInsets = safeInsets;
        clampBoth();
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

    /** Screen-space size left after persistent fullscreen controls are excluded. */
    public ViewportSize unobscuredSize() {
        return new ViewportSize(availableWidth(), availableHeight());
    }

    /** Current unobscured camera rectangle, clipped to the logical tree canvas. */
    public CanvasBounds visibleCanvasBounds() {
        double left = clampToCanvas(
                panX + safeInsets.left() / scale, canvasWidth);
        double top = clampToCanvas(
                panY + safeInsets.top() / scale, canvasHeight);
        double right = clampToCanvas(
                panX + (viewportWidth - safeInsets.right()) / scale, canvasWidth);
        double bottom = clampToCanvas(
                panY + (viewportHeight - safeInsets.bottom()) / scale, canvasHeight);
        return new CanvasBounds(
                Math.min(left, right),
                Math.min(top, bottom),
                Math.abs(right - left),
                Math.abs(bottom - top));
    }

    public Snapshot snapshot() {
        return new Snapshot(targetPanX, targetPanY, targetScale);
    }

    public void restore(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Research Tree viewport snapshot cannot be null");
        }
        panX = snapshot.panX();
        panY = snapshot.panY();
        scale = Math.max(MIN_FIT_SCALE, Math.min(snapshot.scale(), MAX_SCALE));
        syncTargetToCurrent();
        clampBoth();
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
        validateFinite(deltaX, deltaY);
        cancelAnimation();
        panX -= deltaX / scale;
        panY -= deltaY / scale;
        clampCurrent();
        syncTargetToCurrent();
    }

    public void zoomAt(double wheelDelta, double viewportX, double viewportY) {
        if (wheelDelta == 0.0D) {
            return;
        }
        if (targetScale < MIN_SCALE && wheelDelta < 0.0D) {
            return;
        }
        double anchorX = canvasX(viewportX);
        double anchorY = canvasY(viewportY);
        double nextScale = targetScale < MIN_SCALE
                ? MIN_SCALE
                : targetScale + Math.copySign(SCALE_STEP, wheelDelta);
        targetScale = clampScale(nextScale);
        targetPanX = anchorX - viewportX / targetScale;
        targetPanY = anchorY - viewportY / targetScale;
        clampTarget();
        applyTargetImmediatelyWhenStatic();
    }

    public void focus(double x, double y, double width, double height) {
        validateFocusBounds(x, y, width, height, false);
        double safeCenterX = safeInsets.left()
                + availableWidth() / 2.0D;
        double safeCenterY = safeInsets.top()
                + availableHeight() / 2.0D;
        targetPanX = x + width / 2.0D - safeCenterX / targetScale;
        targetPanY = y + height / 2.0D - safeCenterY / targetScale;
        clampTarget();
        applyTargetImmediatelyWhenStatic();
    }

    /**
     * Moves only as far as necessary to expose a canvas rectangle inside the
     * unobscured viewport. Rapid keyboard moves use the destination camera so
     * they form one coherent transition instead of fighting unfinished motion.
     */
    public boolean reveal(
            double x,
            double y,
            double width,
            double height,
            double screenPadding) {
        validateFocusBounds(x, y, width, height, false);
        if (!Double.isFinite(screenPadding) || screenPadding < 0.0D) {
            throw new IllegalArgumentException("Research Tree reveal padding is invalid");
        }
        double horizontalPadding = Math.min(
                screenPadding,
                Math.max(0.0D, (availableWidth() - 1.0D) / 2.0D));
        double verticalPadding = Math.min(
                screenPadding,
                Math.max(0.0D, (availableHeight() - 1.0D) / 2.0D));
        double safeLeft = safeInsets.left() + horizontalPadding;
        double safeTop = safeInsets.top() + verticalPadding;
        double safeRight = viewportWidth - safeInsets.right() - horizontalPadding;
        double safeBottom = viewportHeight - safeInsets.bottom() - verticalPadding;
        double previousPanX = targetPanX;
        double previousPanY = targetPanY;
        targetPanX = revealAxis(
                targetPanX, x, x + width, safeLeft, safeRight, targetScale);
        targetPanY = revealAxis(
                targetPanY, y, y + height, safeTop, safeBottom, targetScale);
        clampTarget();
        boolean changed = Math.abs(targetPanX - previousPanX) > PAN_SNAP_DISTANCE
                || Math.abs(targetPanY - previousPanY) > PAN_SNAP_DISTANCE;
        applyTargetImmediatelyWhenStatic();
        return changed;
    }

    public void fit() {
        fitWithMinimumScale(0, 0, canvasWidth, canvasHeight, 0.0D, false);
    }

    /** Fits the complete canvas while retaining a usable lower zoom bound. */
    public void fitReadable(double minimumScale) {
        fitWithMinimumScale(0, 0, canvasWidth, canvasHeight, minimumScale, false);
    }

    /** Fits a canvas region while retaining a usable lower zoom bound. */
    public void fitReadable(
            double x,
            double y,
            double width,
            double height,
            double minimumScale) {
        fitWithMinimumScale(x, y, width, height, minimumScale, true);
    }

    private void fitWithMinimumScale(
            double x,
            double y,
            double width,
            double height,
            double minimumScale,
            boolean region) {
        if (!Double.isFinite(minimumScale) || minimumScale < 0.0D
                || minimumScale > 1.0D) {
            throw new IllegalArgumentException("Research Tree readable fit scale is invalid");
        }
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            if (region) {
                throw new IllegalArgumentException(
                        "cannot fit a Research Tree region without a canvas");
            }
            scale = 1.0D;
            panX = 0.0D;
            panY = 0.0D;
            syncTargetToCurrent();
            return;
        }
        validateFocusBounds(x, y, width, height, region);
        targetScale = clampFitScale(Math.min(
                1.0D,
                Math.min(availableWidth() / width,
                        availableHeight() / height)));
        targetScale = Math.max(targetScale, minimumScale);
        focus(x, y, width, height);
    }

    public void fit(double x, double y, double width, double height) {
        fitWithMinimumScale(x, y, width, height, 0.0D, true);
    }

    /** Advances a bounded fullscreen camera transition by one nominal render frame. */
    public boolean tick() {
        return tick(1.0D / 60.0D);
    }

    /** Advances using render-frame time so easing duration is frame-rate independent. */
    public boolean tick(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0D) {
            throw new IllegalArgumentException("Research Tree camera delta time is invalid");
        }
        if (!animated || !isAnimating()) {
            return false;
        }
        double factor = 1.0D - Math.exp(-EASING_SPEED * Math.min(deltaSeconds, 0.1D));
        panX = approach(panX, targetPanX, factor, PAN_SNAP_DISTANCE);
        panY = approach(panY, targetPanY, factor, PAN_SNAP_DISTANCE);
        scale = approach(scale, targetScale, factor, SCALE_SNAP_DISTANCE);
        clampCurrent();
        return true;
    }

    public boolean isAnimating() {
        return Math.abs(panX - targetPanX) > PAN_SNAP_DISTANCE
                || Math.abs(panY - targetPanY) > PAN_SNAP_DISTANCE
                || Math.abs(scale - targetScale) > SCALE_SNAP_DISTANCE;
    }

    public void finishAnimation() {
        panX = targetPanX;
        panY = targetPanY;
        scale = targetScale;
        clampCurrent();
        syncTargetToCurrent();
    }

    public void cancelAnimation() {
        syncTargetToCurrent();
    }

    public boolean intersects(double x, double y, double width, double height) {
        double visibleWidth = viewportWidth / scale;
        double visibleHeight = viewportHeight / scale;
        return x + width >= panX && x <= panX + visibleWidth
                && y + height >= panY && y <= panY + visibleHeight;
    }

    private void clampBoth() {
        clampCurrent();
        clampTarget();
    }

    private void clampCurrent() {
        panX = clampAxis(
                panX, canvasWidth, viewportWidth, scale, safeInsets.left(), safeInsets.right());
        panY = clampAxis(
                panY, canvasHeight, viewportHeight, scale, safeInsets.top(), safeInsets.bottom());
    }

    private void clampTarget() {
        targetPanX = clampAxis(
                targetPanX,
                canvasWidth,
                viewportWidth,
                targetScale,
                safeInsets.left(),
                safeInsets.right());
        targetPanY = clampAxis(
                targetPanY,
                canvasHeight,
                viewportHeight,
                targetScale,
                safeInsets.top(),
                safeInsets.bottom());
    }

    private static double clampAxis(
            double value,
            double canvasSize,
            double viewportSize,
            double scale,
            int startInset,
            int endInset) {
        double safeSize = Math.max(1.0D, viewportSize - startInset - endInset);
        double safeVisibleSize = safeSize / scale;
        if (canvasSize <= safeVisibleSize) {
            double safeCenter = startInset + safeSize / 2.0D;
            return canvasSize / 2.0D - safeCenter / scale;
        }
        double minimum = -startInset / scale;
        double maximum = canvasSize - (viewportSize - endInset) / scale;
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static double revealAxis(
            double pan,
            double contentStart,
            double contentEnd,
            double screenStart,
            double screenEnd,
            double scale) {
        double visibleStart = pan + screenStart / scale;
        double visibleEnd = pan + screenEnd / scale;
        double contentSize = contentEnd - contentStart;
        double visibleSize = visibleEnd - visibleStart;
        if (contentSize > visibleSize) {
            return contentStart + contentSize / 2.0D
                    - (screenStart + screenEnd) / (2.0D * scale);
        }
        if (contentStart < visibleStart) {
            return contentStart - screenStart / scale;
        }
        if (contentEnd > visibleEnd) {
            return contentEnd - screenEnd / scale;
        }
        return pan;
    }

    private int availableWidth() {
        return Math.max(1, viewportWidth - safeInsets.left() - safeInsets.right());
    }

    private int availableHeight() {
        return Math.max(1, viewportHeight - safeInsets.top() - safeInsets.bottom());
    }

    private void applyTargetImmediatelyWhenStatic() {
        if (!animated) {
            finishAnimation();
        }
    }

    private void syncTargetToCurrent() {
        targetPanX = panX;
        targetPanY = panY;
        targetScale = scale;
    }

    private void validateFocusBounds(
            double x,
            double y,
            double width,
            double height,
            boolean requireInsideCanvas) {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0.0D || height <= 0.0D
                || requireInsideCanvas && (x < 0.0D || y < 0.0D
                        || x + width > canvasWidth || y + height > canvasHeight)) {
            throw new IllegalArgumentException("invalid Research Tree focus bounds");
        }
    }

    private static void validateFinite(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) {
            throw new IllegalArgumentException("Research Tree camera delta must be finite");
        }
    }

    private static double approach(
            double current,
            double target,
            double factor,
            double snapDistance) {
        double next = current + (target - current) * factor;
        return Math.abs(next - target) <= snapDistance ? target : next;
    }

    private static double clampScale(double value) {
        return Math.max(MIN_SCALE, Math.min(value, MAX_SCALE));
    }

    private static double clampFitScale(double value) {
        return Math.max(MIN_FIT_SCALE, Math.min(value, MAX_SCALE));
    }

    private static double clampToCanvas(double value, int canvasSize) {
        return Math.max(0.0D, Math.min(value, canvasSize));
    }

    public record ViewportSize(int width, int height) {
        public ViewportSize {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("invalid unobscured Research Tree viewport size");
            }
        }
    }

    public record CanvasBounds(double x, double y, double width, double height) {
        public CanvasBounds {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(width) || !Double.isFinite(height)
                    || x < 0.0D || y < 0.0D || width < 0.0D || height < 0.0D) {
                throw new IllegalArgumentException("invalid visible Research Tree canvas bounds");
            }
        }
    }

    public record Snapshot(double panX, double panY, double scale) {
        public Snapshot {
            if (!Double.isFinite(panX) || !Double.isFinite(panY)
                    || !Double.isFinite(scale) || scale <= 0.0D) {
                throw new IllegalArgumentException("invalid Research Tree viewport snapshot");
            }
        }
    }

    public record Insets(int left, int top, int right, int bottom) {
        public static final Insets NONE = new Insets(0, 0, 0, 0);

        public Insets {
            if (left < 0 || top < 0 || right < 0 || bottom < 0) {
                throw new IllegalArgumentException("Research Tree safe insets cannot be negative");
            }
        }
    }
}
