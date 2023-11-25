package com.tagadvance.filededuplicator;

import static com.tagadvance.filededuplicator.Hash.calculateHash;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileDeduplicator implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(FileDeduplicator.class);

	private final Configuration configuration;
	private final CsvDao csvDao;

	public FileDeduplicator(final Configuration configuration, final CsvDao csvDao) {
		this.configuration = requireNonNull(configuration, "configuration must not be null");
		this.csvDao = requireNonNull(csvDao, "csvDao must not be null");
	}

	@Override
	public void run() {
		final Map<Path, PathMeta> metaByPath = csvDao.selectAll()
			.stream()
			.collect(Collectors.toConcurrentMap(PathMeta::path, Function.identity()));

		configuration.roots()
			.stream()
			.parallel()
			.map(Paths::get)
			.forEach(path -> prefetch(metaByPath, path));

		processFiles();
	}

	private void prefetch(final Map<Path, PathMeta> metaByPath, final Path path) {
		final List<String> extensions = Collections.synchronizedList(new ArrayList<>());
		final Consumer<Path> peekExtension = filePath -> {
			if (!isIncluded(filePath) || !isNotExcluded(filePath)) {
				Utils.getExtension(filePath).ifPresent(extensions::add);
			}
		};

		try (Stream<Path> stream = Files.walk(path)) {
			stream.parallel()
				.filter(Files::isRegularFile)
				.peek(peekExtension)
				.forEach(filePath -> createAndStorePathMeta(metaByPath, filePath));
			csvDao.flush();
		} catch (final IOException e) {
			logger.error("Prefetch failed!", e);
		}

		printExtensionOptimizationHint(extensions);
	}

	private void createAndStorePathMeta(final Map<Path, PathMeta> metaByPath, final Path filePath) {
		if (metaByPath.containsKey(filePath)) {
			return;
		}

		final var stopwatch = Stopwatch.createStarted();

		try {
			final var size = Files.size(filePath);
			final var lastModified = Files.getLastModifiedTime(filePath).toMillis();
			final var hashes = calculateHash(filePath, Hash.ALGORITHM_MD5, Hash.ALGORITHM_SHA512);
			final var meta = new PathMeta(filePath, size, lastModified,
				hashes.get(Hash.ALGORITHM_MD5), hashes.get(Hash.ALGORITHM_SHA512));

			logger.debug("Hashed {} in {}", filePath, stopwatch);

			metaByPath.put(filePath, meta);
			csvDao.insert(meta);
		} catch (final IOException e) {
			logger.error(String.format("Failed to store hash for %s!", filePath), e);
		}
	}

	private void printExtensionOptimizationHint(final Collection<String> extensions) {
		final var extensionHints = extensions.stream()
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
		final var values = extensionHints.values()
			.stream()
			.sorted(((Comparator<Long>) Long::compareTo).reversed())
			.toList();
		final var cutoff = values.get(Math.min(10, values.size() / 10));
		logger.info("Please consider de-duplicating the following extensions:");
		extensionHints.forEach((ext, count) -> {
			if (count >= cutoff) {
				logger.info("{} => {}", ext, count);
			}
		});
	}

	private void processFiles() {
		final var allMetaPaths = csvDao.selectAll();
		final AtomicLong redundantDataSize = new AtomicLong();
		var metasByHash = allMetaPaths.stream()
			.parallel()
			.filter(meta -> isIncluded(meta.path()))
			.filter(meta -> isNotExcluded(meta.path()))
			.collect(Collectors.groupingBy(PathMeta::sha512));
		metasByHash.forEach((hash, metas) -> {
			if (isReadyForProcessing(metas)) {
				final var sum = metas.stream().mapToLong(PathMeta::size).skip(1).sum();
				redundantDataSize.addAndGet(sum);

				processDuplicates(metas);
			}
		});

		final AtomicLong redundantDataTotal = new AtomicLong();
		allMetaPaths.stream()
			.collect(Collectors.groupingBy(PathMeta::sha512))
			.forEach((hash, metas) -> {
				if (isReadyForProcessing(metas)) {
					final var sum = metas.stream().mapToLong(PathMeta::size).skip(1).sum();
					redundantDataTotal.addAndGet(sum);
				}
			});

		logger.info("{} of redundant data detected",
			Utils.humanReadableByteCountBin(redundantDataSize.get()));

		final var totalBytes = redundantDataTotal.get();
		final var difference = totalBytes - redundantDataSize.get();
		if (difference > 0) {
			logger.info("An additional {} of data may be deduplicated by processing all files.",
				Utils.humanReadableByteCountBin(difference));
		}
	}

	private boolean isIncluded(final Path path) {
		final var inclusions = configuration.inclusions();
		if (inclusions == null || inclusions.isEmpty()) {
			return true;
		}

		final var absolutePath = path.toAbsolutePath().toString();

		return inclusions.stream()
			.map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
			.map(pattern -> pattern.matcher(absolutePath))
			.anyMatch(Matcher::find);
	}

	private boolean isNotExcluded(final Path path) {
		final var exclusions = configuration.exclusions();
		if (exclusions == null || exclusions.isEmpty()) {
			return true;
		}

		final var absolutePath = path.toAbsolutePath().toString();

		return exclusions.stream()
			.map(Pattern::compile)
			.map(pattern -> pattern.matcher(absolutePath))
			.noneMatch(Matcher::find);
	}

	private boolean isReadyForProcessing(final Collection<PathMeta> metas) {
		// if both hashes match then the contents are the same
		// the odds of a md5sum and sha512sum both colliding are astronomically low
		final var count = metas.stream().map(PathMeta::md5).distinct().count();
		if (count > 1) {
			logger.warn("Hash collision detected for: {}", metas.stream()
				.map(PathMeta::path)
				.map(Path::getFileName)
				.map(Path::toString)
				.distinct()
				.collect(Collectors.joining(", ")));

			return false;
		}

		return metas.size() > 1;
	}

	private void processDuplicates(final List<PathMeta> metas) {
		final var sortedMetas = metas.stream()
			.sorted(Comparator.comparing(PathMeta::lastModified).reversed())
			.collect(Collectors.toList());
		final var prominentMeta = sortedMetas.remove(0);
		final var prominentPath = prominentMeta.path();
		final var deduplication = configuration.deduplication().resolve(prominentMeta.sha512());

		if (configuration.dryRun()) {
			logger.info("The prominent {} will be moved to {} and a symbol link created",
				prominentPath, deduplication);
		} else {
			if (Files.exists(prominentPath) && !Files.isSymbolicLink(prominentPath)
				&& !Files.exists(deduplication) && move(prominentPath, deduplication)) {
				if (!createSymbolicLink(prominentPath, deduplication)) {
					// rollback
					if (!move(prominentPath, deduplication)) {
						logger.error("Rollback failed!");
						System.exit(1);
					}
				}
			} else {
				logger.info("{} already moved to {}", prominentPath, deduplication);
			}
		}

		sortedMetas.forEach(meta -> {
			final var filePath = meta.path();
			final var trashName = filePath.toAbsolutePath()
				.toString()
				.replaceAll(File.separator, "_");
			final var trash = configuration.trash().resolve(trashName);

			if (!Files.exists(filePath) || Files.isSymbolicLink(filePath)) {
				logger.info("{} already pointed at {}", filePath, deduplication);

				return;
			}

			if (configuration.dryRun()) {
				logDryRun(filePath, trash, deduplication);

				return;
			}

			// soft-delete
			if (!move(filePath, trash)) {
				return;
			}

			if (configuration.replaceWithSymlink()) {
				if (createSymbolicLink(filePath, deduplication)) {
					if (!configuration.safeDelete()) {
						rm(trash);
					}
				} else {
					// rollback soft-delete
					move(trash, filePath);
				}
			}
		});

		logger.info("");
	}

	private void logDryRun(final Path path, final Path trash, final Path deduplication) {
		final var sb = new StringBuilder();

		if (configuration.safeDelete()) {
			sb.append(String.format("%s will be moved to %s", path, trash));
		} else {
			sb.append(String.format("%s will be permanently deleted", path));
		}
		if (configuration.replaceWithSymlink()) {
			sb.append(String.format(" and symlinked to %s", deduplication));
		}

		logger.info("{}", sb);
	}

	public static boolean move(final Path source, final Path target, final CopyOption... options) {
		try {
			Files.move(source, target, options);
			logger.info("Moved {} to {}", source, target);

			return true;
		} catch (final IOException e) {
			logger.error(String.format("Failed to move %s to %s", source, target), e);
		}

		return false;
	}

	public static void rm(final Path path) {
		try {
			Files.deleteIfExists(path);
			logger.info("Deleted {}", path);
		} catch (final IOException e) {
			logger.error(String.format("Failed to delete %s", path), e);
		}
	}

	public static boolean createSymbolicLink(final Path link, final Path target,
		final FileAttribute<?>... attributes) {
		try {
			Files.createSymbolicLink(link, target, attributes);
			logger.info("Created symbolic link {} to {}", link, target);

			return true;
		} catch (final IOException e) {
			logger.error(String.format("Failed to create symbolic link %s to %s", link, target), e);
		}

		return false;
	}


}
