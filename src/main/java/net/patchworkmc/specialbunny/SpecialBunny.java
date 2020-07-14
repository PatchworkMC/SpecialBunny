package net.patchworkmc.specialbunny;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SpecialBunny {
	private static final int ALL = 0;
	private static final int COREMOD = 1;
	private static final int FABRIC = 2;
	private static final int FORGE = 3;
	private static final int BOTH = 4;
	private static final int NEITHER = 5;
	private static final int AT = 6;
	private static final int FORGE_LEGACY = 7;
	private static final int MCREATOR = 8;
	private static final int MIXINS_FABRIC = 9;
	private static final int MIXINS_FORGE = 10;

	// aggressive scan results
	private static final int AGGRESSIVE_MCREATOR = 0;
	private static final int AGGRESSIVE_MIXINS = 1;

	public static void main(String[] args) throws Throwable {
		System.out.println("PatchworkMC SpecialBunny: Mass Mod Statistics");

		Path holder = Paths.get("input");
		Path output = Paths.get("output");

		Files.createDirectories(output);

		final int[] totals = new int[11];

		Files.walkFileTree(holder, new SimpleFileVisitor<Path>() {
			public FileVisitResult visitFile(Path candidate, BasicFileAttributes attributes) throws IOException {
				Objects.requireNonNull(candidate);
				Objects.requireNonNull(attributes);

				URI uri;

				try {
					uri = new URI("jar:" + candidate.toUri().toString());
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}

				FileSystem jar;

				try {
					jar = FileSystems.newFileSystem(uri, Collections.emptyMap());
				} catch (IOException | UnsupportedOperationException swallowed) {
					// Probably not a valid JAR. Skip it.

					return FileVisitResult.CONTINUE;
				}

				totals[ALL]++;

				Path manifest = jar.getPath("/META-INF/coremods.json");

				try {
					String coremods = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);

					handleCoreMods(candidate, jar, coremods, output);

					totals[COREMOD]++;
				} catch (NoSuchFileException swallowed) {
					// no coremods
				}

				Path atPath = jar.getPath("/META-INF/accesstransformer.cfg");

				try {
					String accesstransformers = new String(Files.readAllBytes(atPath), StandardCharsets.UTF_8);

					Path modFolder = createModFolder(candidate, output);
					Files.copy(atPath, modFolder.resolve("accesstransformer.cfg"), StandardCopyOption.REPLACE_EXISTING);

					totals[AT]++;
				} catch (NoSuchFileException swallowed) {
					// no ATs
				}

				Path servicesPath = jar.getPath("/META-INF/services");

				try {
					scanServices(servicesPath);

					// Path modFolder = createModFolder(candidate, output);
					// Files.copy(servicesPath, modFolder.resolve("services"));

					System.out.println("Mod with services: " + candidate);
				} catch (NoSuchFileException swallowed) {
					// no services
				}

				boolean fabric = Files.exists(jar.getPath("/fabric.mod.json"));
				boolean forge = Files.exists(jar.getPath("/META-INF/mods.toml"));
				boolean forgeLegacy = Files.exists(jar.getPath("/mcmod.info"));

				boolean[] aggressive = aggressiveScan(jar.getPath("/"));
				// System.out.println("Discovered MCreator mod with aggressive scan: " + candidate);
				boolean mcreator = Files.exists(jar.getPath("/net/mcreator")) || aggressive[AGGRESSIVE_MCREATOR];
				boolean mixins = aggressive[AGGRESSIVE_MIXINS];

				if (mixins) {
					if (fabric) {
						totals[MIXINS_FABRIC]++;
					}

					if (forge) {
						totals[MIXINS_FORGE]++;
					}
				}

				if (mcreator) {
					if (!forge && !forgeLegacy) {
						System.err.println("Non-forge MCreator mod? " + candidate);
					}

					totals[MCREATOR]++;
				}

				if (fabric && forge) {
					System.err.println("Mod had both fabric and forge? " + candidate);
					totals[BOTH]++;
				} else if (fabric) {
					totals[FABRIC]++;
				} else if (forge) {
					totals[FORGE]++;
				} else if(forgeLegacy) {
					System.err.println("Some dummy published a 1.12 or below mod on 1.13+: " + candidate);
					totals[FORGE_LEGACY]++;
				} else {
					System.err.println("Mod had neither fabric nor forge? " + candidate);
					totals[NEITHER]++;
				}

				return FileVisitResult.CONTINUE;
			}
		});

		System.out.println("Total mods: " + totals[ALL]);
		System.out.println("Total mods using MCreator: " + totals[MCREATOR] + " (" + percent(totals[MCREATOR], totals[FORGE]) + " of Forge mods)");
		System.out.println("Total mods with core mods: " + totals[COREMOD] + " (" + percent(totals[COREMOD], totals[FORGE]) + " of Forge mods, " + percent(totals[COREMOD], totals[FORGE] - totals[MCREATOR]) + " excluding MCreator)");
		System.out.println("Total mods with access transformers: " + totals[AT] + " (" + percent(totals[AT], totals[FORGE]) + " of Forge mods, " + percent(totals[AT], totals[FORGE] - totals[MCREATOR]) + " excluding MCreator)");

		System.out.println("Forge mods using Mixins: " + totals[MIXINS_FORGE]  + " (" + percent(totals[MIXINS_FORGE], totals[FORGE]) + " of Forge mods, " + percent(totals[MIXINS_FORGE], totals[FORGE] - totals[MCREATOR]) + " excluding MCreator)");
		System.out.println("Fabric mods using Mixins: " + totals[MIXINS_FABRIC] + " (" + percent(totals[MIXINS_FABRIC], totals[FABRIC]) + " of Fabric mods)");

		System.out.println("Fabric: " + totals[FABRIC] + " (" + percent(totals[FABRIC], totals[ALL]) + ") Forge: " + totals[FORGE] + " (" + percent(totals[FORGE], totals[ALL]) + ") Both: " + totals[BOTH] + " (" + percent(totals[BOTH], totals[ALL]) + ") Forge 1.12 or below: " + totals[FORGE_LEGACY] + " (" + percent(totals[FORGE_LEGACY], totals[ALL]) + ") Neither: " + totals[NEITHER] + " (" + percent(totals[NEITHER], totals[ALL]) + ")");
	}

	private static String percent(int value, int divisor) {
		return ((value * 1000 / divisor) / 10D) + "%";
	}

	private static boolean[] aggressiveScan(Path root) throws IOException {
		boolean[] found = new boolean[2];

		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			public FileVisitResult visitFile(Path candidate, BasicFileAttributes attributes) throws IOException {
				if (candidate.toString().endsWith("ModElement.class")) {
					found[AGGRESSIVE_MCREATOR] = true;
				} else if (candidate.toString().endsWith("mixins.json") || candidate.toString().contains("refmap") || (candidate.toString().endsWith(".json") && candidate.toString().startsWith("mixins"))) {
					found[AGGRESSIVE_MIXINS] = true;
				}

				return FileVisitResult.CONTINUE;
			}
		});

		return found;
	}

	private static void scanServices(Path root) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			public FileVisitResult visitFile(Path candidate, BasicFileAttributes attributes) throws IOException {
				System.out.println("Service in " + root + " " + candidate);

				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static Path createModFolder(Path root, Path output) throws IOException {
		Path modFolder = output.resolve(root.getFileName().toString());

		if (!Files.exists(modFolder)) {
			Files.createDirectory(modFolder);
		}

		return modFolder;
	}

	private static void handleCoreMods(Path root, FileSystem jar, String coremods, Path output) throws IOException {
		System.out.println("Found coremods.json in " + root);
		System.out.println(coremods);

		JsonParser parser = new JsonParser();

		JsonElement element = parser.parse(coremods);

		if (!(element instanceof JsonObject)) {
			throw new RuntimeException("parsed coremods.json was not a JsonObject");
		}

		JsonObject map = (JsonObject) element;

		Path modFolder = createModFolder(root, output);

		for(Map.Entry<String, JsonElement> entry:  map.entrySet()) {
			String name = entry.getKey();
			String path = entry.getValue().getAsString();

			Path coremodFile = jar.getPath(path);
			String javascript = new String(Files.readAllBytes(coremodFile), StandardCharsets.UTF_8);

			String fileName = coremodFile.getFileName().toString();
			Path target = modFolder.resolve(fileName);

			while (Files.exists(target)) {
				System.err.println("Coremod already exists: " + target);

				fileName = "-" + fileName;
				target = modFolder.resolve(fileName);
			}

			Files.copy(coremodFile, target, StandardCopyOption.REPLACE_EXISTING);

			System.out.println("Found coremod: " + name);
			//System.out.println(javascript);
		}
	}
}
