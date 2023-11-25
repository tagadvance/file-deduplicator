package com.tagadvance.filededuplicator;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Hash {

	public static final String ALGORITHM_MD5 = "MD5";
	public static final String ALGORITHM_SHA1 = "SHA1";
	public static final String ALGORITHM_SHA512 = "SHA512";

	public static Map<String, String> calculateHash(final Path path, final String... algorithms)
		throws IOException {
		final var digestByAlgorithm = Stream.of(algorithms)
			.collect(Collectors.toMap(Function.identity(), Hash::toMessageDigest));

		try (final var in = Files.newInputStream(path)) {
			int read;
			final var bytes = new byte[1024 * 1024];
			while ((read = in.read(bytes)) != -1) {
				for (final var digest : digestByAlgorithm.values()) {
					digest.update(bytes, 0, read);
				}
			}
		}

		return digestByAlgorithm.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> {
			final var digest = e.getValue().digest();
			final var i = new BigInteger(1, digest);

			return String.format("%032x", i);
		}));
	}

	private static MessageDigest toMessageDigest(final String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private Hash() {
	}

}
