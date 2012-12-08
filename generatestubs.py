#!/usr/bin/python

import subprocess
import os

CB_CLASS_LIST = "../forge/classes-patched-cb-mcdev"
ALL_CLASS_LIST_SRG = "../jars/cb2mcp.srg"
MC_DEV_EXTRACTED_DIR = "../jars/mc-dev"
OUT_STUB_DIR = "../CraftBukkit/src/main/java/"

MC_DEV_SOURCE_DIR = "../mc-dev"

"""Get classes patched by CraftBukkit"""
def getPatchedByCB():
    return set([x.strip() for x in file(CB_CLASS_LIST).readlines()])

"""Get all classes (with mc-dev namings)"""
def getAll():
    classes = []
    fields = {}
    for line in file("../jars/cb2mcp.srg").readlines():
        line = line.strip()
        tokens = line.split(" ")
        if tokens[0] == "CL:": 
            classes.append(tokens[1])

    return set(classes)


"""An attempt at generating compatible but empty methods stubs from javap."""
def generateStubFromJavap(fullClassPath, filename):
    f = file(filename, "w")

    package = ".".join(fullClassPath.split("/")[:-1])
    className = fullClassPath.split("/")[-1]

    header = """// Auto-generated methods stubs for %s

package %s;

import org.apache.commons.lang.NotImplementedException;

""" % (className, package)

    f.write(header)

    lines = subprocess.Popen(["javap", "-classpath", MC_DEV_EXTRACTED_DIR, fullClassPath], stdout=subprocess.PIPE).communicate()[0].split("\n")

    if "Compiled" in lines[0]:
        lines = lines[1:] # skip initial "Compiled from" line, if present


    for line in lines:
        line = line.replace(package + ".", "")  # already in package

        line = line.replace(" final ", " ") # 

        if "{}" in line:
            # Skip static initializer (always empty)
            continue

        if ")" in line:
            # Methods - add parameter names and body

            parts = line.split("(")
            retn = parts[0]
            args = parts[1].replace(");", "").split(", ")
            if len(args) == 1 and len(args[0]) == 0: args = []

            namedArgs = []
            for i, arg in enumerate(args):
                namedArgs.append("%s par%d" % (arg, i + 1))

            if " abstract " in line:
                # abstract methods can't have a body
                body = ";"
            else:
                body = "{ throw new NotImplementedException(); }"

            line = retn + "(" + ", ".join(namedArgs) + ")" + body
        elif line.startswith("  public static") or line.startswith("  protecte static"): # not doing private
            # static fields need initializers
            tokens = line.strip().replace(";","").split(" ")
            name = tokens[-1]
            if "byte" in tokens or "short" in tokens or "int" in tokens:
                default = "0"
            elif "long" in tokens:
                default = "0L"
            elif "float" in tokens:
                default = "0.0f"
            elif "double" in tokens:
                default = "0.0d"
            elif "char" in tokens:
                default = "'\u0000'"
            elif "boolean" in tokens:
                default = "false";
            else:
                default = "null";

            line = line.replace(";", " = %s;" % (default,))


        f.write(line + "\n")
    f.close()

def copyFromMcdev(fullClassPath, outputFilename):
    os.system("cp -v " + MC_DEV_SOURCE_DIR + "/" + fullClassPath + ".java" + " " + outputFilename) # warning: inj

def main():
    unpatched = getAll() - getPatchedByCB()
    for fullClassPath in sorted(list(unpatched)):
        outputFilename = OUT_STUB_DIR + fullClassPath + ".java"
        if os.path.exists(outputFilename):
            #print "File already exists:",outputFilename
            #raise SystemExit
            pass

        if not os.path.exists(os.path.dirname(outputFilename)):
            # org/ dirs need to be created; CB only has net/
            #os.mkdir(os.path.dirname(outputFilename)) # need recursive mkdir
            os.system("mkdir -p " + os.path.dirname(outputFilename)) # warning: injection
        
        print outputFilename

        #generateStubFromJavap(fullClassPath, outputFilename)
        copyFromMcdev(fullClassPath, outputFilename)

if __name__ == "__main__":
    main()
