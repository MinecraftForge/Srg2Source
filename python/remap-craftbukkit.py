#!/usr/bin/python

# Remap CraftBukkit to MCP mappings - main driver script

import os, os.path, sys, subprocess, zipfile
import shutil, glob, fnmatch, signal
import csv, re, logging, urllib, stat
import difflib
import xml.dom.minidom
from pprint import pprint
from pprint import pformat
from zipfile import ZipFile
from optparse import OptionParser
from ConfigParser import ConfigParser
    
class Remapper(object):
    def __init__(self, options):
        self.options = options
        self.startlogger()

        if sys.platform.startswith('linux'):
            self.osname = 'linux'
        elif sys.platform.startswith('darwin'):
            self.osname = 'osx'
        elif sys.platform.startswith('win'):
            self.osname = 'win'
        else:
            self.logger.error('Unrecognized OS: %s, assuming Linux-compatible', sys.platform)
            self.osname = 'linux'
        self.logger.debug('OS : %s', sys.platform)

    def startlogger(self):
        log_file = 'remapper.log'
        if os.path.isfile(log_file):
            os.remove(log_file)
        
        self.logger = logging.getLogger('Refactoring')
        self.logger.setLevel(logging.INFO)
        fh = logging.FileHandler(log_file)
        fh.setFormatter(logging.Formatter('%(asctime)s - %(message)s'))
        self.logger.addHandler(fh)
        ch = logging.StreamHandler()
        ch.setFormatter(logging.Formatter("%(message)s"))
        self.logger.addHandler(ch)
        
    def readversion(self):
        self.version = str(xml.dom.minidom.parse(os.path.join(self.cb_dir, "pom.xml")).getElementsByTagName("minecraft.version")[0].firstChild.data)
        print "Minecraft version (from CraftBukkit): %s" % (self.version,)
 
        self.data = self.options.data_dir
        self.logger.debug('Data: %s' % self.data)
    
        config_file = os.path.join(self.data, self.version, 'config.properties')
        if not os.path.isfile(config_file):
            self.logger.error('Could not find data file: %s' % config_file)
            
        config = ConfigParser()
        config.read(config_file)
        
        self.fml_url = config.get('Main', 'fml_url')
        self.dev_url = config.get('Main', 'dev_url')
        self.repo_version = config.get('Main', 'repo_version')
        
        self.logger.info('FML: %s' % self.fml_url)
        self.logger.info('DEV: %s' % self.dev_url)
        self.logger.info('SHA: %s' % self.repo_version)

    def download_file(self, url, target):
        name = os.path.basename(target)
        
        if not os.path.isfile(target):
            try:
                urllib.urlretrieve(url, target)
                self.logger.info('Downloaded %s' % name)
            except Exception as e:
                self.logger.error(e)
                self.logger.error('Download of %s failed' % target)
                return False
        else:
            self.logger.info('File Exists: %s' % os.path.basename(target))
        return True
        
    def setupfml(self):
        self.fml_dir = self.options.fml_dir
        self.fml_clean = False
        
        if not self.fml_dir is None: # Don't setup FML if directory is specified
            return
            
        self.fml_clean = True
        self.fml_dir = 'fml'
        if os.path.isdir(self.fml_dir):
            self.logger.info('Deleting FML dir: %s' % self.fml_dir)
            shutil.rmtree(self.fml_dir)

        fml_tmp = 'fml.zip'
        if os.path.isfile(fml_tmp):
            os.remove(fml_tmp)
        
        if not self.download_file(self.fml_url, fml_tmp):
            self.logger.error('Could not download FML, aborting')
            sys.exit(1)
            
        self.logger.info('Extracting fml')
        zip = ZipFile(fml_tmp)
        zip.extractall('.')
        zip.close()
        os.remove(fml_tmp)
        
        if os.path.isfile(os.path.join(self.fml_dir, 'install.py')):
            orig_dir = os.path.abspath('.')
            
            self.logger.info('Setting up FML')
            if not self.run_command([sys.executable, 'install.py', '--no-client', '--server', '--no-rename'], self.fml_dir):
                self.logger.error('Could not setup FML')
                sys.exit(1)
                
            # Here until FML officially rolls out renaming local vars in 1.5
            # XXX: remove since 1.5 is officially out?
            from rename_vars import rename_file
            
            for path, _, filelist in os.walk(os.path.join(self.fml_dir, 'mcp', 'src', 'minecraft_server'), followlinks=True):
                for cur_file in fnmatch.filter(filelist, '*.java'):
                    file = os.path.normpath(os.path.join(path, cur_file))
                    rename_file(file, MCP=True)
            
    def run_command(self, command, cwd='.', verbose=True):
        self.logger.info('Running command: ')
        self.logger.info(pformat(command))
            
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, bufsize=1, cwd=cwd)
        while process.poll() is None:
            line = process.stdout.readline()
            if line:
                line = line.rstrip()
                if verbose:
                    self.logger.info(line)
                else:
                    self.logger.debug(line)
                    sys.stdout.write('.')
        if process.returncode:
            self.logger.error("failed: %d", process.returncode)
            return False
        return True

    def generatecbsrg(self, cb_to_vanilla):
        OUT_SRG  = os.path.join('specialsource.srg')
        CB_JAR   = os.path.abspath('cb_minecraft_server.jar')
        VA_JAR   = os.path.abspath(os.path.join(self.fml_dir, 'mcp', 'jars', 'minecraft_server.jar'))
        # TODO: download from http://search.maven.org/remotecontent?filepath=net/md-5/SpecialSource/1.4/SpecialSource-1.4-shaded.jar and update to release
        SS = ['java', '-jar', os.path.abspath(os.path.join('tools', 'SpecialSource-1.3-SNAPSHOT-shaded-14x.jar')),
            '--generate-dupes',
            '--first-jar',  CB_JAR, 
            '--second-jar', VA_JAR,            
            '--srg-out',    OUT_SRG]
        
        if not os.path.isfile(CB_JAR):
            if not self.download_file(self.dev_url, CB_JAR):
                sys.exit(1)
        
        self.logger.info('Generating SpecialSource srg file')
        if not self.run_command(SS):
            sys.exit(1)
        
        if os.path.isfile(cb_to_vanilla):
            os.remove(cb_to_vanilla)
        
        shutil.move(OUT_SRG, cb_to_vanilla)
        
        os.remove(CB_JAR)

    def clean_rangemap(self, in_file, out_file):
        rangeMap = {}
        for line in file(in_file).readlines():
            tokens = line.strip().split("|")
            if tokens[0] != "@": continue
            
            filename = tokens[1].replace('\\', '/')
            data = '|'.join(tokens[2:])

            if not rangeMap.has_key(filename):
                rangeMap[filename] = []
            rangeMap[filename].append(data)
            
        for filename in sorted(rangeMap.keys()):
            rangeMap[filename] = sorted(set(rangeMap[filename]), lambda x,y: int(x.split('|')[0]) - int(y.split('|')[0]))
        
        self.logger.info('Writing clean srg')
        with open(out_file, 'wb') as fh:
            for key in sorted(rangeMap.keys()):
                fh.write('Processing %s\n' % key)
                for data in rangeMap[key]:
                    fh.write('@|%s|%s\n' % (key,data))
            fh.close()
        
        return rangeMap
        
    def generatemcprange(self, rangefile):
        self.download_file("https://github.com/LexManos/Srg2Source/blob/master/python/tools/RangeExtractor.jar?raw=true", "tools/RangeExtractor.jar") 
        RANGE = ['java', '-jar', os.path.abspath(os.path.join('tools', 'RangeExtractor.jar')),
            os.path.join(self.fml_dir, 'mcp', 'src', 'minecraft_server'),
            os.path.join(self.fml_dir, 'mcp', 'lib'),
            'MCP.rangemap']
            
        self.logger.info('Generating MCP rangemap')
        self.run_command(RANGE)
            
        self.clean_rangemap('MCP.rangemap', rangefile)
        os.remove('MCP.rangemap')

    def remove_readonly(self, fn, path, excinfo):
        if fn is os.rmdir:
            os.chmod(path, stat.S_IWRITE)
            shutil.rmtree(path, self.remove_readonly)
        elif fn is os.remove:
            os.chmod(path, stat.S_IWRITE)
            os.remove(path)
            
    def checkoutcb(self):
        self.cb_dir = self.options.cb_dir
        self.cb_clean = False
        
        if not self.cb_dir is None: # Don't setup CB if its specified, assume it's already setup
            return
            
        self.cb_clean = True
        self.cb_dir = 'craftbukkit'
        if os.path.isdir(self.cb_dir):
            shutil.rmtree(self.cb_dir, onerror=self.remove_readonly)
            
        self.logger.info('Cloneing CraftBukkit git')
        if not self.run_command(['git', 'clone', 'git://github.com/Bukkit/CraftBukkit.git', os.path.abspath(self.cb_dir)]):
            self.logger.error('Could not clone CraftBukkit!')
            sys.exit(1)

    def setupcb(self):
        if not self.repo_version is None and not self.repo_version == '':
            self.logger.info('Resetting head to \'%s\'' % self.repo_version)
            if not self.run_command(['git', 'reset', '--hard', self.repo_version], os.path.abspath(self.cb_dir)):
                self.loger.error('Could not reset head')
                sys.exit(1)
                
        ASTYLE = ['astyle', 
            '--suffix=none', 
            '--options=' + os.path.join(self.fml_dir, 'mcp', 'conf', 'astyle.cfg'), 
            os.path.join(self.cb_dir, 'src', 'main', 'java', 'net', 'minecraft', 'server', '*')]
    
        if sys.platform.startswith('win'):
            ASTYLE[0] = os.path.join(self.fml_dir, 'mcp', 'runtime', 'bin', 'astyle.exe')
            
        self.logger.info('Running astyle of net/minecraft/server')
        self.run_command(ASTYLE)
                
    def apply_patch(self, target_dir, patch):
        PATCH = ['patch', '-p2', '-i', os.path.abspath(patch)]
        
        if self.osname == 'win':
            PATCH[0] = os.path.abspath(os.path.join(self.fml_dir, 'mcp', 'runtime', 'bin', 'applydiff.exe'))
            
        return self.run_command(PATCH, cwd=target_dir)
      
    def generatecbrange(self, rangefile):
        RANGE = ['java', 
            '-jar', os.path.abspath(os.path.join('tools', 'RangeExtractor.jar')),
            os.path.join(self.cb_dir, 'src', 'main', 'java'),
            'none',
            'output.rangemap']
        
        dep_file = os.path.abspath('classpath.txt')
        DEPS = ['mvn', 'dependency:build-classpath', '-Dmdep.outputFile=%s' % dep_file]
        if self.osname == 'win':
            DEPS = ['cmd', '/C'] + DEPS
        
        if not self.run_command(DEPS, cwd=self.cb_dir):
            self.logger.error('Could not extract dependancies from Maven')
            sys.exit(1)
        
        with open(dep_file, 'rb') as in_file:
            RANGE[4] = in_file.read()
        os.remove(dep_file)
        
        self.logger.info('Generating CB rangemap')
        if not self.run_command(RANGE):
            self.logger.error('Could not extract craftbukkit rangemap')
            sys.exit(1)
            
        self.clean_rangemap('output.rangemap', rangefile)
        os.remove('output.rangemap')
        
        return RANGE[4].split(os.pathsep)

    def run_rangeapply(self, cbsrg, mcprange, cbrange, chained_srg):
        RANGEAPPLY = [sys.executable, 'rangeapply.py',
            '--srcRoot', os.path.join(self.cb_dir, 'src', 'main', 'java'),
            '--srcRangeMap', cbrange,
            '--lvRangeMap', mcprange,
            '--mcpConfDir', os.path.join(self.fml_dir, 'mcp', 'conf')]
            
        data = os.path.join(self.data, self.version)
        
        excs = [f for f in os.listdir(data) if f.endswith('.exc')]
        if len(excs) > 0:
            RANGEAPPLY += ['--excFiles']
            for file in excs:
                RANGEAPPLY += [os.path.join(data, file)]
                
        srgs = [f for f in os.listdir(os.path.join(self.data, self.version)) if f.endswith('.srg') and not f == 'cb_to_vanilla.srg']
        RANGEAPPLY += ['--srgFiles', chained_srg]
        if len(srgs) > 0:
            for file in srgs:
                RANGEAPPLY += [os.path.join(data, file)]
            
        from chain import chain
        chained = chain(os.path.join(self.fml_dir, 'mcp', 'conf', 'packaged.srg'), '^' + cbsrg, verbose=False)
        
        if os.path.isfile(chained_srg):
            os.remove(chained_srg)
        
        with open(chained_srg, 'wb') as out_file: 
            out_file.write('\n'.join(chained))
        
        if not self.run_command(RANGEAPPLY):
            sys.exit(1)
        
    def cleanup_source(self, cb_srg):
        SRG_MCP = os.path.join(self.fml_dir, 'mcp', 'conf', 'packaged.srg')
        SRC_MCP = os.path.join(self.fml_dir, 'mcp', 'src', 'minecraft_server', 'net', 'minecraft')
        SRC_CB  = os.path.join(self.cb_dir, 'src', 'main', 'java', 'net', 'minecraft')
        
        sys.path.append(os.path.join(self.fml_dir, 'mcp', 'runtime', 'pylibs'))
        from cleanup_src import src_cleanup
        print 'Running MCP src cleanup:'
        src_cleanup(SRC_CB, fix_imports=True, fix_unicode=True, fix_charval=True, fix_pi=True, fix_round=False)
        
        sys.path.append(os.path.join(self.fml_dir))
        from fml import cleanup_source
        print 'Running MCP src cleanup:'
        cleanup_source(SRC_CB)
    
        from cleanup_var_names import cleanup_var_names
        print 'Cleaning local variable names:'
        cleanup_var_names(SRG_MCP, cb_srg, SRC_CB)
        
        from whitespaceneutralizer import nutralize_whitespace
        nutralize_whitespace(SRC_CB, SRC_MCP)

    def codefix_cb(self, deps, chained_srg):
        CODEFIX = ['java', 
            '-cp', os.path.abspath(os.path.join('tools', 'RangeExtractor.jar')),
            'ast.CodeFixer',
            os.path.join(self.cb_dir, 'src', 'main', 'java'),
            os.pathsep.join(deps),
            chained_srg]
        
        self.logger.info('Attempting to fix CB compiler errors')
        if not self.run_command(CODEFIX):
            self.logger.error('Could not run CodeFixer')
            sys.exit(1)
        
    def create_patches(self, output):
        self.logger.info('Creating patches')
        SRC_MCP = os.path.join(self.fml_dir, 'mcp', 'src', 'minecraft_server')
        SRC_CB  = os.path.join(self.cb_dir, 'src', 'main', 'java')
    
        patchd = os.path.normpath(output)
        base = os.path.normpath(SRC_MCP)
        work = os.path.normpath(SRC_CB)
        
        if os.path.isdir(patchd):
            shutil.rmtree(patchd, onerror=self.remove_readonly)
    
        for path, _, filelist in os.walk(work, followlinks=True):
            for cur_file in fnmatch.filter(filelist, '*.java'):
                file_base = os.path.normpath(os.path.join(base, path[len(work)+1:], cur_file)).replace(os.path.sep, '/')
                file_work = os.path.normpath(os.path.join(work, path[len(work)+1:], cur_file)).replace(os.path.sep, '/')
                
                if not os.path.isfile(file_base):
                    self.logger.info('Could not find base class? %s -> %s' % (file_base, file_work))
                    continue
                fromlines = open(file_base, 'U').readlines()
                tolines = open(file_work, 'U').readlines()
                
                patch = ''.join(difflib.unified_diff(fromlines, tolines, '../' + file_base[len(SRC_MCP)+1:], '../' + file_work[len(SRC_CB)+1:], '', '', n=3))
                patch_dir = os.path.join(patchd, path[len(work)+1:])
                patch_file = os.path.join(patch_dir, cur_file + '.patch')
                
                if len(patch) > 0:
                    self.logger.info(patch_file[len(patchd)+1:])
                    patch = patch.replace('\r\n', '\n')
                    
                    if not os.path.exists(patch_dir):
                        os.makedirs(patch_dir)
                    with open(patch_file, 'wb') as fh:
                        fh.write(patch)
                else:
                    self.logger.info('Found CB file that had no changes, deleting: %s' % file_work)
                    os.remove(file_work)

    def merge_tree(self, root_src_dir, root_dst_dir):
        for src_dir, dirs, files in os.walk(root_src_dir):
            dst_dir = src_dir.replace(root_src_dir, root_dst_dir)
            if not os.path.exists(dst_dir):
                os.mkdir(dst_dir)
            for file_ in files:
                src_file = os.path.join(src_dir, file_)
                dst_file = os.path.join(dst_dir, file_)
                if os.path.exists(dst_file):
                    os.remove(dst_file)
                shutil.copy(src_file, dst_dir)

    def gather_files(self, src_dir, pattern, append_pattern=False, all_files=False):
        dirs = []
        for path, dirlist, filelist in os.walk(src_dir, followlinks=True):
            sub_dir = os.path.relpath(path, src_dir)
            files = fnmatch.filter(filelist, pattern)
            if files:
                if all_files:
                    dirs.extend([os.path.join(path, f) for f in files])
                elif append_pattern:
                    dirs.append(os.path.join(path, pattern))
                else:
                    dirs.append(path)
        return dirs
        
    def compile_cb(self, deps):
        self.logger.info('Gathering dependencies')
        for dep in deps:
            target = os.path.join(self.fml_dir, 'mcp', 'lib', os.path.basename(dep))
            if os.path.isfile(target):
                os.remove(target)
            shutil.copyfile(dep, target)
            
        #self.logger.info('Moving sources')
        SRC_MCP = os.path.join(self.fml_dir, 'mcp', 'src', 'minecraft_server')
        SRC_CB  = os.path.join(self.cb_dir, 'src', 'main', 'java')
        #self.merge_tree(SRC_CB, SRC_MCP)
        
        java_files = self.gather_files(SRC_CB, '*.java', append_pattern=self.osname == 'win', all_files=self.osname != 'win')
        COMPILE = ['javac', 
            '-cp', os.pathsep.join(deps),
            '-encoding', 'UTF-8',
            '-Xlint:-options', 
            #'-deprecation',
            '-g',
            '-source', '1.6',
            '-target', '1.6',
            '-classpath', os.pathsep.join(deps),
            '-sourcepath', SRC_CB,
            '-d', os.path.join(self.fml_dir, 'mcp', 'bin', 'minecraft_server')
            ] + java_files
        
        self.logger.info('Compiling CB with MCP')
        if not self.run_command(COMPILE):
            self.logger.error('Could not compile CraftBukkit with MCP names')
            sys.exit(1)
            
        self.logger.info('Reobfuscating CB with MCP')
        if not self.run_command([sys.executable, os.path.join('runtime', 'reobfuscate.py'), '--server'], os.path.join(self.fml_dir, 'mcp')):
            self.logger.error('Could not setup FML')
            sys.exit(1)
    
    def zipfolder(self, path, relname, archive):
        paths = os.listdir(path)
        for p in paths:
            p1 = os.path.join(path, p) 
            p2 = os.path.join(relname, p)
            if os.path.isdir(p1): 
                self.zipfolder(p1, p2, archive)
            else:
                archive.write(p1, p2) 

    def create_zip(self, path, archname):
        archive = zipfile.ZipFile(archname, "w", zipfile.ZIP_DEFLATED)
        self.zipfolder(path, '', archive)
        archive.close()
    
    def create_output(self, patch_dir):
        self.out_dir = self.options.out_dir
        PATCH_OUT = os.path.join(self.out_dir, 'patches')
        SRC_OUT = os.path.join(self.out_dir, 'src')
        BIN_OUT = os.path.join(self.out_dir, 'bin')
        
        self.logger.info('Gathering output files to %s' % self.out_dir)
        
        if os.path.isdir(self.out_dir):
            shutil.rmtree(self.out_dir, onerror=self.remove_readonly)
            
        os.mkdir(self.out_dir)
        
        self.logger.info('Grabbing binary')
        self.create_zip(os.path.join(self.fml_dir, 'mcp', 'reobf', 'minecraft_server'), os.path.join(self.out_dir, 'craftbukkit_mcp.zip'))
        
        self.logger.info('Grabbing patches')
        shutil.move(patch_dir, PATCH_OUT)
        
        self.logger.info('Grabbing sources')
        shutil.move(os.path.join(self.cb_dir, 'src', 'main', 'java'), SRC_OUT)
        
        self.logger.info('Killing patched sources')
        for path, _, filelist in os.walk(PATCH_OUT, followlinks=True):
            for cur_file in fnmatch.filter(filelist, '*.java.patch'):
                file = os.path.normpath(os.path.join(SRC_OUT, path[len(PATCH_OUT)+1:], cur_file)).replace(os.path.sep, '/')
                file = file[:-6]
                
                if os.path.isfile(file):
                    self.logger.info('    %s' % file[len(SRC_OUT)+1:])
                    os.remove(file)
                    
        def cleanDirs(path):
            if not os.path.isdir(path):
                return
         
            files = os.listdir(path)
            if len(files):
                for f in files:
                    fullpath = os.path.join(path, f)
                    if os.path.isdir(fullpath):
                        cleanDirs(fullpath)
         
            files = os.listdir(path)
            if len(files) == 0:
                os.rmdir(path)
                
        cleanDirs(SRC_OUT)
        
        if self.fml_clean:
            self.logger.info('Cleaning FML')
            shutil.rmtree(self.fml_dir, onerror=self.remove_readonly)
            
        if self.cb_clean:
            self.logger.info('Cleaning CraftBukkit')
            shutil.rmtree(self.cb_dir, onerror=self.remove_readonly)
            
        #Just for good measure :P
        for pyc in glob.glob('*.pyc'):
            os.remove(pyc)



