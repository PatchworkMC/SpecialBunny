package net.patchworkmc.specialbunny;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ModScanner {
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

        info.isMCreator = Files.exists(jar.getPath("/net/mcreator"));

        aggressiveScan(jar.getPath("/"), info); // MCreator and Mixins

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

        System.out.println("Found coremods.json in " + fs.getPath("/"));
        System.out.println(coremods);

        JsonParser parser = new JsonParser();

        JsonElement element = parser.parse(coremods);

        if (!(element instanceof JsonObject)) {
            throw new RuntimeException("parsed coremods.json was not a JsonObject");
        }

        JsonObject map = (JsonObject) element;

        getModFolder(fs).ifPresent(modFolder -> {
            for(Map.Entry<String, JsonElement> entry:  map.entrySet()) {
                String name = entry.getKey();
                String path = entry.getValue().getAsString();

                Path coremodFile = fs.getPath(path);

                String fileName = coremodFile.getFileName().toString();
                Path target = modFolder.resolve(fileName);

                while (Files.exists(target)) {
                    System.err.println("Coremod already exists: " + target);

                    fileName = "-" + fileName;
                    target = modFolder.resolve(fileName);
                }

                try {
                    Files.copy(coremodFile, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Failed to copy file: " + coremodFile);
                    e.printStackTrace();
                    continue;
                }

                System.out.println("Found coremod: " + name);

//                try {
//                    String javascript = new String(Files.readAllBytes(coremodFile), StandardCharsets.UTF_8);
//                    System.out.println(javascript);
//                } catch (IOException e) {
//                    System.err.println("Failed to read file: " + coremodFile);
//                    e.printStackTrace();
//                }
            }
        });

        return true;
    }

    private Optional<String> checkAccessTransformers(FileSystem fs) {
        Path atPath = fs.getPath("/META-INF/accesstransformer.cfg");
        String accesstransformers;
        try {
             accesstransformers = new String(Files.readAllBytes(atPath), StandardCharsets.UTF_8);
        } catch (IOException swallowed) {
            // no access transformers
            return Optional.empty();
        }

        getModFolder(fs).ifPresent(modFolder -> {
            try {
                Files.copy(atPath, modFolder.resolve("accesstransformer.cfg"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Failed to copy accesstransformers.");
                e.printStackTrace();
            }
        });

        return Optional.of(accesstransformers);
    }

    private void scanServices(FileSystem fs) {
        Path servicesPath = fs.getPath("/META-INF/services");
        try {
//             getModFolder(fs).ifPresent(modFolder -> {
//                 try {
//                     Files.copy(servicesPath, modFolder.resolve("services"));
//                 } catch (IOException e) {
//                     System.err.println("Failed to copy services.");
//                     e.printStackTrace();
//                 }
//             });

            Files.walkFileTree(servicesPath, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path candidate, BasicFileAttributes attributes) throws IOException {
                    System.out.println("Service in " + servicesPath + " " + candidate);

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
            // TODO: original doesn't catch this exception i think, should we just let this be a fatal error?
        }
    }

    private Optional<Path> getModFolder(FileSystem fs) {
        Optional<Path> modFolder = outputPath.map(p -> p.resolve(fs.getPath("/").getFileName()));

        if (modFolder.isPresent() && !Files.exists(modFolder.get())) {
            try {
                Files.createDirectory(modFolder.get());
            } catch (IOException e) {
                System.err.println("Failed to create output directory! Ignoring.");
                return Optional.empty();
            }
        }

        return modFolder;
    }
}
