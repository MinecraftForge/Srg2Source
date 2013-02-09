#!/usr/bin/python

# srglib: routines for manipulating FML/MCP's .srg, .csv, and .exc files

# Not all of the tools use this library yet

import re, os, csv, sys, pprint

EXC_RE = re.compile(r"^([^.]+)\.([^(]+)(\([^=]+)=([^|]*)\|(.*)")

# Get map from full descriptive method name + signature -> list of descriptive parameter names
def readParameterMap(mcpConfDir, excFilename = None, apply_map = True):
    if excFilename is None:
        excFilename = os.path.join(mcpConfDir, "packaged.exc")  # TODO: what about joined.exc?
    methodNum2Name = readDescriptiveMethodNames(mcpConfDir)
    paramNum2Name = readDescriptiveParameterNames(mcpConfDir)

    paramMap = {}

    rows = readExc(excFilename)
    for row in rows:
        className, methodNumber, methodSig, exceptions, paramNumbers = row
         
        if methodNumber == "<init>":
            # constructor
            #methodName = className.split("/")[-1]
            methodName = "<init>"
        elif apply_map and methodNum2Name.has_key(methodNumber):
            # descriptive name
            methodName = methodNum2Name[methodNumber]
        else:
            # no one named this method
            methodName = methodNumber

        fullMethodName = className + "/" + methodName

        # Parameters by number, p_XXXXX_X.. to par1. descriptions
        paramNames = [paramNum2Name[x] if paramNum2Name.has_key(x) else x for x in paramNumbers ]
        
        if not apply_map:
            paramNames = paramNumbers

        paramMap[fullMethodName + " " + methodSig] = paramNames

    return paramMap

# Remap a parameter map's method signatures (keeping the parameter names intact)
# Returns new map, and list of methods not found in mapping and were removed
def remapParameterMap(paramMap, methodMap, methodSigMap, classMap, keep_missing=False):
    newParamMap = {}
    removed = []
    for methodInfo, paramNames in paramMap.iteritems():
        if "<init>" in methodInfo:
            # constructor - remap to new name through class map, not method map
            fullMethodName, methodSig = methodInfo.split(" ")
            className = splitPackageName(fullMethodName)
            newClassName = className
            if not classMap.has_key(className):
                # not in class map - probably client-only class
                removed.append(methodInfo)
                if not keep_missing:
                    continue
            else:
                newClassName = classMap[className]
            constructorName = splitBaseName(newClassName)
            newFullMethodName = newClassName + "/" + splitBaseName(newClassName)
            newMethodSig = remapSig(methodSig, classMap)
        elif not methodMap.has_key(methodInfo):
            # not in method map - probably client-only method
            removed.append(methodInfo)
            if not keep_missing:
                continue
            
            fullMethodName, methodSig = methodInfo.split(" ")
            className = splitPackageName(fullMethodName)
            newFullMethodName = className + "/" + splitBaseName(fullMethodName)
            newMethodSig = remapSig(methodSig, classMap)
        else:
            newFullMethodName = methodMap[methodInfo]
            newMethodSig = methodSigMap[methodInfo]

        newParamMap[newFullMethodName + " " + newMethodSig] = paramNames

    return newParamMap, removed

def invertDict(d):
    r = {}
    for k,v in d.iteritems():
        r[v] = k
    return r

# Invert a method + method signature map, undoing the mapping
def invertMethodMap(inMethodMap, inSigMap):
    outMethodMap = {}
    outSigMap = {}
    assert len(inMethodMap) == len(inSigMap), "invertMethodMap given method map size != in sig map size"
    for inInfo, outName in inMethodMap.iteritems():
        inName, inSig = inInfo.split(" ")
        outSig = inSigMap[inInfo]

        outMethodMap[outName + " " + outSig] = inName
        outSigMap[outName + " "+ outSig] = inSig

    return outMethodMap, outSigMap

# Read .exc file, returning list of tuples per line,
# tuples of class name, method number, signature, list of exceptions through, and list of parameter numbers
def readExc(filename):
    exc = []
    for line in file(filename).readlines():
        match = re.match(EXC_RE, line)
        if match is None:
            continue
        className, methodNumber, methodSig, exceptionsString, paramNumbersString = match.groups()

        # List of classes thrown as exceptions
        exceptions = exceptionsString.split(",")
        if exceptions == ['']: exceptions = []

        # Parameters by number, p_XXXXX_X..
        paramNumbers = paramNumbersString.split(",")
        if paramNumbers == ['']: paramNumbers = []

        exc.append((className, methodNumber, methodSig, exceptions, paramNumbers))

    return exc

