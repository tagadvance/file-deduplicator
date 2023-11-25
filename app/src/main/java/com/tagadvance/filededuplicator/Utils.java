package com.tagadvance.filededuplicator;

import java.nio.file.Path;
import java.text.StringCharacterIterator;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Utils {

	public static Optional<String> getExtension(final Path path) {
		final var fileName = path.getFileName().toString();

		return getExtension(fileName);
	}

	public static Optional<String> getExtension(final String fileName) {
		final var pattern = Pattern.compile("((\\.\\w{3,4})+)$");
		final var matcher = pattern.matcher(fileName);
		if (matcher.find()) {
			final var group = matcher.group();

			return Optional.of(group);
		}

		return Optional.empty();
	}

	/**
	 * @param bytes the number of bytes
	 * @return the number of bytes in human-readable format
	 * @see <a href="https://stackoverflow.com/a/3758880/625688">aioobe's answer</a>
	 */
	public static String humanReadableByteCountBin(long bytes) {
		final long absoluteBytes = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		if (absoluteBytes < 1024) {
			return bytes + " B";
		}

		long value = absoluteBytes;
		final var ci = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absoluteBytes > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}
		value *= Long.signum(bytes);

		return String.format("%.1f %ciB", value / 1024.0, ci.current());
	}

	private Utils() {
	}

}
