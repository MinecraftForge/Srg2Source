#!/bin/sh -x

# Root directory of data files (for example from https://github.com/agaricusb/MinecraftRemapping)
DATA=../../jars

# Minecraft version
MCVER=1.4.7

# Root of checked out CraftBukkit repository
CB_ROOT=../../CraftBukkit

# MCP decompiled with FML repackaging, but not joined. See https://gist.github.com/4366333
MCP_ROOT=../../mcp726-pkgd

# Python 2.7+ installation 
PYTHON=/usr/bin/python2.7

# Location for IntelliJ IDEA
IDEA=/Applications/IntelliJ\ IDEA\ 12.app/Contents/MacOS/idea 

# Where to store CB/MCP patch output
DIFF_OUT=/tmp/diff

# Pre-generated MCP rangemap for local variables
# Currently you need to generate this manually by following the instructions on https://github.com/agaricusb/CraftBukkit/wiki/How-to-decompile-Minecraft-using-MCP-with-FML-repackaged-class-names,-without-FML's-other-changes
# then importing the sources into IDEA, and extracting the range map with srg2source.
# TODO: automate extracting rangemap for MCP? probably not necessary to automate, doesn't change much
MCP_RANGEMAP=$MCP_ROOT/`basename $MCP_ROOT`.rangemap

# CB to MCP mapping
SRG_CB2MCP=$DATA/$MCVER/cb2pkgmcp.srg
SRG_CB2MCP_FIXES=$DATA/1.4.6/uncollide-cb2pkgmcp.srg


# Abort on any command failure
set -e


# Small fixes to accomodate renaming compatibility
patch -p1 -d $CB_ROOT < $DATA/$MCVER/prerenamefixes.patch

# Change minecraft-server library to a "slimmed" version without the same NMS classes CB patches
# This avoids Psi symbol resolving errors
patch -p1 -d $CB_ROOT < $DATA/pom-slim-minecraft-server.patch

# Preflight IDEA with the updated pom, giving it a time to scan the symbols
rm -f $CB_ROOT/srg2source-batchmode
/Applications/IntelliJ\ IDEA\ 12.app/Contents/MacOS/idea `pwd`/$CB_ROOT &
echo Ensure the pom is updated, then press enter to continue
read
set +e
killall -0 idea
while [ $? -eq 0 ]
do
    echo "IDEA is still running! Wait for it to finish, then close and press enter to continue"
    read
    killall -0 idea
done
set -e

#sleep 120
#killall idea
#sleep 2
#killall -9 idea || true
#killall -9 idea || true

# Extract map of symbol ranges in CB source, required for renaming
# IDEA must have Srg2source plugin installed, it will detect batchmode and automatically run
CB_RANGEMAP=$CB_ROOT/craftbukkit.rangemap
mv $CB_RANGEMAP $CB_RANGEMAP.old || true
touch $CB_ROOT/srg2source-batchmode
/Applications/IntelliJ\ IDEA\ 12.app/Contents/MacOS/idea `pwd`/$CB_ROOT
rm $CB_ROOT/srg2source-batchmode
if [ ! -e $CB_RANGEMAP ]
then
    echo Failed to extract CB rangemap
    exit -1
fi
diff -ur $CB_RANGEMAP.old $CB_RANGEMAP|wc -l

# Change to a new minecraft-server library with MCP names
patch -p1 -d $CB_ROOT -R < $DATA/pom-slim-minecraft-server.patch
patch -p1 -d $CB_ROOT < $DATA/pom-minecraft-server-pkgmcp.patch

# Apply the renames
$PYTHON rangeapply.py --srcRoot $CB_ROOT --srcRangeMap $CB_RANGEMAP --lvRangeMap $MCP_RANGEMAP --mcpConfDir $MCP_ROOT/conf --srgFiles $SRG_CB2MCP $SRG_CB2MCP_FIXES

# TODO: apply javadoc

# Reformat source style in NMS (only; not OBC) to more closely resemble MCP
# This assumes you have astyle installed and ~/.astylerc copied from conf/astyle.cfg
#ARTISTIC_STYLE_OPTIONS=../mcp726-pkgd/conf/astyle.cfg astyle --suffix=none -R $CB_ROOT/src/main/java/net/minecraft # TODO
find $CB_ROOT/src/main/java/net/minecraft -name '*.java' -exec astyle --suffix=none {} \;

$PYTHON javadocxfer.py $CB_ROOT $MCP_ROOT

$PYTHON whitespaceneutralizer.py $CB_ROOT $MCP_ROOT

# Measure differences to get a sense of progress
(diff -ur $MCP_ROOT/src/minecraft_server/net/minecraft/ $CB_ROOT/src/main/java/net/minecraft/ > $DIFF_OUT) || true

wc -l $DIFF_OUT


