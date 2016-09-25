Srg2Source

A tool for renaming symbols (classes, methods, fields, parameters, and variables) in Java source code using .srg mappings.

For porting, Minecraft, CraftBukkit, mods, plugins, etc.

## Usage

	java -cp build/libs/Srg2Source-4.0-SNAPSHOT-all.jar [Class of what you want to use]
	java -cp build/libs/Srg2Source-4.0-SNAPSHOT-all.jar net.minecraftforge.srg2source.ast.RangeExtractor [SourceDir] [LibDir] [OutFile]
	java -cp build/libs/Srg2Source-4.0-SNAPSHOT-all.jar net.minecraftforge.srg2source.rangeapplier.RangeApplier --help

## See also

Inspired by the Frans-Willem's binary remapper ApplySrg, originally from https://github.com/Frans-Willem/SrgTools (updated version at https://github.com/agaricusb/SrgTools)

More remapping tools and generated .srgs: https://github.com/agaricusb/MinecraftRemapping
