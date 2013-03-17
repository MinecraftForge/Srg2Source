#!/usr/bin/python

# Remap each CraftBukkit commit

import subprocess
import os
import shutil
import xml.dom.minidom
import re
import sys

inOriginalDir = "CraftBukkit"       # original source
inRemappedDir = "../output"       # output of remapper
inRemappedDirNames = ("patches", "src/org", "src/jline") # subdirectories in inRemappedDir to add 

shouldCloneRepo = True
shouldPullLatestChanges = True
shouldCheckoutMaster = True
remoteSource = "origin" # 'git remote' name
repoURL = "https://github.com/Bukkit/CraftBukkit/commit/"
masterBranch = "master"
#defaultStartCommit = "437c575bc9b97cfc226128608e910ccf0f9a33b0" # commit before d3d98a166f05f8fadfcd11adc0318c548da8a25b Update CraftBukkit to Minecraft 1.5
defaultStartCommit = "d3d98a166f05f8fadfcd11adc0318c548da8a25b" # Update CraftBukkit to Minecraft 1.5

def runRemap():
    print "Starting remap script..."
    print "pwd=",os.getcwd()
    if not os.path.exists("remap-craftbukkit.py"):
        print "No remapping script found!"
    run(sys.executable + " remap-craftbukkit.py --cb-dir "+inOriginalDir+" --fml-dir fml --skip-finish-cleanup --skip-compile")
    print "Remap script finished"

def run(cmd):
    print ">",cmd
    #raw_input()
    status = os.system(cmd)
    assert status == 0, "Failed to run '%s' status=%s" % (cmd, status)

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
    if not os.path.exists(outDirGitRepo):
        print "Creating",outDirGitRepo
        os.mkdir(outDirGitRepo)

    pushd(outDirGitRepo)

    if not os.path.exists(".git"):
        print "No git repository found in "+outDirGitRepo
        print "Initializing new repository"
        run("git init")
        popd()
        print "Starting at default "+defaultStartCommit
        return defaultStartCommit

    message = runOutput(("git", "show", "--format=%B"))

    r = re.compile(CREDIT_MESSAGE_PREFIX.strip() + "([0-9a-f]+)")
    match = r.search(message)
    if not match:
        print "Unrecognized commit message: >>>\n"+message+"\n<<< - no match for '"+CREDIT_MESSAGE_PREFIX+"'"
        print "Starting at default "+defaultStartCommit
        popd()
        return defaultStartCommit

    commit = match.group(1)
    popd()

    print "Resuming from upstream commit",repoURL+commit
    return commit

def main():
    global outDirGitRepo

    if len(sys.argv) != 2:
        print "usage: %s <mcpbukkit-git-directory>" % (sys.argv[0],)
        raise SystemExit

    outDirGitRepo = sys.argv[1]

    if shouldCloneRepo:
        if os.path.exists(inOriginalDir):
            shutil.rmtree(inOriginalDir) 

        run("git clone http://github.com/Bukkit/CraftBukkit "+inOriginalDir)

    pushd(inOriginalDir)
    if shouldPullLatestChanges:
        # Get all the latest changes 
        run("git pull "+remoteSource+" "+masterBranch)

    if shouldCheckoutMaster:
        clean()
        run("git checkout "+masterBranch)

    # Get commits beyond our last remapped commit, the new commits to be remapped
    startCommit = getStartCommit()
    commits = readCommitLog(startCommit)
    popd()

    for commitInfo in commits:
        # Remap this commit
        commit, shortMessage = commitInfo
        print "\n\n*** %s %s" % (commit, shortMessage)
        pushd(inOriginalDir)
        clean()
        run("git checkout "+commit)
        author, date, message = getCommitInfo(commit)
        popd()
        try:
            runRemap()
        except Exception as e:
            print "WARNING!!! Remapping failed with exception:",e
            print "Continuing anyways"

        # Append message to commit
        # TODO: former-commit in 'commit notes' like bfg? http://rtyley.github.com/bfg-repo-cleaner/
        message += CREDIT_MESSAGE_PREFIX+commit

        # Copy remapper output to git repository
        for part in inRemappedDirNames:
            a = os.path.join(inRemappedDir, part)
            b = os.path.join(outDirGitRepo, part)
            print "Copying %s -> %s" % (a, b)
            if os.path.exists(b): shutil.rmtree(b)
            shutil.copytree(a, b)

        # For copying full NMS source (vs patches) with --skip-output-archive; might want this in a separate repo later
        ## Copy to target
        #a = os.path.join(inOriginalDir, "src")
        #b = os.path.join(outDirGitRepo, "src")
        #print "Copying %s -> %s" % (a, b)
        #if os.path.exists(b): shutil.rmtree(b)
        #shutil.copytree(a, b)

        # Generate the new remapped commit
        commitFile = os.path.join(os.getcwd(), "commit.msg")
        pushd(outDirGitRepo)
        for part in inRemappedDirNames:
            run("git add "+part)
        file(commitFile,"w").write(message)
        run("git commit --file='%s' --all --author='%s' --date='%s'" % (commitFile, author, date))
        os.unlink(commitFile)
        popd()
       
if __name__ == "__main__":
    main()

