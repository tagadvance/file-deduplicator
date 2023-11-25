package com.tagadvance.filededuplicator;

import static java.util.Objects.requireNonNull;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class DefaultModule extends AbstractModule implements AutoCloseable {

	private final Supplier<Configuration> configurationSupplier;
	private final List<Runnable> closers = new ArrayList<>();

	public DefaultModule(final Supplier<Configuration> configurationSupplier) {
		this.configurationSupplier = requireNonNull(configurationSupplier);
	}

	@Override
	public void close() {
		closers.forEach(Runnable::run);
	}

	@Provides
	@Singleton
	Configuration providesConfiguration() {
		return configurationSupplier.get();
	}

	@Provides
	@Singleton
	CsvDao providesCsvDao() throws IOException {
		final var file = new File("file-deduplicator.csv");
		final var csvDao = new CsvDao(file);
		closers.add(csvDao::close);

		return csvDao;
	}

	@Provides
	@Singleton
	FileDeduplicator providesFileScrubber(final Configuration configuration, final CsvDao csvDao) {
		return new FileDeduplicator(configuration, csvDao);
	}

}
