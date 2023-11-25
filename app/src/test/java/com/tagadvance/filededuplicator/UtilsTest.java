package com.tagadvance.filededuplicator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Streams;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Utils}.
 */
class UtilsTest {

	@Test
	void testGetExtension() {
		final Stream<String> expectedExtensions = Stream.of(".bar", ".foo.bar", ".jpeg");
		final Stream<String> actualExtensions = Stream.of("foo.bar", "test.foo.bar", "foo.jpeg")
			.map(Utils::getExtension).filter(Optional::isPresent).map(Optional::get);
		Streams.zip(expectedExtensions, actualExtensions,
				(expected, actual) -> new Pair(expected, actual))
			.forEach(p -> assertEquals(p.a(), p.b()));
	}

	@Test
	void testHumanReadableByteCount() {
		final Stream<String> expectedValues = Stream.of("1.0 KiB", "1.0 MiB", "4.2 GiB");
		final Stream<String> actualValues = Stream.of(1024L, (long) Math.pow(1024L, 2),
			(long) (Math.pow(1024L, 3) * 4.2)).map(Utils::humanReadableByteCountBin);
		Streams.zip(expectedValues, actualValues, (expected, actual) -> new Pair(expected, actual))
			.forEach(p -> assertEquals(p.a(), p.b()));
	}

	record Pair<A, B>(A a, B b) {

	}

}
