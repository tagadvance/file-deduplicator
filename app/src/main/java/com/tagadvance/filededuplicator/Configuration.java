package com.tagadvance.filededuplicator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.StreamSupport;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public record Configuration(
	boolean dryRun, Path deduplication, boolean safeDelete, Path trash,
	boolean replaceWithSymlink, List<String> roots, List<String> inclusions,
	List<String> exclusions) {

	public static Configuration parseFile(final File file) throws IOException {
		try (final var in = new FileInputStream(file)) {
			return parseInputStream(in);
		}
	}

	public static Configuration parseInputStream(final InputStream in) {
		final var settings = LoadSettings.builder().setLabel("Custom user configuration").build();
		final var load = new Load(settings);
		final var objects = load.loadAllFromInputStream(in);
		final var spliterator = objects.spliterator();
		final var parallel = false;
		final var config = (LinkedHashMap<String, ?>) StreamSupport.stream(spliterator, parallel)
			.findFirst()
			.orElseThrow();

		final var druRun = (boolean) config.get("dryRun");
		final var dedpulication = Paths.get((String) config.get("deduplication"));
		final var safeDelete = (boolean) config.get("safeDelete");
		final var trash = Paths.get((String) config.get("trash"));
		final var replaceWithSymlink = (boolean) config.get("replaceWithSymlink");
		final var roots = (List<String>) config.get("roots");
		final var inclusions = (List<String>) config.get("inclusions");
		final var exclusions = (List<String>) config.get("exclusions");

		return new Configuration(druRun, dedpulication, safeDelete, trash, replaceWithSymlink,
			roots, inclusions, exclusions);
	}

}
