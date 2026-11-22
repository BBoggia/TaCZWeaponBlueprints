package com.gamergaming.taczweaponblueprints.client;

/** Reserves a hit area for every domain before allocating the lane button and optional header text. */
record TechTreeToolbar(int groupingWidth, boolean compactGrouping, boolean compactDomains, int maxDomainWidth) {
    static TechTreeToolbar fit(int controlsLeft, int fullGroupingWidth, int fullDomainWidth, int domains) {
        int available = controlsLeft - 16;
        boolean compactGrouping = fullGroupingWidth + domains * 22 > available;
        int groupingWidth = compactGrouping ? 20 : fullGroupingWidth;
        int domainSpace = available - groupingWidth;
        int maxDomainWidth = Math.max(1, domainSpace / Math.max(1, domains) - 2);
        return new TechTreeToolbar(groupingWidth, compactGrouping, fullDomainWidth > domainSpace, maxDomainWidth);
    }
}