# Mapping from parameter number (p_####) to name in source (par#X..)
def readDescriptiveParameterNames(mcpConfDir):
    return readCSVMap(os.path.join(mcpConfDir, "params.csv"))

# Method numbers (func_####) to descriptive name in source
def readDescriptiveMethodNames(mcpConfDir):
    return readCSVMap(os.path.join(mcpConfDir, "methods.csv"))

# Class name to package, from FML/MCP's repackaging
def readClassPackageMap(mcpConfDir):
    return readCSVMap(os.path.join(mcpConfDir, "packages.csv"))

# Read MCP's comma-separated-values files into key->value map
def readCSVMap(path):
    d = {}
    header = True

    for row in csv.reader(file(path), delimiter=","):
        if header: 
            header = False
            continue
        d[row[0]] = row[1]

    return d

def splitPackageName(fullClassName, sep="/"):
    return sep.join(fullClassName.split(sep)[:-1])

def splitBaseName(fullClassName, sep="/"):
    return fullClassName.split(sep)[-1]

# Java bytecode internally uses "/" to separate package/class names, but
# source code uses "." -- these routines convert between the two

def internalName2Source(internalName):
    return internalName.replace("/",".")

def sourceName2Internal(sourceName):
    if sourceName is None: return None
    return sourceName.replace(".","/")

# Read MCP's .srg mappings
def readSrg(filename):
    packageMap = {}
    classMap = {}
    fieldMap = {}
    methodMap = {} # not to be confused with methodMan
    methodSigMap = {}
    for line in file(filename).readlines():
        line = line.strip()
        if len(line) == 0 or line.startswith("#"): continue
        kind, argsString = line.split(": ")
        args = argsString.split(" ")
        if kind == "PK":
            inName, outName = args
            packageMap[inName] = outName
        elif kind == "CL":
            inName, outName = args
            classMap[inName] = outName
        elif kind == "FD": 
            inName, outName = args
            fieldMap[inName] = outName
        elif kind == "MD": 
            inName, inSig, outName, outSig = args

            methodMap[inName + " " + inSig] = outName
            methodSigMap[inName + " " + inSig] = outSig  # fundamentally the same signature, but with types replaced (alternative to remapSig(inSig))
        else:
            assert False, "Unknown type " + kind

    return packageMap, classMap, fieldMap, methodMap, methodSigMap

# Read multiple .srg's, combined into one
def readMultipleSrgs(filenames):
    packageMaps = {}
    classMaps = {}
    fieldMaps = {}
    methodMaps = {}
    methodSigMaps = {}

    for filename in filenames:
        packageMap, classMap, fieldMap, methodMap, methodSigMap = readSrg(filename)

        packageMaps.update(packageMap)
        classMaps.update(classMap)
        fieldMaps.update(fieldMap)
        methodMaps.update(methodMap)
        methodSigMaps.update(methodSigMap)

    return packageMaps, classMaps, fieldMaps, methodMaps, methodSigMaps

# Remap method signatures through a class map
def remapSig(sig, classMap):
    def lookup(match):
        className = match.group(1)
        return "L" + classMap.get(className, className) + ";"
    return remapSigThrough(sig, lookup)

def remapSigThrough(sig, lookup):
    return re.sub(r"L([^;]+);", lookup, sig)

# Rename file to path possibly containing non-existent directories, created as necessary
def rename_path(oldPath, newPath):
    dirComponents = os.path.dirname(newPath).split(os.path.sep)
    for i in range(2,len(dirComponents)+1):
        intermediateDir = os.path.sep.join(dirComponents[0:i])
        if not os.path.exists(intermediateDir):
            os.mkdir(intermediateDir)

    os.rename(oldPath, newPath)

# Get all .java file paths recursively within a source directory
def getJavaSourceFiles(srcdir):
    paths = []
    for root, dirs, files in os.walk(srcdir):
        for fn in files:
            if not fn.endswith(".java"): continue

            path = os.path.join(root, fn)
            paths.append(path)
        for d in dirs:
            paths.extend(getJavaSourceFiles(os.path.join(root, d)))
        return paths

"""Get a string with _all_ whitespace removed."""
def killWhitespace(s):
    return "".join(s.split())

"""Get if a file is non-NMS but in NMS namespace"""
def isPollution(mcpFilenamePath):
    return mcpFilenamePath.endswith("BaseMod.java") or mcpFilenamePath.endswith("FakeInventory.java") or mcpFilenamePath.endswith("FMLLogJLineBreakProxy.java") or mcpFilenamePath.endswith("TradeEntry.java")

