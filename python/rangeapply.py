#!/usr/bin/python2.7

# Process symbol range maps produced by ApplySrg2Source

import os, sys
import srglib
import argparse  # note: requires Python 2.7+
import subprocess # for git

from pprint import pprint
from pprint import pformat

SEP = "/"   # path-separator from srg2source - always '/' even on Windows! not os.path.sep

# Read ApplySrg2Source symbol range map into a dictionary
# Keyed by filename -> list of (range start, end, expectedOldText, key)
def readRangeMap(filename, srcRoot):
    rangeMap = {}
    for line in file(filename).readlines():
        tokens = line.strip().split("|")
        if tokens[0] != "@": continue
        absFilename, startRangeStr, endRangeStr, expectedOldText, kind = tokens[1:6]
        filename = getProjectRelativePath(absFilename, srcRoot)
        startRange = int(startRangeStr)
        endRange = int(endRangeStr)
        info = tokens[6:]

        if not rangeMap.has_key(filename):
            rangeMap[filename] = []


        # Build unique identifier for symbol

        if kind == "package":
            packageName, forClass = info

            #key = "package "+packageName # ignore old name (unique identifier is filename)
            if forClass == "(file)":
                forClass = getTopLevelClassForFilename(filename)
            else:
                forClass = srglib.sourceName2Internal(forClass)  # . -> / 
   

            # 'forClass' is the class that is in this package; when the class is
            # remapped to a different package, this range should be updated
            key = "package "+forClass
        elif kind == "class":
            className, = info
            key = "class "+srglib.sourceName2Internal(className)
        elif kind == "field":
            className, fieldName = info
            key = "field "+srglib.sourceName2Internal(className)+"/"+fieldName
        elif kind == "method":
            if expectedOldText in ("super", "this"): continue # hack: avoid erroneously replacing super/this calls
            className, methodName, methodSignature = info
            key = "method "+srglib.sourceName2Internal(className)+"/"+methodName+" "+methodSignature
        elif kind == "param":
            className, methodName, methodSignature, parameterName, parameterIndex = info
            key = "param "+srglib.sourceName2Internal(className)+"/"+methodName+" "+methodSignature+" "+str(parameterIndex)  # ignore old name (positional)
        elif kind == "localvar":
            className, methodName, methodSignature, variableName, variableIndex = info
            key = "localvar "+srglib.sourceName2Internal(className)+"/"+methodName+" "+methodSignature+" "+str(variableIndex) # ignore old name (positional)
        else:
            assert False, "Unknown kind: "+kind

        # Map to range
        rangeMap[filename].append((startRange, endRange, expectedOldText, key))

    # Sort and check
    for filename in sorted(rangeMap.keys()):
        sortRangeList(rangeMap[filename], filename)

    return rangeMap

# Read existing local variable name from MCP range map to get local variable positional mapping
def readLocalVariableMap(filename, renameMaps, invClassMap, invMethodMap, invMethodSigMap, srcRoot):
    for line in file(filename).readlines():
        tokens = line.strip().split("|")
        if tokens[0] != "@": continue
        absFilename, startRangeStr, endRangeStr, expectedOldText, kind = tokens[1:6]
        filename = getProjectRelativePath(absFilename, srcRoot)
        startRange = int(startRangeStr)
        endRange = int(endRangeStr)
        info = tokens[6:]

        if kind != "localvar": continue

        mcpClassName, mcpMethodName, mcpMethodSignature, variableName, variableIndex = info

        mcpClassName = srglib.sourceName2Internal(mcpClassName)

        # Range map has MCP names, but we need to map from CB

        if not invClassMap.has_key(mcpClassName):
            className = "net.minecraft.server." + srglib.splitBaseName(mcpClassName) # fake it, assuming CB mc-dev will choose similar name to MCP
            print "WARNING: readLocalVariableMap: no CB class name for MCP class name '%s', using %s" % (mcpClassName, className)
        else:
            className = invClassMap[mcpClassName]

        if mcpMethodName == "{}": 
            # Initializer - no name
            methodName = className + "/{}"
            methodSignature = ""
        elif srglib.splitBaseName(mcpClassName) == mcpMethodName:
            # Constructor - same name as class
            methodName = className + "/" + srglib.splitBaseName(className)
            methodSignature = srglib.remapSig(mcpMethodSignature, invClassMap)
        else:
            # Normal method
            key = mcpClassName+"/"+mcpMethodName+" "+mcpMethodSignature
            if not invMethodMap.has_key(key):
                print "NOTICE: local variables available for %s but no inverse method map; skipping" % (key,)
                # probably a changed signature
                continue

            methodName = invMethodMap[key]
            methodSignature = invMethodSigMap[key]

        key = "localvar "+methodName+" "+methodSignature+" "+str(variableIndex)

        renameMaps[key] = expectedOldText  # existing name


