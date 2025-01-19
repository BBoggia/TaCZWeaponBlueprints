package com.gamergaming.taczweaponblueprints.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemNameFilterHelper {

    private static final List<String> ALL_PATTERN_LIST = Collections.unmodifiableList(
        Arrays.asList("\\((.*)\\)\\s*$")   
    );

    private static final List<String> ATTACHMENT_PATTERN_LIST = Collections.unmodifiableList(
        Arrays.asList(
            "\\bsight\\b", "\\bscope\\b", 
            "\\bsilencer\\b", "\\bmuzzle\\b", "\\bbayonet\\b", 
            "\\b\sstock\\b", "\\bp?mag\\b"
        )
    );

    public static String filterAttachmentName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        if (name.contains("\"")) {
            name = addSpaceBeforeFirstQuote(name);
        }

        Pattern p = Pattern.compile("\\bred\\s*dot\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(name);
        
        name = m.replaceAll("Red Dot");

        for (String pattern : ATTACHMENT_PATTERN_LIST) {
            p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            m = p.matcher(name);
            
            if (m.find()) {
                int idx = m.start();
                if (name.charAt(idx - 1) == ' ') {
                    name = name.substring(0, idx - 1) + String.valueOf(name.charAt(idx)).toUpperCase() + name.substring(idx + 1);
                } else {
                    name = name.substring(0, idx) + " " + String.valueOf(name.charAt(idx)).toUpperCase() + name.substring(idx + 1);
                }
            }
                
        }

        name = name.replaceAll("\\s{2,}", " ").strip();
        name = runAllPatternList(name);
        name = addSpaceBeforeFirstQuote(name);

        return name;
    }

    public static String filterAmmoName(String name) {
        if (name.toLowerCase().endsWith("bullet")) {
            name = name.substring(0, name.length() - 6).strip();
        }

        return name;
    }

    public static String filterGunName(String name) {

        if (name.contains("-(")) {
            name = name.substring(0, name.indexOf("-("));
        }

        if (name.startsWith("Military")) {
            name = name.replace("Military", "").strip();

        } else if (name.startsWith("Custom")) {
            name = name.replace("Custom", "").strip();

        } else if (name.startsWith("Tactical")) {
            name = name.replace("Tactical", "").strip();
        }

        // check if it ends with assault rifle but ignore case
        if (name.toLowerCase().endsWith("assault rifle")) {
            name = name.substring(0, name.length() - 13).strip();

        } else if (name.toLowerCase().endsWith("sniper rifle")) {
            name = name.substring(0, name.length() - 12).strip();

        } else if (name.toLowerCase().endsWith("rifle") && !name.toLowerCase().equals("gauss rifle")) {
            name = name.substring(0, name.length() - 5).strip();
        } else if (name.toLowerCase().endsWith("sniper")) {
            name = name.substring(0, name.length() - 6).strip();
        } else if (name.toLowerCase().endsWith("pistol")) {
            name = name.substring(0, name.length() - 6).strip();
        } else if (name.toLowerCase().endsWith("shotgun")) {
            name = name.substring(0, name.length() - 7).strip();
        } else if (name.toLowerCase().endsWith("smg")) {
            name = name.substring(0, name.length() - 3).strip();
        } else if (name.toLowerCase().endsWith("submachine gun")) {
            name = name.substring(0, name.length() - 15).strip();
        } else if (name.toLowerCase().endsWith("machine gun")) {
            name = name.substring(0, name.length() - 12).strip();
        }

        name = runAllPatternList(name);
        name = addSpaceBeforeFirstQuote(name);

        return name;
    }

    private static String runAllPatternList(String name) {
        for (String pattern : ALL_PATTERN_LIST) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(name);

            if (m.find()) {
                name = m.replaceAll("").strip();
            }
        }

        return name;
    }

    private static String addSpaceBeforeFirstQuote(String name) {
        Pattern pattern = Pattern.compile("(?<!\\s)\"");
        Matcher matcher = pattern.matcher(name);
        
        if (matcher.find()) {
            // Replace only the first match
            StringBuffer sb = new StringBuffer();
            matcher.appendReplacement(sb, " \"");
            matcher.appendTail(sb);
            return sb.toString();
        }

        return name;
    }
}
