#!/usr/bin/python

# Reconcile whitespace differences between two codebases, preferring one over the other
# in order to reduce unnecessary diff lines

import sys, os, pprint, re
import srglib

rewriteFiles = True

# Get dict of all lines without whitespace, mapped to line number and original line text
def getLinesMap(filename):
    seenLines = {}
    for i, originalLine in enumerate(file(filename).readlines()):
        key = removeNonPrefixWhitespace(originalLine)
        if not seenLines.has_key(key):
            seenLines[key] = []
        seenLines[key].append((i, originalLine))

    return seenLines

# Remove all whitespace except at the very beginning
def removeNonPrefixWhitespace(s):
    prefix, rest = re.match(r"(\s*)(.*)", s).groups()
    return prefix + srglib.killWhitespace(rest)

def neutralizeWhitespace(cbFilenamePath, mcpFilenamePath, className):
    cbLinesMap = getLinesMap(cbFilenamePath)
    mcpLinesMap = getLinesMap(mcpFilenamePath)

    lineChanges = {}

    # Find whitespace-only differences in unique lines
    for key in cbLinesMap:
        for cbLineNo, cbOriginalLine in cbLinesMap[key]:
            if not mcpLinesMap.has_key(key): continue

            for mcpLineNo, mcpOriginalLine in mcpLinesMap[key]:  # TODO: nested loops really necessary?
                if cbOriginalLine != mcpOriginalLine:
                    # Lines are identical except for whitespace
                    # For example in (casts), CB often has a byte after the closing paren, while MCP
                    # doesn't.. and there's a space after array initializers { ...}. CB prefers more 
                    # space. Unfortunately, astyle doesn't fix this. CB seems to be using the jacobe
                    # tool for reformatting (see Bukkit-MinecraftServer GitHub project), not astyle.
                    #print "CB:",cbOriginalLine
                    #print "MC:",mcpOriginalLine

                    # MCP is right
                    lineChanges[cbLineNo] = mcpOriginalLine

    # Change those lines
    newLines = []
    count = 0
    for i, line in enumerate(file(cbFilenamePath).readlines()):
        if lineChanges.has_key(i):
            newLines.append(lineChanges[i])
            count += 1
        else:
            newLines.append(line)

    # Special case: remove spurious blank line after opening brace of class declaration statement
    i = 0
    while i < len(newLines):
        if isBlank(newLines[i]) and i >= 2:
            if newLines[i - 1].startswith("{") and "class" in newLines[i - 2]:
                del newLines[i]
                count += 1
        i += 1

    if rewriteFiles:
        file(cbFilenamePath, "w").write("".join(newLines))

    if count != 0:
        print "Neutralized %s whitespace line differences in %s" % (count, className)

def isBlank(line):
    return len(srglib.killWhitespace(line)) == 0

def main():
    if len(sys.argv) != 3:
        print "usage: %s cbRoot mcpRoot" % (sys.argv[0],)
        raise SystemExit
    
    cbRoot = sys.argv[1]#"../CraftBukkit"
    mcpRoot = sys.argv[2]#"../mcp725-pkgd"

    cbSrc = os.path.join(cbRoot, "src/main/java/net/minecraft/")
    mcpSrc = os.path.join(mcpRoot, "src/minecraft_server/net/minecraft/")

    for cbFilenamePath in srglib.getJavaSourceFiles(cbSrc):
        # Get corresponding MCP filename path
        commonPath = cbFilenamePath.replace(os.path.commonprefix((cbFilenamePath, cbSrc)), "") 
        className = os.path.splitext(commonPath)[0]  # class name including package path
        mcpFilenamePath = os.path.join(mcpSrc, commonPath)

        assert os.path.exists(cbFilenamePath), "CB source %s not found?" % (cbFilenamePath,)
        if srglib.isPollution(mcpFilenamePath): continue
        assert os.path.exists(mcpFilenamePath), "CB source %s has no corresponding MCP file at %s" % (cbFilenamePath, mcpFilenamePath)


        neutralizeWhitespace(cbFilenamePath, mcpFilenamePath, className)

if __name__ == "__main__":
    main()