# Transform a rename map to use fully-qualified, removing need for imports
def qualifyClassRenameMaps(renameMap, importMap):
    newRenameMap = renameMap.copy()  # shallow copy OK
    for key, v in renameMap.iteritems():
        if key.startswith("class "):
            # Replace with fully-qualified class name (usually imported, but now insert directly when referenced in source)
            newRenameMap[key] = importMap[key]  
        elif key.startswith("package "):
            # No package names in classes - removing existing qualifications
            newRenameMap[key] = ""

    return newRenameMap

# Get all rename maps, keyed by globally unique symbol identifier, values are new names
def getRenameMaps(srgFiles, mcpConfDir, lvRangeMapFile, dumpRenameMap, srcRoot, excFiles):
    maps = {}
    importMaps = {}

    # CB -> packaged MCP class/field/method
    _notReallyThePackageMap, classMap, fieldMap, methodMap, methodSigMap = srglib.readMultipleSrgs(srgFiles)
    for old,new in classMap.iteritems():
        maps["class "+old]=srglib.splitBaseName(new) 
        importMaps["class "+old]=srglib.internalName2Source(new)  # when renaming class, need to import it, too
    for old,new in fieldMap.iteritems():
        maps["field "+old]=srglib.splitBaseName(new)
    for old,new in methodMap.iteritems():
        maps["method "+old]=srglib.splitBaseName(new)

    # CB class -> MCP package name
    for cbClass, mcpClass in classMap.iteritems():
        cbFile = "src/main/java/"+cbClass+".java"
        mcpPackage = srglib.splitPackageName(mcpClass)
        maps["package "+cbClass] = srglib.internalName2Source(mcpPackage)

    # Read parameter map from MCP.. it comes from MCP with MCP namings, so have to remap to CB 
    invMethodMap, invMethodSigMap = srglib.invertMethodMap(methodMap, methodSigMap)
    invClassMap = srglib.invertDict(classMap)
    if mcpConfDir is not None:
        mcpParamMap = srglib.readParameterMap(mcpConfDir, apply_map = False)
        cbParamMap, removedParamMap = srglib.remapParameterMap(mcpParamMap, invMethodMap, invMethodSigMap, invClassMap)
        if not excFiles is None:
            for file in excFiles:
                tmp = srglib.readParameterMap(mcpConfDir, file, apply_map = False)
                tmp_clean, _ = srglib.remapParameterMap(tmp, invMethodMap, invMethodSigMap, invClassMap, keep_missing=True)
                pprint(tmp_clean)
                cbParamMap.update(tmp_clean)
        # removedParamMap = methods in FML/MCP repackaged+joined but not CB = client-only methods

    else:
        # don't rename any parameters
        mcpParamMap = {}
        cbParamMap = {}
        removedParamMap = {}

    for old,new in cbParamMap.iteritems():
        for i in range(0,len(new)):
            maps["param %s %s" % (old, i)] = new[i]

    # Local variable map - position in source -> name; derived from MCP rangemap
    if lvRangeMapFile is not None:
        readLocalVariableMap(lvRangeMapFile, maps, invClassMap, invMethodMap, invMethodSigMap, srcRoot)

    if dumpRenameMap:
        for key in sorted(maps.keys()):
            newName = maps[key]
            print "RENAME MAP: %s -> %s" % (key, newName)

    return maps, importMaps

