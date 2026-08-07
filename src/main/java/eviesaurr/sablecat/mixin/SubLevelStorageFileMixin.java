package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.nio.ByteBuffer;

@Mixin(SubLevelStorageFile.class)
public abstract class SubLevelStorageFileMixin {

    @Shadow(remap = false)
    private ByteBuffer header;

    @Shadow(remap = false)
    private java.nio.channels.FileChannel file;

    @Redirect(
        method = "write(ILjava/nio/ByteBuffer;)V",
        at = @At(value = "INVOKE", target = "Ljava/nio/channels/FileChannel;write(Ljava/nio/ByteBuffer;J)I", remap = false),
        remap = false
    )
    private int sablecat$forceFlushAfterDataWrite(java.nio.channels.FileChannel channel, ByteBuffer src, long position) throws IOException {
        int written = channel.write(src, position);
        if (FixRegistry.isEnabled("write-flush")) {
            channel.force(false);
        }
        return written;
    }

    @Redirect(
        method = "write(ILjava/nio/ByteBuffer;)V",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/region/SubLevelStorageFile;writeHeader()V", remap = false),
        remap = false
    )
    private void sablecat$forceFlushAfterHeader(SubLevelStorageFile self) throws IOException {
        this.header.position(0);
        this.file.write(this.header, 0L);
        if (FixRegistry.isEnabled("write-flush")) {
            this.file.force(false);
        }
    }
}
