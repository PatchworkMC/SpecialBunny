package net.patchworkmc.specialbunny;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ModScanner {
    private int totalMods = 0;
    private int forgeMods = 0;
    private int legacyForgeMods = 0;
    private int fabricMods = 0;
    private int MCreatorMods = 0;
    private int fabricAndForgeMods = 0; // aka "both"
    private int unknownMods = 0; // aka "neither"

    private int accessTransformers = 0;
    private int fabricMixins = 0;
    private int forgeMixins = 0;
    private int forgeCoremods = 0;

    private Optional<Path> outputPath = Optional.empty();

    public ModScanner() {

    }

    public ModInfo scan(Path path) throws IOException {
        FileSystem jar = FileSystems.newFileSystem(path, null);
        return scan(jar);
    }

    public void setOutputDirectory(Path path) {
        // TODO: make sure it's a directory, not a file
        outputPath = Optional.ofNullable(path);
    }

    public ModInfo scan(FileSystem jar) {
        ++totalMods;

        ModInfo info = new ModInfo();

        info.hasForgeCoremods = checkForgeCoreMods(jar);
        info.accessTransformers = checkAccessTransformers(jar);
        scanServices(jar);

        info.isFabric = Files.exists(jar.getPath("/fabric.mod.json"));

        if (Files.exists(jar.getPath("/META-INF/mods.toml"))) {
            info.isForge = ModInfo.ForgeType.YES;
        } else if (Files.exists(jar.getPath("/mcmod.info"))) {
            info.isForge = ModInfo.ForgeType.LEGACY;
        } else {
            info.isForge = ModInfo.ForgeType.NO;
        }

        Path candidate = jar.getPath("/");

        if (info.isFabric && info.isForge == ModInfo.ForgeType.YES) {
            System.err.println("Mod had both fabric and forge? " + candidate);
            ++fabricAndForgeMods;
        } else if (info.isFabric) {
            ++fabricMods;
        } else if (info.isForge == ModInfo.ForgeType.YES) {
            ++forgeMods;
        } else if (info.isForge == ModInfo.ForgeType.LEGACY) {
            System.err.println("Some dummy published a 1.12 or below mod on 1.13+: " + candidate);
            ++legacyForgeMods;
        } else {
            System.err.println("Mod had neither fabric nor forge? " + candidate);
            ++unknownMods;
        }

        info.isMCreator = Files.exists(jar.getPath("/net/mcreator"));

        aggressiveScan(jar.getPath("/"), info); // MCreator and Mixins

        if (info.hasMixins) {
            if (info.isFabric) {
                ++fabricMixins;
            }

            if (info.isForge == ModInfo.ForgeType.YES) {
                ++forgeMixins;
            }
        }

        if (info.isMCreator) {
            if (info.isForge == ModInfo.ForgeType.NO) {
                System.err.println("Non-forge MCreator mod? " + candidate);
            }

            ++MCreatorMods;
        }

        return info;
    }

    private boolean checkForgeCoreMods(FileSystem fs) {
        String coremods;
        try {
            coremods = new String(Files.readAllBytes(fs.getPath("/META-INF/coremods.json")), StandardCharsets.UTF_8);
        } catch (IOException swallowed) {
            // no coremods
            return false;
        }

        ++forgeCoremods;

        System.out.println("Found coremods.json in " + fs.getPath("/"));
        System.out.println(coremods);

        JsonParser parser = new JsonParser();

        JsonElement element = parser.parse(coremods);

        if (!(element instanceof JsonObject)) {
            throw new RuntimeException("parsed coremods.json was not a JsonObject");
        }

        JsonObject map = (JsonObject) element;

        // TODO: handle output
//        Path modFolder = createModFolder(root, output);
//
//        for(Map.Entry<String, JsonElement> entry:  map.entrySet()) {
//            String name = entry.getKey();
//            String path = entry.getValue().getAsString();
//
//            Path coremodFile = jar.getPath(path);
//            String javascript = new String(Files.readAllBytes(coremodFile), StandardCharsets.UTF_8);
//
//            String fileName = coremodFile.getFileName().toString();
//            Path target = modFolder.resolve(fileName);
//
//            while (Files.exists(target)) {
//                System.err.println("Coremod already exists: " + target);
//
//                fileName = "-" + fileName;
//                target = modFolder.resolve(fileName);
//            }
//
//            Files.copy(coremodFile, target, StandardCopyOption.REPLACE_EXISTING);
//
//            System.out.println("Found coremod: " + name);
//            //System.out.println(javascript);
//        }

        return true;
    }

    private Optional<String> checkAccessTransformers(FileSystem fs) {
        String accesstransformers;
        try {
             accesstransformers = new String(Files.readAllBytes(fs.getPath("/META-INF/accesstransformer.cfg")), StandardCharsets.UTF_8);
        } catch (IOException swallowed) {
            // no access transformers
            return Optional.empty();
        }
        ++accessTransformers;

        // TODO: output
//        Path modFolder = createModFolder(candidate, output);
//        Files.copy(atPath, modFolder.resolve("accesstransformer.cfg"), StandardCopyOption.REPLACE_EXISTING);

        return Optional.of(accesstransformers);
    }

    private static void scanServices(FileSystem fs) {
        Path root = fs.getPath("/META-INF/services");
        try {
            // this was already commented out, but may want to make output work later
            // Path modFolder = createModFolder(candidate, output);
            // Files.copy(servicesPath, modFolder.resolve("services"));

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path candidate, BasicFileAttributes attributes) throws IOException {
                    System.out.println("Service in " + root + " " + candidate);

                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("Mod with services: " + fs.getPath("/"));
        } catch (IOException swallowed) {
            // No services
        }
    }

    private static void aggressiveScan(Path root, ModInfo info) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path candidate, BasicFileAttributes attributes) {
                    if (candidate.toString().endsWith("ModElement.class")) {
                        info.isMCreator = true;
                    } else if (candidate.toString().endsWith("mixins.json") || candidate.toString().contains("refmap") || (candidate.toString().endsWith(".json") && candidate.toString().startsWith("mixins"))) {
                        info.hasMixins = true;
                    }

                    if (info.isMCreator && info.hasMixins) {
                        // we can return early, since there's nothing extra to glean
                        // weird combination though
                        return FileVisitResult.TERMINATE;
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException swallowed) {
            // TODO: original doesn't catch this exception i think, should we  just let this be a fatal error?
        }
    }

    public int getTotalMods() {
        return totalMods;
    }

    public int getForgeMods() {
        return forgeMods;
    }

    public int getLegacyForgeMods() {
        return legacyForgeMods;
    }

    public int getFabricMods() {
        return fabricMods;
    }

    public int getMCreatorMods() {
        return MCreatorMods;
    }

    public int getFabricAndForgeMods() {
        return fabricAndForgeMods;
    }

    public int getUnknownMods() {
        return unknownMods;
    }

    public int getAccessTransformers() {
        return accessTransformers;
    }

    public int getFabricMixins() {
        return fabricMixins;
    }

    public int getForgeMixins() {
        return forgeMixins;
    }

    public int getForgeCoremods() {
        return forgeCoremods;
    }
}