# Add new import statements to source
def updateImports(data, newImports, importMap):
    lines = data.split("\n")
    # Parse the existing imports and find out where to add ours
    # This doesn't use Psi.. but the syntax is easy enough to parse here
    newLines = []
    addedNewImports = False

    newImportLines = []
    for imp in sorted(list(newImports)):
        newImportLines.append("import %s;" % (imp,))

    sawImports = False

    for i, line in enumerate(lines):
        if line.startswith("import "):
            sawImports = True

            if line.startswith("import net.minecraft."):
                # If no import map, *remove* NMS imports (OBC rewritten with fully-qualified names)
                if len(importMap) == 0:
                    continue

                # Rewrite NMS imports
                oldClass = line.replace("import ", "").replace(";", "");
                print oldClass
                if oldClass == "net.minecraft.server.*":
                    # wildcard NMS imports (CraftWorld, CraftEntity, CraftPlayer).. bad idea
                    continue
                else:
                    newClass = importMap["class "+srglib.sourceName2Internal(oldClass)]

                newLine = "import %s;" % (newClass,)
                if newLine not in newImportLines:  # if not already added
                    newLines.append(newLine)
            else:
                newLines.append(line)
        else:
            if sawImports and not addedNewImports:
                # Add our new imports right after the last import
                print "Adding %s imports" % (len(newImportLines,))
                newLines.extend(newImportLines)

                addedNewImports = True


            newLines.append(line)

    if not addedNewImports:
        newLines = newLines[0:2] + newImportLines + newLines[2:]

    newData = "\n".join(newLines)

    # Warning: ugly hack ahead
    # The symbol range map extractor is supposed to emit package reference ranges, which we can 
    # update with the correct new package names. However, it has a bug where the package ranges
    # are not always emitted on fully-qualified names. For example: (net.minecraft.server.X)Y - a
    # cast - will fail to recognize the net.minecraft.server package, so it won't be processed by us.
    # This leads to some qualified names in the original source to becoming "overqualified", that is,
    # net.minecraft.server.net.minecraft.X; the NMS class is replaced with its fully-qualified name
    # (in non-NMS source, where we want it to always be fully-qualified): original package name isn't replaced.
    # Occurs in OBC source which uses fully-qualified NMS names already, and NMS source which (unnecessarily)
    # uses fully-qualified NMS names, too. Attempted to fix this problem for longer than I should.. 
    # maybe someone smarter can figure it out -- but until then, in the interest of expediency, I present 
    # this ugly workaround, replacing the overqualified names after-the-fact.
    # Fortunately, this pattern is easy enough to reliably detect and replace textually!
    newData = newData.replace("net.minecraft.server.net", "net")  # OBC overqualified symbols
    newData = newData.replace("net.minecraft.server.Block", "Block") # NMS overqualified symbols
    # ..and qualified inner classes, only one.... last ugly hack, I promise :P
    newData = newData.replace("net.minecraft.block.BlockSapling/*was:BlockSapling*/.net.minecraft.block.BlockSapling.TreeGenerator", "net.minecraft.block.BlockSapling.TreeGenerator")
    newData = newData.replace("net.minecraft.block.BlockSapling.net.minecraft.block.BlockSapling.TreeGenerator", "net.minecraft.block.BlockSapling.TreeGenerator")

    return newData


# Check whether a unique identifier method key is a constructor, if so return full class name for remapping, else None
def getConstructor(key):
    tokens = key.split(" ", 2)  # TODO: switch to non-conflicting separator..types can have spaces :(
    if tokens[0] != "method": return None
    print tokens
    kind, fullMethodName, methodSig = tokens
    if methodSig[-1] != "V": return None # constructors marked with 'V' return type signature in ApplySrg2Source and MCP
    fullClassName = srglib.splitPackageName(fullMethodName)
    methodName = srglib.splitBaseName(fullMethodName)

    packageName = srglib.splitPackageName(fullClassName)
    className = srglib.splitBaseName(fullClassName)

    if className == methodName: # constructor has same name as class
        return fullClassName
    else:
        return None

def getNewName(key, oldName, renameMap, shouldAnnotate):
    if not renameMap.has_key(key):
        constructorClassName = getConstructor(key)
        if constructorClassName is not None:
            # Constructors are not in the method map (from .srg, and can't be derived
            # exclusively from the class map since we don't know all the parameters).. so we
            # have to synthesize a rename from the class map here. Ugh..but, it works.
            print "FOUND CONSTR",key,constructorClassName
            if renameMap.has_key("class "+constructorClassName):
                # Rename constructor to new class name
                newName = srglib.splitBaseName(renameMap["class "+constructorClassName])
            else:
                return None
        else:
            # Not renaming this
            return None
    else:
        newName = renameMap[key]

    if shouldAnnotate:
        newName = newName+"/*was:"+oldName+"*/"

    return newName