def main(options, args):
    mapper = Remapper(options)
        
    class PrintHook:
        def __init__(self, logger):
            self.logger = logger
            self.out = sys.__stdout__
            sys.stdout = self
            
        def write(self, text):
            text = text.rstrip('\r\n')
            if len(text):
                self.logger.info(text)
            #self.out.write(text)
            
        def __getattr__(self, name):
            return self.origOut.__getattr__(name)

    hook = PrintHook(mapper.logger)
    
    mapper.checkoutcb()
    mapper.readversion()
    mapper.setupfml()
    mapper.setupcb()
    
    CHAINED_SRG = 'chained.srg'
    
    cb_to_vanilla = os.path.join(mapper.data, mapper.version, 'cb_to_vanilla.srg')
    if not os.path.isfile(cb_to_vanilla):
        mapper.generatecbsrg(cb_to_vanilla)
        
    van_range = os.path.join(mapper.data, mapper.version, 'mcp.rangemap')
    if not os.path.isfile(van_range):
        mapper.generatemcprange(van_range)
        
    cb_range = 'cb.rangemap'
    cb_deps = mapper.generatecbrange(cb_range)
    
    mapper.run_rangeapply(cb_to_vanilla, van_range, cb_range, CHAINED_SRG)
    os.remove(van_range)
    os.remove(cb_range)
    
    mapper.cleanup_source(cb_to_vanilla)
    
    mapper.logger.info('Dependencies:')
    for x in range(0, len(cb_deps) - 1):
        if 'minecraft-server' in cb_deps[x]:
            cb_deps[x] = os.path.abspath(os.path.join(mapper.fml_dir, 'mcp', 'temp', 'minecraft_server_rg.jar'))
        mapper.logger.info('    ' + cb_deps[x])
        
    mapper.codefix_cb(cb_deps, CHAINED_SRG)
    os.remove(CHAINED_SRG)
    
    mapper.create_patches('patches')
    
    mapper.compile_cb(cb_deps)
    
    mapper.create_output('patches')
    
if __name__ == '__main__':
    parser = OptionParser()
    parser.add_option('-d', '--data-dir',  action='store', dest='data_dir', help='Data directory, typically a checkout of MinecraftRemapper', default='../Data')
    parser.add_option('-c', '--cb-dir',    action='store', dest='cb_dir',   help='Path to CraftBukkit clone, none to pull automatically', default=None)
    parser.add_option('-f', '--fml-dir',   action='store', dest='fml_dir',  help='Path to setup FML, none to setup automatically', default=None)
    parser.add_option('-o', '--out-dir',   action='store', dest='out_dir', help='Output directory to place remapped files and patches', default='../output')
    options, args = parser.parse_args()

    main(options, args)
    
