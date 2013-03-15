#!/usr/bin/python

# Remap each CraftBukkit commit

import subprocess
import os
import shutil
import xml.dom.minidom

srcRoot = "../CraftBukkit"          # original source
scriptDir = "../Srg2Source/python"  # relative to srcRoot
outDir = "/tmp/MCPBukkit"           # remapped source output
srcComponent = "src"                # common directory name for source

shouldPullLatestChanges = True
shouldCheckoutMaster = True
remoteSource = "origin" # 'git remote' name
repoURL = "https://github.com/Bukkit/CraftBukkit/commit/"
masterBranch = "master"
defaultStartCommit = "27f73b62998ef7ba6b951a5cc7acbb95a1a17bed" # Updated version to 1.4.7-R1.0 in pom.xml for RB.


def runRemap():
    print "Starting remap script..."
    mcVersion = getVersion("pom.xml")
    pushd(scriptDir)
    #run("./remap-craftbukkit.py --version "+mcVersion)
    popd()
    print "Remap script finished"

def run(cmd):
    print ">",cmd
    #raw_input()
    assert os.system(cmd) == 0, "Failed to run "+cmd

def runOutput(cmd):
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.read()

DIR_STACK = []
def pushd(newDir):
    print "cd",newDir
    DIR_STACK.append(os.getcwd())
    os.chdir(newDir)

def popd():
    print "cd",DIR_STACK[-1]
    os.chdir(DIR_STACK.pop())

"""Clean out even non-repository or moved files."""
def clean():
    run("rm -rf src")
    run("git reset --hard HEAD")

"""Get game version from project object model."""
def getVersion(filename):
    return str(xml.dom.minidom.parse(filename).getElementsByTagName("minecraft.version")[0].firstChild.data)

"""Get commit IDs and short messages after the starting commit, in reverse chronological order."""
def readCommitLog(startCommit):
    commits = []
    for line in runOutput(("git", "log", "--format=oneline")).split("\n"):
        assert len(line) != 0, "Reached end of commit log without finding starting commit "+startCommit
        commit, message = line.split(" ", 1)
        if commit == startCommit: break
        print commit, message
        commits.append((commit, message))
    commits.reverse()
    return commits

"""Get detailed information on a commit."""
def getCommitInfo(commit):
    out = runOutput(("git", "show", commit, "--format=format:%an <%ae>%n%aD%n%B%n---END---"))
    lines = out.split("\n")
    author = lines[0]
    date = lines[1]
    messageLines = []
    for i in range(2,len(lines)):
        if lines[i] == "---END---": break
        messageLines.append(lines[i])
    message = "\n".join(messageLines)

    return author, date, message

CREDIT_MESSAGE_PREFIX = "\n\nRemapped by Srg2Source from "+repoURL

"""Get the last remapped commit, if any"""
def getStartCommit():
    pushd(outDir)

    if not os.path.exists(".git"):
        print "No git repository found in "+outDir
        print "Initializing new repository"
        run("git init")
        popd()
        print "Starting at default "+defaultStartCommit
        return defaultStartCommit

    message = runOutput(("git", "show", "--format=%b"))
    if CREDIT_MESSAGE_PREFIX not in message:
        print "Unrecognized commit message: >>>\n"+message+"\n<<< - no match for '"+CREDIT_MESSAGE_PREFIX+"'"
        print "Starting at default "+defaultStartCommit
        popd()
        return defaultStartCommit

    n = message.index(CREDIT_MESSAGE_PREFIX)
    commit = message[CREDIT_MESSAGE_PREFIX:]
    print "C1",commit
    commit = commit[:commit.index("\n")]
    print "C2",commit
    popd()

    return commit

def main():
    if os.path.basename(os.getcwd()) != os.path.basename(srcRoot): os.chdir(srcRoot)

    if shouldPullLatestChanges:
        # Get all the latest changes 
        run("git pull "+remoteSource+" "+masterBranch)

    if shouldCheckoutMaster:
        clean()
        run("git checkout "+masterBranch)

    # Get commits beyond our last remapped commit, the new commits to be remapped
    startCommit = getStartCommit()
    commits = readCommitLog(startCommit)

    for commitInfo in commits:
        # Remap this commit
        commit, shortMessage = commitInfo
        print "\n\n*** %s %s" % (commit, shortMessage)
        clean()
        run("git checkout "+commit)
        runRemap()
        author, date, message = getCommitInfo(commit)

        # Append message to commit
        # TODO: former-commit in 'commit notes' like bfg? http://rtyley.github.com/bfg-repo-cleaner/
        message += CREDIT_MESSAGE_PREFIX+commit

        # Copy to target
        a = os.path.join(srcRoot, srcComponent)
        b = os.path.join(outDir, srcComponent)
        print "Copying %s -> %s" % (a, b)
        if os.path.exists(b): shutil.rmtree(b)
        shutil.copytree(a, b)

        # Generate the new remapped commit
        pushd(outDir)
        commitFile = "commit.msg"
        run("git add "+srcComponent)
        file(commitFile,"w").write(message)
        run("git commit --file=%s --all --author='%s' --date='%s'" % (commitFile, author, date))
        os.unlink(commitFile)
        popd()
       
if __name__ == "__main__":
    main()

