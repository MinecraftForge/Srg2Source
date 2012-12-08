#!/usr/bin/python

import subprocess
import os

CB_CLASS_LIST = "../forge/classes-patched-cb-mcdev"
ALL_CLASS_LIST_SRG = "../jars/cb2mcp.srg"
MC_DEV_EXTRACTED_DIR = "../jars/mc-dev"
OUT_STUB_DIR = "../CraftBukkit/src/main/java/"

def getPatchedByCB():
    return set([x.strip() for x in file(CB_CLASS_LIST).readlines()])

def getAll():
    # get all classes, with mcdev namings
    classes = []
    fields = {}
    for line in file("../jars/cb2mcp.srg").readlines():
        line = line.strip()
        tokens = line.split(" ")
        if tokens[0] == "CL:": 
            classes.append(tokens[1])

    return set(classes)

unpatched = getAll() - getPatchedByCB()
for cls in sorted(list(unpatched)):
    filename = OUT_STUB_DIR + cls + ".java"
    if os.path.exists(filename):
        #print "File already exists:",filename
        #raise SystemExit
        pass

    if not os.path.exists(os.path.dirname(filename)):
        # org/ dirs need to be created; CB only has net/
        #os.mkdir(os.path.dirname(filename)) # need recursive mkdir
        os.system("mkdir -p " + os.path.dirname(filename)) # warning: injection
    
    print filename
    f = file(filename, "w")

    header = """// Auto-generated methods stubs for %s

package net.minecraft.server;

import org.apache.commons.lang.NotImplementedException;
""" % (cls, )

    f.write(header)

    lines = subprocess.Popen(["javap", "-classpath", MC_DEV_EXTRACTED_DIR, cls], stdout=subprocess.PIPE).communicate()[0].split("\n")

    if "Compiled" in lines[0]:
        lines = lines[1:] # skip initial "Compiled from" line, if present

    for line in lines:
        line = line.replace("net.minecraft.server.", "")  # already in package

        line = line.replace(" final ", " ") # 

        if "{}" in line:
            # Skip static initializer (always empty)
            continue

        # Methods
        if ")" in line:
            parts = line.split("(")
            retn = parts[0]
            args = parts[1].replace(");", "").split(", ")
            if len(args) == 1 and len(args[0]) == 0: args = []

            namedArgs = []
            for i, arg in enumerate(args):
                namedArgs.append("%s par%d" % (arg, i + 1))

            line = retn + "(" + ", ".join(namedArgs) + ") { throw new NotImplementedException(); }"


        f.write(line + "\n")
    f.close()

