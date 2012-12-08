#!/usr/bin/python

import subprocess
import os

CB_CLASS_LIST = "../forge/classes-patched-cb-mcdev"
ALL_CLASS_LIST_SRG = "../jars/cb2mcp.srg"
MC_DEV_EXTRACTED_DIR = "../jars/mc-dev"

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
    header = """// Auto-generated methods stubs for %s
import org.apache.commons.lang.NotImplementedException;
""" % (cls, )

    lines = subprocess.Popen(["javap", "-classpath", MC_DEV_EXTRACTED_DIR, cls], stdout=subprocess.PIPE).communicate()[0].split("\n")

    print header

    for line in lines[1:]: # skip initial "Compiled from" line
        line = line.replace("net.minecraft.server.", "")

        if ")" in line:
            parts = line.split("(")
            retn = parts[0]
            args = parts[1].replace(");", "").split(", ")
            if len(args) == 1 and len(args[0]) == 0: args = []

            namedArgs = []
            for i, arg in enumerate(args):
                namedArgs.append("%s par%d" % (arg, i + 1))

            line = retn + "(" + ", ".join(namedArgs) + ") { throw new NotImplementedException(); }"


        print line

