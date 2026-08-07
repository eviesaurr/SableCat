package eviesaurr.sablecat.fix;

import java.util.concurrent.CompletableFuture;

public record PendingSave(String key, String description, CompletableFuture<Void> future) {}