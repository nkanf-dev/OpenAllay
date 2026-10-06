package dev.openallay.neoforge.command;

import dev.openallay.neoforge.GuideCommandSource;
import dev.openallay.guide.GuideNotice;
import dev.openallay.platform.minecraft.MinecraftComponents;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.World;

/** Actual MCP source interface. Never constructs a server CommandSource on a client. */
public final class ForgeClientCommandSource implements ISuggestionProvider, GuideCommandSource {
    private final ClientSuggestionProvider delegate;
    private final ClientPlayNetHandler connection;
    private final UUID actor;
    public ForgeClientCommandSource(ClientPlayNetHandler connection) {
        this.connection = java.util.Objects.requireNonNull(connection, "connection");
        this.delegate = connection.getSuggestionsProvider();
        this.actor = java.util.Objects.requireNonNull(Minecraft.getInstance().player).getUUID();
    }
    @Override public UUID actor() { return actor; }
    @Override public void publish(GuideNotice notice) {
        feedback("[OpenAllay] " + notice.message(), notice.level() == GuideNotice.Level.ERROR);
    }
    public void feedback(String message) { feedback(message, true); }
    private void feedback(String message, boolean error) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.getConnection() != connection || client.player == null || !client.player.getUUID().equals(actor)) return;
            var text = MinecraftComponents.literal(message);
            if (error) text.withStyle(TextFormatting.RED);
            client.gui.getChat().addMessage(text);
        });
    }
    @Override public Collection<String> getOnlinePlayerNames() { return delegate.getOnlinePlayerNames(); }
    @Override public Collection<String> getSelectedEntities() { return delegate.getSelectedEntities(); }
    @Override public Collection<String> getAllTeams() { return delegate.getAllTeams(); }
    @Override public Collection<ResourceLocation> getAvailableSoundEvents() { return delegate.getAvailableSoundEvents(); }
    @Override public Stream<ResourceLocation> getRecipeNames() { return delegate.getRecipeNames(); }
    @Override public CompletableFuture<Suggestions> customSuggestion(CommandContext<ISuggestionProvider> context, SuggestionsBuilder builder) {
        return delegate.customSuggestion(context, builder);
    }
    @Override public Collection<ISuggestionProvider.Coordinates> getRelevantCoordinates() { return delegate.getRelevantCoordinates(); }
    @Override public Collection<ISuggestionProvider.Coordinates> getAbsoluteCoordinates() { return delegate.getAbsoluteCoordinates(); }
    @Override public Set<RegistryKey<World>> levels() { return delegate.levels(); }
    @Override public DynamicRegistries registryAccess() { return delegate.registryAccess(); }
    @Override public boolean hasPermission(int level) { return delegate.hasPermission(level); }
}
