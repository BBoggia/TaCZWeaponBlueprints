package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure press/drag/release classifier. Selection is intentionally deferred until
 * release so a node-originated pan cannot also activate that node.
 */
public final class ResearchTreeGestureTracker {
    public static final int LEFT_BUTTON = 0;
    public static final int MIDDLE_BUTTON = 2;
    public static final double DEFAULT_DRAG_THRESHOLD = 3.0D;

    private final double dragThresholdSquared;
    private boolean active;
    private boolean dragging;
    private int button = -1;
    private double pressX;
    private double pressY;
    private ResourceLocation candidateNodeId;

    public ResearchTreeGestureTracker() {
        this(DEFAULT_DRAG_THRESHOLD);
    }

    public ResearchTreeGestureTracker(double dragThreshold) {
        if (!Double.isFinite(dragThreshold) || dragThreshold <= 0.0D) {
            throw new IllegalArgumentException("Research Tree drag threshold must be positive");
        }
        dragThresholdSquared = dragThreshold * dragThreshold;
    }

    public boolean press(
            double mouseX,
            double mouseY,
            int mouseButton,
            ResourceLocation candidateNodeId) {
        validateCoordinates(mouseX, mouseY);
        if (mouseButton != LEFT_BUTTON && mouseButton != MIDDLE_BUTTON) {
            return false;
        }
        if (active) {
            return false;
        }
        active = true;
        dragging = false;
        button = mouseButton;
        pressX = mouseX;
        pressY = mouseY;
        this.candidateNodeId = mouseButton == LEFT_BUTTON ? candidateNodeId : null;
        return true;
    }

    public Movement move(double mouseX, double mouseY) {
        validateCoordinates(mouseX, mouseY);
        if (!active) {
            return Movement.NONE;
        }
        if (dragging) {
            return Movement.DRAGGING;
        }
        double deltaX = mouseX - pressX;
        double deltaY = mouseY - pressY;
        if (deltaX * deltaX + deltaY * deltaY < dragThresholdSquared) {
            return Movement.NONE;
        }
        dragging = true;
        candidateNodeId = null;
        return Movement.STARTED_DRAG;
    }

    public Outcome release(double mouseX, double mouseY, int mouseButton) {
        validateCoordinates(mouseX, mouseY);
        if (!active || mouseButton != button) {
            return Outcome.NONE;
        }
        move(mouseX, mouseY);
        Outcome result;
        if (dragging) {
            result = Outcome.panEnd();
        } else if (button == LEFT_BUTTON && candidateNodeId != null) {
            result = Outcome.nodeClick(candidateNodeId);
        } else if (button == LEFT_BUTTON) {
            result = Outcome.backgroundClick();
        } else {
            result = Outcome.NONE;
        }
        cancel();
        return result;
    }

    public void cancel() {
        active = false;
        dragging = false;
        button = -1;
        candidateNodeId = null;
    }

    public boolean active() {
        return active;
    }

    public boolean dragging() {
        return dragging;
    }

    private static void validateCoordinates(double mouseX, double mouseY) {
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)) {
            throw new IllegalArgumentException("Research Tree pointer coordinates must be finite");
        }
    }

    public enum Movement {
        NONE,
        STARTED_DRAG,
        DRAGGING
    }

    public record Outcome(Type type, Optional<ResourceLocation> nodeId) {
        public static final Outcome NONE = new Outcome(Type.NONE, Optional.empty());

        public Outcome {
            if (type == null) {
                throw new IllegalArgumentException("Research Tree gesture outcome type cannot be null");
            }
            nodeId = nodeId == null ? Optional.empty() : nodeId;
            if ((type == Type.NODE_CLICK) != nodeId.isPresent()) {
                throw new IllegalArgumentException("Research Tree gesture node outcome is inconsistent");
            }
        }

        private static Outcome nodeClick(ResourceLocation nodeId) {
            return new Outcome(Type.NODE_CLICK, Optional.of(nodeId));
        }

        private static Outcome backgroundClick() {
            return new Outcome(Type.BACKGROUND_CLICK, Optional.empty());
        }

        private static Outcome panEnd() {
            return new Outcome(Type.PAN_END, Optional.empty());
        }
    }

    public enum Type {
        NONE,
        NODE_CLICK,
        BACKGROUND_CLICK,
        PAN_END
    }
}
