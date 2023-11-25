package com.tagadvance.filededuplicator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class ConfigurationTest {

	private static final String CONFIG_FILE = "/config.yaml";

	@Test
	void testConfiguration() throws IOException {
		try (final var in = ConfigurationTest.class.getResourceAsStream(CONFIG_FILE)) {
			final var configuration = Configuration.parseInputStream(in);
			assertTrue(configuration.dryRun(), "dryRun is disabled");
		}
	}

}
