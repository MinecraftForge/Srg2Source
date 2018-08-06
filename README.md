Srg2Source v4.1

A tool for renaming symbols (classes, methods, fields, parameters, and variables) in Java source code using .srg mappings.

For porting, Minecraft, CraftBukkit, mods, plugins, etc.

## Usage

    java -jar Srg2Source-fatjar.jar --extract [SourceDir] [LibrariesDir] [RangeMapOutput]
    java -jar Srg2Source-fatjar.jar --apply --srcRoot [SourceDir] --srcRangeMap [RangeMap] --srgFiles [SRGFile] --excFiles [ExcFile] --outDir [Output]

## See also

Inspired by the Frans-Willem's binary remapper ApplySrg, originally from https://github.com/Frans-Willem/SrgTools (updated version at https://github.com/agaricusb/SrgTools)

More remapping tools and generated .srgs: https://github.com/agaricusb/MinecraftRemapping
