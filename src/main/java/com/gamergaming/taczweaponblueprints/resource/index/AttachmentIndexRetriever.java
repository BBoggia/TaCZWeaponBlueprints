package com.gamergaming.taczweaponblueprints.resource.index;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.network.CommonGunPackNetwork;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.AmmoIndexPOJO;
import com.tacz.guns.resource.pojo.AttachmentIndexPOJO;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import com.tacz.guns.util.TacPathVisitor;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.tacz.guns.resource.CommonGunPackLoader.GSON;

public final class AttachmentIndexRetriever {
    private static final Pattern ATTACHMENT_INDEX_PATTERN = Pattern.compile("^(\\w+)/attachments/index/(\\w+)\\.json$");
    private static final Pattern GUNS_INDEX_PATTERN = Pattern.compile("^(\\w+)/guns/index/(\\w+)\\.json$");
    private static final Pattern AMMO_INDEX_PATTERN = Pattern.compile("^(\\w+)/ammo/index/(\\w+)\\.json$");
    private static final Marker MARKER = MarkerManager.getMarker("CommonAttachmentIndexLoader");

    public static void loadAttachmentIndex(String path, ZipFile zipFile) throws IOException {
        Matcher matcher = ATTACHMENT_INDEX_PATTERN.matcher(path);
        if (matcher.find()) {
            String namespace = matcher.group(1);
            String id = matcher.group(2);
            ZipEntry entry = zipFile.getEntry(path);
            if (entry == null) {
                GunMod.LOGGER.warn(MARKER, "{} file don't exist", path);
                return;
            }
            try (InputStream stream = zipFile.getInputStream(entry)) {
                String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
                ResourceLocation registryName = new ResourceLocation(namespace, id);
                getAttachmentFromJsonString(registryName, json);
                CommonGunPackNetwork.addData(DataType.ATTACHMENT_INDEX, registryName, json);
            } catch (IllegalArgumentException | JsonSyntaxException | JsonIOException exception) {
                GunMod.LOGGER.warn("{} index file read fail!", path);
                exception.printStackTrace();
            }
        }
    }

    public static void loadAttachmentIndex(File root) throws IOException {
        Path[] itemPaths = new Path[]{root.toPath().resolve("attachments/index"), root.toPath().resolve("guns/index"), root.toPath().resolve("ammo/index")};

        for (Path itemPath : itemPaths) {
            TaCZWeaponBlueprints.LOGGER.info("!!! STARTING INDEX LOADING !!! - " + itemPath.toString());
            if (Files.isDirectory(itemPath)) {
                TacPathVisitor visitor = new TacPathVisitor(itemPath.toFile(), root.getName(), ".json", (id, file) -> {
                    try (InputStream stream = Files.newInputStream(file)) {
                        String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
                        if (itemPath.equals(itemPaths[0])) {
                            AttachmentIndexPOJO attIdxPojo = getAttachmentFromJsonString(id, json);
                            TaCZWeaponBlueprints.LOGGER.info("LOADING ATTACHMENT INDEX: " + id + " WITH INDEX: " + attIdxPojo);
                        } else if (itemPath.equals(itemPaths[1])) {
                            TaCZWeaponBlueprints.LOGGER.info("LOADING GUN INDEX: " + id + " WITH INDEX: " + getGunFromJsonString(id, json).toString());
                        } else if (itemPath.equals(itemPaths[2])) {
                            TaCZWeaponBlueprints.LOGGER.info("LOADING AMMO INDEX: " + id + " WITH INDEX: " + getAmmoFromJsonString(id, json).toString());
                        }
                    } catch (IllegalArgumentException | IOException | JsonSyntaxException | JsonIOException exception) {
                        GunMod.LOGGER.warn("{} index file read fail!", file);
                        exception.printStackTrace();
                    }
                });
                Files.walkFileTree(itemPath, visitor);
            }
        }
    }

    public static AttachmentIndexPOJO getAttachmentFromJsonString(ResourceLocation id, String json) {
        AttachmentIndexPOJO indexPOJO = GSON.fromJson(json, AttachmentIndexPOJO.class);
        return indexPOJO;
    }

    public static AmmoIndexPOJO getAmmoFromJsonString(ResourceLocation id, String json) {
        AmmoIndexPOJO indexPOJO = GSON.fromJson(json, AmmoIndexPOJO.class);
        return indexPOJO;
    }

    public static GunIndexPOJO getGunFromJsonString(ResourceLocation id, String json) {
        GunIndexPOJO indexPOJO = GSON.fromJson(json, GunIndexPOJO.class);
        return indexPOJO;
    }
}