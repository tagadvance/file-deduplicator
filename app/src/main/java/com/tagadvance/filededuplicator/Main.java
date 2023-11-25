package com.tagadvance.filededuplicator;

import com.google.inject.Guice;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

public final class Main {

	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	private static final String CONFIG_FILE = "config.yaml";

	public static void main(final String[] args) {
		final Supplier<Configuration> configurationSupplier = () -> {
			try {
				return getConfiguration(args.length < 2 ? CONFIG_FILE : args[1]);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		};

		try (final var defaultModule = new DefaultModule(configurationSupplier)) {
			var interrupt = new Signal("INT");
			Signal.handle(interrupt, signal -> {
				logger.info("Interrupt detected! Shutting down gracefully.");
				defaultModule.close();
				System.exit(0);
			});

			final var injector = Guice.createInjector(defaultModule);
			final var scrubber = injector.getInstance(FileDeduplicator.class);
			scrubber.run();
		}
	}

	private static Configuration getConfiguration(final String path) throws IOException {
		final var file = new File(path);

		return Configuration.parseFile(file);
	}

	private Main() {
	}

}
