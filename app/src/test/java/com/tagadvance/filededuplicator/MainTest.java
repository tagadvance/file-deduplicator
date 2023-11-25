package com.tagadvance.filededuplicator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Main}.
 */
class MainTest {

	private static final String CONFIG_FILE = "/config.yaml";

	@Test
	void testConfiguration() {
		try (final var defaultModule = new DefaultModule(MainTest::getTestConfiguration)) {
			final var injector = Guice.createInjector(defaultModule);
			final var configuration = injector.getInstance(Configuration.class);
			assertTrue(configuration.dryRun(), "dryRun is disabled");
		}
	}

	private static Configuration getTestConfiguration() {
		try (final var in = MainTest.class.getResourceAsStream(CONFIG_FILE)) {
			return Configuration.parseInputStream(in);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

}
