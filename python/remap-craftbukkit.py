#!/usr/bin/python

import os
import shutil

# Root directory of data files (for example from https://github.com/agaricusb/MinecraftRemapping)
DATA="../../jars"

# Minecraft version
MCVER="1.4.7"

# Root of checked out CraftBukkit repository
CB_ROOT="../../CraftBukkit"

# MCP decompiled with FML repackaging, but not joined. See https://gist.github.com/4366333
# TODO: update for new FML flags
#MCP_ROOT="../../mcp726-pkgd"
MCP_ROOT="../../FML/fml/mcp"

# Python 2.7+ installation 
PYTHON="/usr/bin/python2.7"

# Location for IntelliJ IDEA
#IDEA="/Applications/IntelliJ\ IDEA\ 12.app/Contents/MacOS/idea"
IDEA="sh /tmp/ij/bin/idea.sh"

# Where to store CB/MCP patch output
DIFF_OUT="/tmp/diff"

# Pre-generated MCP rangemap for local variables
# Currently you need to generate this manually by following the instructions on https://github.com/agaricusb/CraftBukkit/wiki/How-to-decompile-Minecraft-using-MCP-with-FML-repackaged-class-names,-without-FML's-other-changes
# then importing the sources into IDEA, and extracting the range map with srg2source.
# TODO: automate extracting rangemap for MCP? probably not necessary to automate, doesn't change much
MCP_RANGEMAP=os.path.join(MCP_ROOT, os.path.basename(MCP_ROOT) + ".rangemap")

# CB to MCP mapping
SRG_CB2MCP=os.path.join(DATA, MCVER, "cb2pkgmcp.srg")
SRG_CB2MCP_FIXES=os.path.join(DATA, "1.4.6", "uncollide-cb2pkgmcp.srg")


# Small fixes to accomodate renaming compatibility
status = os.system("patch -p1 -d "+CB_ROOT+" < "+os.path.join(DATA, MCVER, "prerenamefixes.patch"))
assert status == 0, "failed to patch prerenamefixes"

# Change minecraft-server library to a "slimmed" version without the same NMS classes CB patches
# This avoids Psi symbol resolving errors
status = os.system("patch -p1 -d "+CB_ROOT+" < "+os.path.join(DATA, "pom-slim-minecraft-server.patch"))
assert status == 0, "failed to patch pom-slim"

# Preflight IDEA with the updated pom, giving it a time to scan the symbols
cookie = os.path.join(CB_ROOT, "srg2source-batchmode")
if os.path.exists(cookie):
    os.unlink(cookie)

# Extract map of symbol ranges in CB source, required for renaming
# IDEA must have Srg2source plugin installed, it will detect batchmode and automatically run
CB_RANGEMAP=os.path.join(CB_ROOT, "craftbukkit.rangemap")

if os.path.exists(CB_RANGEMAP):
    shutil.move(CB_RANGEMAP, CB_RANGEMAP+".old")


file(cookie, "w")

status = os.system(IDEA+" "+os.path.join(os.getcwd(), CB_ROOT))
assert status == 0

os.unlink(os.path.join(CB_ROOT, "srg2source-batchmode"))

if not os.path.exists(CB_RANGEMAP):
    raise Exception("Failed to extract CB rangemap")

os.system("diff -ur "+CB_RANGEMAP+".old "+CB_RANGEMAP+"|wc -l")

# Change to a new minecraft-server library with MCP names
status = os.system("patch -p1 -d "+CB_ROOT+" -R < "+os.path.join(DATA, "pom-slim-minecraft-server.patch"))
assert status ==  0

status = os.system("patch -p1 -d "+CB_ROOT+" < "+os.path.join(DATA, "pom-minecraft-server-pkgmcp.patch"))
assert status == 0

# Apply the renames
status = os.system(PYTHON+" rangeapply.py --srcRoot "+CB_ROOT+" --srcRangeMap "+CB_RANGEMAP+" --lvRangeMap "+MCP_RANGEMAP+" --mcpConfDir "+os.path.join(MCP_ROOT, "conf")+" --srgFiles "+SRG_CB2MCP+" "+SRG_CB2MCP_FIXES)
assert status == 0

# TODO
# Reformat source style in NMS (only; not OBC) to more closely resemble MCP
# This assumes you have astyle installed and ~/.astylerc copied from conf/astyle.cfg
#ARTISTIC_STYLE_OPTIONS=../mcp726-pkgd/conf/astyle.cfg astyle --suffix=none -R $CB_ROOT/src/main/java/net/minecraft # TODO
#find $CB_ROOT/src/main/java/net/minecraft -name '*.java' -exec astyle --suffix=none {} \;

#$PYTHON javadocxfer.py $CB_ROOT $MCP_ROOT

#$PYTHON whitespaceneutralizer.py $CB_ROOT $MCP_ROOT

# Measure differences to get a sense of progress
#(diff -ur $MCP_ROOT/src/minecraft_server/net/minecraft/ $CB_ROOT/src/main/java/net/minecraft/ > $DIFF_OUT) || true

#wc -l $DIFF_OUT


