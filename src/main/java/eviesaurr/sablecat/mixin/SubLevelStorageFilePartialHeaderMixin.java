package eviesaurr.sablecat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import eviesaurr.sablecat.fix.FixRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

@Mixin(value = SubLevelStorageFile.class, remap = false)
public abstract class SubLevelStorageFilePartialHeaderMixin {

    @Shadow
    @Final
    private ByteBuffer header;

    @Shadow
    @Final
    private FileChannel file;

    @WrapOperation(
            method = "write(ILjava/nio/ByteBuffer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/region/SubLevelStorageFile;writeHeader()V"
            )
    )
    private void sablecat$writePartialHeaderOnly(SubLevelStorageFile instance, Operation<Void> original,
                                                 @Local(argsOnly = true, ordinal = 0) int index) throws IOException {
        if (!FixRegistry.isEnabled("partial-header-write")) {
            original.call(instance);
            return;
        }

        int offset = index * Integer.BYTES;
        int value = this.header.getInt(offset);

        ByteBuffer slot = ByteBuffer.allocate(Integer.BYTES);
        slot.putInt(value);
        slot.flip();

        long writeOffset = offset;
        while (slot.hasRemaining()) {
            writeOffset += this.file.write(slot, writeOffset);
        }
    }
}