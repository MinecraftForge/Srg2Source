#!/usr/bin/python

# Add javadoc from MCP source to CB-renamed source

import os, pprint, sys
import srglib

rewriteFiles = True

"""Read javadoc into a dict keyed by line after the javadoc (without whitespace), to array of raw javadoc lines"""
def readJavadoc(mcpFilenamePath):
    readingJavadoc = False
    readingIdentifyingLine = False
    javadocLines = []
    javadoc = {}
    expectBlankLineBefore = {}
    lineBeforeJavadoc = None
    for mcpLine in file(mcpFilenamePath).readlines():
        if readingIdentifyingLine:
            # The line after the javadoc is what it refers to
            identifier = srglib.killWhitespace(mcpLine)
            assert len(identifier) != 0, "Nothing associated with javadoc '%s' in %s" % (javadocLines, mcpFilenamePath)
            assert not javadoc.has_key(identifier), "Duplicate javadoc for '%s' in %s: %s" % (identifier, mcpFilenamePath, javadocLines)
            javadoc[identifier] = javadocLines
            expectBlankLineBefore[identifier] = isBlank(lineBeforeJavadoc)
            javadocLines = []
            readingIdentifyingLine = False
        if "/**" in mcpLine:
            readingJavadoc = True
        if readingJavadoc:
            # javadoc is enclosed in /** ... */
            javadocLines.append(mcpLine)
            if "*/" in mcpLine:
                readingJavadoc = False
                readingIdentifyingLine = True 
        if not readingJavadoc and not readingIdentifyingLine:
            lineBeforeJavadoc = mcpLine

    assert not readingJavadoc, "Failed to find end of javadoc %s in %s" % (javadocLines, mcpFilenamePath)
    assert not readingIdentifyingLine, "Failed to find javadoc identifier for %s in %s" % (javadocLines, mcpFilenamePath)

    return javadoc, expectBlankLineBefore

def isBlank(s):
    return len(s.strip()) == 0

def addJavadoc(cbFilenamePath, mcpFilenamePath, className):
    javadoc, expectBlankLineBefore = readJavadoc(mcpFilenamePath)
    #pprint.pprint(javadoc)

    newLines = []
    found = 0
    previousLine = None
    for line in file(cbFilenamePath).readlines():
        identifier = srglib.killWhitespace(line)
        if javadoc.has_key(identifier):
            # This line has associated javadoc
            found += 1
            
            if expectBlankLineBefore[identifier]:
                # Add missing blank
                if not isBlank(previousLine):
                    newLines.append("\n")
            else:
                # Remove extra blank
                if isBlank(previousLine):
                    newLines = newLines[:-1]

            newLines.extend(javadoc[identifier])

        newLines.append(line)
        previousLine = line

    newData = "".join(newLines)
    if rewriteFiles:
        file(cbFilenamePath, "w").write(newData)

    missing = len(javadoc) - found
    if found != 0:
        print "Added %s javadoc to %s" % (found, className)
    if missing != 0:
        print "Skipping %s javadoc in %s" % (missing, className)

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


        addJavadoc(cbFilenamePath, mcpFilenamePath, className)

if __name__ == "__main__":
    main()

