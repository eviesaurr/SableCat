package eviesaurr.sablecat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class SableCatMixinConfigPlugin implements IMixinConfigPlugin {

    private static int sableVersionState = 0;
    private static boolean versionChecked = false;

    @Override
    public void onLoad(String mixinPackage) {

    }

    private static int getSableVersionState() {
        if (versionChecked) return sableVersionState;
        versionChecked = true;
        try {
            Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Method getLoadingModList = fmlLoaderClass.getMethod("getLoadingModList");
            Object modList = getLoadingModList.invoke(null);

            Method getMods = modList.getClass().getMethod("getMods");
            @SuppressWarnings("unchecked")
            List<Object> modInfos = (List<Object>) getMods.invoke(modList);

            for (Object info : modInfos) {
                Method getModId = info.getClass().getMethod("getModId");
                String modId = (String) getModId.invoke(info);
                if ("sable".equals(modId)) {
                    Method getVersion = info.getClass().getMethod("getVersion");
                    Object version = getVersion.invoke(info);

                    Class<?> defaultArtifactVersionClass = Class.forName("org.apache.maven.artifact.versioning.DefaultArtifactVersion");
                    Object threshold = defaultArtifactVersionClass.getConstructor(String.class).newInstance("2.0.0");
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    int result = ((Comparable) version).compareTo(threshold);
                    boolean v2 = result >= 0;
                    sableVersionState = v2 ? 2 : 1;
                    SableCat.LOGGER.info("Detected Sable version {}, enabling {} constraint self-fix mixin",
                        version, v2 ? "V2 (PhysicsPipelineBody)" : "V1 (ServerSubLevel)");
                    return sableVersionState;
                }
            }
        } catch (Throwable t) {
            SableCat.LOGGER.warn("FML API version detection failed, falling back to class signature detection", t);
        }

        sableVersionState = detectByClassSignature();
        return sableVersionState;
    }

    private static int detectByClassSignature() {
        String className = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline";
        String classResource = className.replace('.', '/') + ".class";
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) loader = SableCatMixinConfigPlugin.class.getClassLoader();
            try (InputStream in = loader.getResourceAsStream(classResource)) {
                if (in == null) {
                    SableCat.LOGGER.warn("Sable class resource not found: {}, disabling both constraint self-fix mixins", classResource);
                    sableVersionState = 0;
                    return sableVersionState;
                }
                byte[] bytes = in.readAllBytes();
                ClassReader reader = new ClassReader(bytes);
                final int[] detected = {0};
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if (detected[0] != 0) return null;
                        if (!"addConstraint".equals(name) || descriptor == null || descriptor.isEmpty()) return null;
                        if (descriptor.length() < 3 || descriptor.charAt(1) != 'L') return null;
                        int start = 2;
                        int end = descriptor.indexOf(';', start);
                        if (end < 0) return null;
                        String firstParam = descriptor.substring(start, end);
                        if (firstParam.contains("PhysicsPipelineBody")) {
                            detected[0] = 2;
                        } else {
                            detected[0] = 1;
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                sableVersionState = detected[0];
                if (sableVersionState == 2) {
                    SableCat.LOGGER.info("Detected Sable >=2.0.0 by class signature (addConstraint uses PhysicsPipelineBody), enabling V2 mixin");
                } else if (sableVersionState == 1) {
                    SableCat.LOGGER.info("Detected Sable <2.0.0 by class signature (addConstraint first param is not PhysicsPipelineBody), enabling V1 mixin");
                } else {
                    SableCat.LOGGER.warn("addConstraint method not found in {}, disabling both constraint self-fix mixins", className);
                }
                return sableVersionState;
            }
        } catch (Throwable t) {
            SableCat.LOGGER.error("Class signature detection also failed, disabling both constraint self-fix mixins to avoid crash", t);
        }
        sableVersionState = 0;
        return sableVersionState;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("RapierConstraintSelfFixMixinV1")) {
            int state = getSableVersionState();
            if (state == 0) return false;
            return state == 1;
        }
        if (mixinClassName.endsWith("RapierConstraintSelfFixMixinV2")) {
            int state = getSableVersionState();
            if (state == 0) return false;
            return state == 2;
        }
        if (mixinClassName.endsWith("ScalableLuxCompatMixin")) {
            return isModLoaded("scalablelux");
        }
        return true;
    }

    private static boolean isModLoaded(String modId) {
        try {
            Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Method getLoadingModList = fmlLoaderClass.getMethod("getLoadingModList");
            Object modList = getLoadingModList.invoke(null);
            Method getModFileById = modList.getClass().getMethod("getModFileById", String.class);
            Object modFile = getModFileById.invoke(modList, modId);
            return modFile != null;
        } catch (Throwable t) {
            SableCat.LOGGER.debug("Failed to detect mod '{}' during mixin prepare, skipping related mixin", modId, t);
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
