package com.moulberry.axiom.packet;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.blueprint.BlueprintIo;
import com.moulberry.axiom.blueprint.RawBlueprint;
import com.moulberry.axiom.blueprint.ServerBlueprintManager;
import com.moulberry.axiom.blueprint.ServerBlueprintRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadBlueprintPacketListener  {

    private final AxiomPaper plugin;
    public UploadBlueprintPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    public void onReceive(ServerPlayer serverPlayer, FriendlyByteBuf friendlyByteBuf) {
        if (!this.plugin.canUseAxiom(serverPlayer.getBukkitEntity(), "axiom.blueprint.upload")) {
            friendlyByteBuf.writerIndex(friendlyByteBuf.readerIndex());
            return;
        }

        if (this.plugin.isMismatchedDataVersion(serverPlayer.getUUID())) {
            serverPlayer.sendSystemMessage(Component.literal("Axiom+ViaVersion: This feature isn't supported. Switch your client version to " + SharedConstants.VERSION_STRING + " to use this"));
            return;
        }

        ServerBlueprintRegistry registry = ServerBlueprintManager.getRegistry();
        if (registry == null || this.plugin.blueprintFolder == null) {
            return;
        }

        String pathStr = friendlyByteBuf.readUtf();
        RawBlueprint rawBlueprint = RawBlueprint.read(friendlyByteBuf);

        pathStr = pathStr.replace("\\", "/");

        if (!pathStr.endsWith(".bp") || pathStr.contains("..") || !pathStr.startsWith("/")) {
            return;
        }

        pathStr = pathStr.substring(1);

        Path relative = Path.of(pathStr).normalize();
        if (relative.isAbsolute()) {
            return;
        }

        String pathName = pathStr.substring(0, pathStr.length()-3);

        serverPlayer.getServer().execute(() -> {
            try {
                Path path = this.plugin.blueprintFolder.resolve(relative);

                // Write file
                try {
                    Files.createDirectories(path.getParent());
                } catch (IOException e) {
                    return;
                }
                try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(path))) {
                    BlueprintIo.writeRaw(outputStream, rawBlueprint);
                } catch (IOException e) {
                    return;
                }

                // Update registry
                registry.blueprints().put("/" + pathName, rawBlueprint);

                // Resend manifest
                ServerBlueprintManager.sendManifest(serverPlayer.getServer().getPlayerList().getPlayers());
            } catch (Throwable t) {
                serverPlayer.getBukkitEntity().kick(net.kyori.adventure.text.Component.text(
                        "An error occured while uploading blueprint: " + t.getMessage()));
            }
        });
    }

}