# Sort range list by starting offset
# Needed since symbol range output is not always guaranteed to be in source file order
# Also runs a sanity checks, removes duplicates, verifies non-overlapping
# Modifies list in-place
def sortRangeList(rangeList, filename):
    rangeList.sort()  # sorts by keys, tuple, first element is start

    starts = {}
    prevEnd = 0
    newRangeList = []
    for start,end,expectedOldText,key in rangeList:
        if starts.has_key(start):
            # If duplicate, must be identical symbol
            otherStart, otherEnd, otherExpectedOldText, otherKey = starts[start]
            if not (otherStart == start and otherEnd == end and otherExpectedOldText == expectedOldText and otherKey == key):
                print "WARNING: Range map for %s has multiple symbols starting at [%s,%s] '%s' = %s & [%s,%s] '%s' = %s" % (
                    filename,
                    start, end, expectedOldText, key,
                    otherStart, otherEnd, otherExpectedOldText, otherKey)
            continue  # ignore duplicate 

        starts[start] = start,end,expectedOldText,key

        # sanity check
        assert start > prevEnd, "Range map invalid: overlapping symbols, failed check %s > %s: with '%s' = %s" % (start, prevEnd, expectedOldText, key)
        prevEnd = end

        assert len(expectedOldText)==end-start, "Range map invalid: expected old text '%s' length %s != %s (%s - %s)" % (
            expectedOldText, len(expectedOldText), end-start, end, start)

        newRangeList.append((start,end,expectedOldText,key))

    rangeList[:] = []
    rangeList.extend(newRangeList)

# Get the top-level class required to be declared in a file by its given name, if in the main tree
# This is an internal name, including slashes for packages components
def getTopLevelClassForFilename(filename):
    withoutExt, ext = os.path.splitext(filename)
    parts = withoutExt.split(SEP)
    # expect project-relative pathname, standard Maven structure
    #assert parts[0] == "src", "unexpected filename '%s', not in src" % (filename,)
    #assert parts[1] in ("main", "test"), "unexpected filename '%s', not in src/{test,main}" % (filename,)
    #assert parts[2] == "java", "unexpected filename '%s', not in src/{test,main}/java" % (filename,)

    return "/".join(parts[0:])  # "internal" fully-qualified class name, separated by /

# Rename symbols in source code
def processJavaSourceFile(srcRoot, filename, rangeList, renameMap, importMap, shouldAnnotate, options):
    path = os.path.join(options.srcRoot, filename)
    data = file(path).read().decode('utf8')

    if "\r" in data:
        # BlockJukebox is the only file with CRLF line endings in NMS.. and.. IntelliJ IDEA treats offsets 
        # as line endings being one character, whether LF or CR+LF. So remove the extraneous character or
        # offsets will be all off :.
        print "Warning: %s has CRLF line endings; consider switching to LF" % (filename,)
        data = data.replace("\r", "")

    importsToAdd = set()

    shift = 0

    # Existing package/class name (with package, internal) derived from filename
    oldTopLevelClassFullName = getTopLevelClassForFilename(filename)
    oldTopLevelClassPackage = srglib.splitPackageName(oldTopLevelClassFullName)
    oldTopLevelClassName = srglib.splitBaseName(oldTopLevelClassFullName)

    # New package/class name through mapping
    newTopLevelClassPackage = srglib.sourceName2Internal(renameMap.get("package "+oldTopLevelClassFullName))
    newTopLevelClassName = renameMap.get("class "+oldTopLevelClassFullName)
    if newTopLevelClassPackage is not None and newTopLevelClassName is None:
        assert False, "filename %s found package %s->%s but no class map for %s" % (filename, oldTopLevelClassPackage, newTopLevelClassPackage, newTopLevelClassName)
    if newTopLevelClassPackage is None and newTopLevelClassName is not None:
        assert False, "filename %s found class map %s->%s but no package map for %s" % (filename, oldTopLevelClassName, newTopLevelClassName, oldTopLevelClassPackage)

    for start,end,expectedOldText,key in rangeList:
        if renameMap.has_key(key) and len(renameMap[key]) == 0:
            # Replacing a symbol with no text = removing a symbol
            assert key.startswith("package "), "unable to remove non-package symbol %s" % (key,)
            # Remove that pesky extra period after qualified package names
            end += 1
            expectedOldText += "."

        oldName = data[start+shift:end+shift]

        assert oldName == expectedOldText, "Rename sanity check failed: expected '%s' at [%s,%s] (shifted %s to [%s,%s]) in %s, but found '%s'\nRegenerate symbol map on latest sources or start with fresh source and try again" % (
            expectedOldText, start, end, shift, start+shift, end+shift, filename, oldName)

        newName = getNewName(key, oldName, renameMap, shouldAnnotate)
        if newName is None:
            if 'net/minecraft' in key.split(' ')[1]:
                print "No rename for "+key
            continue

        print "Rename",key,[start+shift,end+shift],"::",oldName,"->",newName

        if importMap.has_key(key):
            # This rename requires adding an import, if it crosses packages
            importPackage = srglib.splitPackageName(srglib.sourceName2Internal(importMap[key]))
            if importPackage != newTopLevelClassPackage:
                importsToAdd.add(importMap[key])

        # Rename algorithm: 
        # 1. textually replace text at specified range with new text
        # 2. shift future ranges by difference in text length
        data = data[0:start+shift] + newName + data[end+shift:]
        shift += len(newName) - len(oldName)

    # Lastly, update imports - this is separate from symbol range manipulation above
    data = updateImports(data, importsToAdd, importMap)

    if options.rewriteFiles:
        print "Writing",filename
        file(path,"w").write(data)

    if options.renameFiles:
        if newTopLevelClassPackage is not None: # rename if package changed
            newFilename = os.path.join(newTopLevelClassPackage, newTopLevelClassName + ".java").replace('\\', '/')
            newPath = os.path.join(options.srcRoot, newFilename).replace('\\', '/')

            print "Rename file",filename,"->",newFilename

            if filename != newFilename:
                # Create any missing directories in new path
                dirComponents = os.path.dirname(newPath).split(SEP)
                for i in range(2,len(dirComponents)+1):
                    intermediateDir = os.path.sep.join(dirComponents[0:i])
                    if not os.path.exists(intermediateDir):
                        os.mkdir(intermediateDir)

                #os.rename(path, newPath)

                cmd = [options.git, 'mv', filename, newFilename]
                if not run_command(cmd, cwd=options.srcRoot):
                    sys.exit(1)

