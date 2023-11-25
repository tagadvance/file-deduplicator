package com.tagadvance.filededuplicator;

import java.nio.file.Path;

public record PathMeta(Path path, long size, long lastModified, String md5, String sha512) {

}
