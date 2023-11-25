package com.tagadvance.filededuplicator;

import static java.util.Objects.requireNonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CsvDao implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(CsvDao.class);
	private static final CSVFormat csvFormat = CSVFormat.DEFAULT.builder().build();

	private final ReadWriteLock ioLock = new ReentrantReadWriteLock();
	private final File file;
	private final Writer writer;

	public CsvDao(final File file) throws IOException {
		this.file = requireNonNull(file, "file must not be null");

		final var append = true;
		this.writer = new BufferedWriter(new FileWriter(file, append));
	}

	public List<PathMeta> selectAll() {
		ioLock.writeLock().lock();
		try {
			writer.flush();

			ioLock.readLock().lock();
			try (final var in = new FileReader(file)) {
				final var records = csvFormat.parse(in);
				final var iterator = records.iterator();
				final var spliterator = Spliterators.spliteratorUnknownSize(iterator,
					Spliterator.ORDERED);
				final var parallel = false;
				final var stream = StreamSupport.stream(spliterator, parallel);

				return stream.map(CsvDao::fromCsvRecord).collect(Collectors.toList());
			} finally {
				ioLock.readLock().unlock();
			}
		} catch (final IOException e) {
			logger.error("{} could not be read", file);
		} finally {
			ioLock.writeLock().unlock();
		}

		return Collections.emptyList();
	}

	private static PathMeta fromCsvRecord(final CSVRecord record) {
		final var pathValue = record.get(0);
		final var path = Paths.get(pathValue);
		final var fileSizeValue = record.get(1);
		final var fileSize = Long.parseLong(fileSizeValue);
		final var lastModifiedValue = record.get(2);
		final var lastModified = Long.parseLong(lastModifiedValue);
		final var md5 = record.get(3);
		final var sha512 = record.get(4);

		return new PathMeta(path, fileSize, lastModified, md5, sha512);
	}

	public void insert(final PathMeta pathMeta) {
		final var path = toString(pathMeta.path());
		final var size = pathMeta.size();
		final var lastModified = pathMeta.lastModified();
		final var md5 = pathMeta.md5();
		final var sha512 = pathMeta.sha512();
		final var record = csvFormat.format(path, size, lastModified, md5, sha512);

		ioLock.writeLock().lock();
		try {
			writer.write(record);
			writer.write('\n');
		} catch (final IOException e) {
			logger.error("Insert failed!", e);
		} finally {
			ioLock.writeLock().unlock();
		}
	}

	public void flush() {
		ioLock.writeLock().lock();
		try {
			writer.flush();
		} catch (final IOException e) {
			logger.error("Flush failed!", e);
		} finally {
			ioLock.writeLock().unlock();
		}
	}

	@Override
	public void close() {
		ioLock.writeLock().lock();
		try {
			writer.close();
		} catch (final IOException e) {
			logger.error("Close failed!", e);
		} finally {
			ioLock.writeLock().unlock();
		}
	}

	private static String toString(final Path path) {
		return path.toAbsolutePath().toString();
	}

}
