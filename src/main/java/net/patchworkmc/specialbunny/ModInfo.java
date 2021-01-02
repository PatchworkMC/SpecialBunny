package net.patchworkmc.specialbunny;

import java.util.Optional;

public class ModInfo {
    public boolean isFabric = false;
    public ForgeType isForge = ForgeType.NO;
    public boolean isMCreator = false;
    public boolean hasMixins = false;

    // TODO: list of coremods?
    public boolean hasForgeCoremods = false;

    // TODO: parse?
    public Optional<String> accessTransformers = Optional.empty();

    // TODO: check for inner jars
    // public List<ModInfo> containedMods = new ArrayList();

    public enum ForgeType {
        NO,
        LEGACY,
        YES
    }
}
