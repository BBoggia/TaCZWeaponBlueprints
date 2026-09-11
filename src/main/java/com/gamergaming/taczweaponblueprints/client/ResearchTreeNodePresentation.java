package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.function.BiFunction;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeScene.Bounds;

/** One screen-size decision for a tile's artwork, actual name lines and corner marks. */
record ResearchTreeNodePresentation(ResearchTreeDetailPolicy.Channels channels, int nameCapacity,
        Bounds artwork, Bounds nameBounds, List<String> nameLines, Marks marks) {
    record State(ResearchTreeDetailPolicy.Channels channels, int nameCapacity) { }
    State state() { return new State(channels, nameCapacity); }

    record Marks(Bounds status, Bounds progression, Bounds inspected, Bounds tracked) { }

    static ResearchTreeNodePresentation resolve(State previous,
            Bounds body, double scale, int lineHeight, BiFunction<Integer, Integer, List<String>> wrapName) {
        var channels = ResearchTreeDetailPolicy.channels(previous == null
                ? ResearchTreeDetailPolicy.Channels.EMPTY : previous.channels(), body.width()*scale, body.height()*scale);
        double side = Math.min(5,body.width()/2), top = Math.min(6,body.height()/2), bottom = Math.min(5,body.height()/2);
        var interior = new Bounds(body.x()+side, body.y()+top, Math.max(0,body.width()-2*side), Math.max(0,body.height()-top-bottom));
        channels = new ResearchTreeDetailPolicy.Channels(channels.artwork() && interior.width()>0 && interior.height()>0,
                channels.names(), channels.stateMarks() && body.width()>=24 && body.height()>=24, channels.junctions());
        // Text consumes at most about two fifths of the image area. Each transition has a
        // one-pixel buffer; a short name never reserves an unused second line.
        double budget = interior.height()*scale*.42;
        int capacity = 0;
        if (channels.names()) for (int lines = 1; lines <= 2; lines++) {
            int buffer = previous != null && previous.nameCapacity() >= lines ? 1 : 0;
            if (budget >= lines*lineHeight+2-buffer) capacity = lines;
        }
        double nameWidth = Math.max(0,body.width()-24);
        var lines = capacity == 0 || nameWidth*scale < 1 ? List.<String>of()
                : List.copyOf(wrapName.apply((int)Math.floor(nameWidth*scale), capacity));
        if (lines.size() > capacity) throw new IllegalArgumentException("Too many name lines");
        var name = new Bounds(body.x()+Math.min(12,body.width()/2),
                interior.bottom()-lines.size()*lineHeight/scale, nameWidth, lines.size()*lineHeight/scale);
        var artwork = lines.isEmpty() ? interior : new Bounds(interior.x(), interior.y(), interior.width(),
                Math.max(0,name.y()-2/scale-interior.y()));
        return new ResearchTreeNodePresentation(channels, capacity, artwork, name, lines, new Marks(
                corner(body,body.width()-10,4,7,7),
                corner(body,4,4,7,9),
                corner(body,2,body.height()-8,8,6),
                corner(body,body.width()-10,body.height()-10,7,7)));
    }

    private static Bounds corner(Bounds body, double x, double y, double width, double height) {
        width = Math.min(width,body.width()); height = Math.min(height,body.height());
        return new Bounds(body.x()+Math.max(0,Math.min(x,body.width()-width)),
                body.y()+Math.max(0,Math.min(y,body.height()-height)),width,height);
    }

    boolean named() { return !nameLines.isEmpty(); }
}