def run_command(command, cwd='.', verbose=True):
    print 'Running command: '
    print pformat(command)
        
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, bufsize=1, cwd=cwd)
    while process.poll() is None:
        line = process.stdout.readline()
        if line:
            line = line.rstrip()
            if verbose:
                print line
    if process.returncode:
        print "failed: %d", process.returncode
        return False
    return True

# Get filename relative to project at srcRoot, instead of an absolute path
def getProjectRelativePath(absFilename, srcRoot):
    if absFilename[0] != "/": return absFilename # so much for absolute

    return absFilename.replace(os.path.commonprefix((absFilename, os.path.abspath(srcRoot))) + "/", "") 
    

def main():
    parser = argparse.ArgumentParser(description="Remap source code to new symbols using range map")
    parser.add_argument("--srcRoot", help="Source root directory to rename", required=True)
    parser.add_argument("--git", help="Command to invoke git", required=False, default="git")
    parser.add_argument("--srcRangeMap", help="Source range map generated by srg2source", required=True)
    parser.add_argument("--lvRangeMap", help="Original source range map generated by srg2source, for renaming local variables", required=False)  # TODO: csv instead?
    parser.add_argument("--mcpConfDir", help="MCP configuration directory, for renaming parameters", required=False)
    parser.add_argument("--srgFiles", help="Symbol map file(s)", required=True, nargs="+")
    parser.add_argument("--excFiles", help="Parameter map file(s)", required=False, nargs="+")
    parser.add_argument("--rewriteFiles", help="Whether to rewrite files with new symbol mappings", type=bool, choices=(True, False), default=True)
    parser.add_argument("--renameFiles", help="Whether to rename files with new filenames", type=bool, choices=(True, False), default=True)
    parser.add_argument("--dumpRenameMap", help="Whether to dump symbol rename map before renaming", type=bool, choices=(True, False), default=True)
    options = parser.parse_args()

    print "Reading rename maps..."
    renameMap, importMap = getRenameMaps(options.srgFiles, options.mcpConfDir, options.lvRangeMap, options.dumpRenameMap, options.srcRoot, options.excFiles)
    print "Qualifying rename maps..."
    qualifiedRenameMap = qualifyClassRenameMaps(renameMap, importMap)
    print "Reading range map..."
    rangeMapByFile = readRangeMap(options.srcRangeMap, options.srcRoot)
    print "Processing files..."

    for filename in sorted(rangeMapByFile.keys()):
        if filename.startswith("jline"):
            continue
        elif filename.startswith("net/minecraft"):
            processJavaSourceFile(options.srcRoot, filename, rangeMapByFile[filename], renameMap, importMap, shouldAnnotate=False, options=options)
        else:
            processJavaSourceFile(options.srcRoot, filename, rangeMapByFile[filename], qualifiedRenameMap, {}, shouldAnnotate=True, options=options)

if __name__ == "__main__":
    main()

