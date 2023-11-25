# File Deduplicator

File Deduplicator finds, and optionally removes, duplicate files in configurable root paths.

I have backups going back years. Many of these backups have files duplicated in other backups. I
wrote this app to find, and optionally remove, duplicate files. It indexes every file in each root
path and then looks for duplicate hashes. When performing dry run the potential space savings will
be printed to the log. The oldest copy of a file is never modified. Duplicates can be deleted or
symlinked to the oldest copy.
