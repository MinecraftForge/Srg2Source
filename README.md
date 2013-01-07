Srg2Source (formerly known as ApplySrg2Source)

A tool for renaming symbols (classes, methods, fields, parameters, and variables) in Java source code using .srg mappings.

For porting, Minecraft, CraftBukkit, mods, plugins, etc.

Latest release: https://bitbucket.org/agaricusb/srg2source/downloads/Srg2Source-2.0.jar

## Basic Usage

1. Install IntelliJ IDEA from http://www.jetbrains.com/idea/ , and install the plugin: Preferences > Plugins > Install plugin from disk... > Srg2Source.jar

2. Open your project (if you aren't already using IDEA, go to File > Import Project, make sure you can build the project before continuing)

3. Srg > Apply .srg with IntelliJ rename refactoring (new menu item), choose a .srg file, and your project will be transformed

This uses IDEA's built-in rename refactoring, and is equivalent to right-clicking on each symbol then selecting Refactor > Rename. This means
for each symbol, all usages are replaced, and the Psi tree is rebuilt -- a slow process for large projects. Consequentially, the order of the
.srg matters, and transitive conflicts can occur (renaming A -> B then B -> C causes A -> C; or A -> B then B -> A undoes the first rename;
it is not atomic). Symbols are renamed in this order: fields, methods, method parameters, and classes.

## Advanced Usage

New in v2.0: an experimental new but much faster and atomic two-part renaming technique. First extract a "range map" describing all of
the textual locations of each symbol:

Srg > Extract symbol range map for all files

or: Srg > Extract symbol range map for selected files

Then use the extracted .rangemap file with the "rangeapply" script from https://github.com/agaricusb/MinecraftRemapping:

python rangeapply.py  --srcRoot yourproject/ --srcRangeMap yourproject/yourproject.rangemap --srgFiles pkgmcp2cb.srg


### Batch Mode

1. Create a file named "srg2source-batchmode" in your project root directory

2. Run IDEA (with Srg2Source installed) with the project directory as a command-line argument

3. Srg2Source will automatically extract a range map for all files, then exit

## See also

Inspired by the Frans-Willem's binary remapper ApplySrg, originally from https://github.com/Frans-Willem/SrgTools (updated version at https://github.com/agaricusb/SrgTools)

More remapping tools and generated .srgs: https://github.com/agaricusb/MinecraftRemapping
