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

		ModScanner scanner = new ModScanner();
		scanner.setOutputDirectory(output);

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

				ModInfo info = scanner.scan(jar);

				totals[ALL]++;

				if (info.hasForgeCoremods) {
					totals[COREMOD]++;
				}

				if (info.accessTransformers.isPresent()) {
					totals[AT]++;
				}

				// TODO: count services?

				if (info.hasMixins) {
					if (info.isFabric) {
						totals[MIXINS_FABRIC]++;
					}

					if (info.isForge == ModInfo.ForgeType.YES) {
						totals[MIXINS_FORGE]++;
					}
				}

				if (info.isMCreator) {
					if (info.isForge == ModInfo.ForgeType.NO) {
						System.err.println("Non-forge MCreator mod? " + candidate);
					}

					totals[MCREATOR]++;
				}

				if (info.isFabric && info.isForge == ModInfo.ForgeType.YES) {
					System.err.println("Mod had both fabric and forge? " + candidate);
					totals[BOTH]++;
				} else if (info.isFabric) {
					totals[FABRIC]++;
				} else if (info.isForge == ModInfo.ForgeType.YES) {
					totals[FORGE]++;
				} else if(info.isForge == ModInfo.ForgeType.LEGACY) {
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
}
