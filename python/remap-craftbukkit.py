import os, os.path, sys, subprocess, zipfile
import shutil, glob, fnmatch, signal
import csv, re, logging, urllib, stat
from pprint import pprint
from pprint import pformat
from zipfile import ZipFile
from optparse import OptionParser
from ConfigParser import ConfigParser

    # Measure differences to get a sense of progress
#    os.system("diff -ur "+os.path.join(MCP_ROOT,"src/minecraft_server/net/minecraft/")+" "+os.path.join(CB_ROOT, "src/main/java/net/minecraft/")+" > "+DIFF_OUT)

#    print len(file(DIFF_OUT).readlines())
    
class Remapper(object):
    def __init__(self, options):
        self.options = options
        self.startlogger()
        self.readversion()

        if sys.platform.startswith('linux'):
            self.osname = 'linux'
        elif sys.platform.startswith('darwin'):
            self.osname = 'osx'
        elif sys.platform.startswith('win'):
            self.osname = 'win'
        else:
            self.logger.error('OS not supported : %s', sys.platform)
            sys.exit(1)
        self.logger.debug('OS : %s', sys.platform)

    def startlogger(self):
        log_file = 'remapper.log'
        if os.path.isfile(log_file):
            os.remove(log_file)
        
        self.logger = logging.getLogger('Refacoring')
        self.logger.setLevel(logging.INFO)
        fh = logging.FileHandler(log_file)
        fh.setFormatter(logging.Formatter('%(asctime)s - %(message)s'))
        self.logger.addHandler(fh)
        ch = logging.StreamHandler()
        ch.setFormatter(logging.Formatter("%(message)s"))
        self.logger.addHandler(ch)

    def readversion(self):
        self.data = self.options.data_dir
        self.version = self.options.version
        self.logger.debug('Data: %s' % self.data)
        self.logger.debug('Version: %s' % self.version)
    
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
        
        if not self.fml_dir is None: # Dont setup fml if its specified
            return
            
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
                
            # Here until FML offocially rolls out renaming local vars in 1.5
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
        RANGE = ['java', '-jar', os.path.abspath(os.path.join('tools', 'RangeExtractor.jar')),
            os.path.join(self.fml_dir, 'mcp', 'src', 'minecraft_server'),
            os.path.join(self.fml_dir, 'mcp', 'lib'),
            'MCP.rangemap']
            
        self.logger.info('Generating MCP rangemap')
        self.run_command(RANGE)
            
        self.clean_rangemap('MCP.rangemap', rangefile)

    def remove_readonly(self, fn, path, excinfo):
        if fn is os.rmdir:
            os.chmod(path, stat.S_IWRITE)
            shutil.rmtree(path, self.remove_readonly)
        elif fn is os.remove:
            os.chmod(path, stat.S_IWRITE)
            os.remove(path)
            
    def setupcb(self):
        self.cb_dir = self.options.cb_dir
        
        if not self.cb_dir is None: # Dont setup fml if its specified, assume it's already setup
            return
            
        self.cb_dir = 'craftbukkit'
        if os.path.isdir(self.cb_dir):
            shutil.rmtree(self.cb_dir, onerror=self.remove_readonly)
            
        self.logger.info('Cloneing CraftBukkit git')
        if not self.run_command(['git', 'clone', 'git://github.com/Bukkit/CraftBukkit.git', os.path.abspath(self.cb_dir)]):
            self.logger.error('Could not clone CraftBukkit!')
            sys.exit(1)
            
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
        RANGE = ['java', '-jar', os.path.abspath(os.path.join('tools', 'RangeExtractor.jar')),
            os.path.join(self.cb_dir, 'src', 'main', 'java'),
            os.path.abspath(os.path.join(self.data, self.version, 'libs')),
            'CB.rangemap']
            
        self.logger.info('Generating CB rangemap')
        self.run_command(RANGE)
            
        self.clean_rangemap('CB.rangemap', rangefile)

    def run_rangeapply(self, cbsrg, mcprange, cbrange):
        SRG_CHAIN = 'chained.srg'
        RANGEAPPLY = [sys.executable, 'rangeapply.py',
            '--srcRoot', os.path.join(self.cb_dir, 'src', 'main', 'java'),
            '--srcRangeMap', cbrange,
            '--lvRangeMap', mcprange,
            '--mcpConfDir', os.path.join(self.fml_dir, 'mcp', 'conf'),
            #'--rewriteFiles',
            #'--renameFiles',
            '--srgFiles',  SRG_CHAIN] #"+SRG_CB2MCP+" "+SRG_CB2MCP_FIXES)
            
        from chain import chain
        chained = chain(os.path.join(self.fml_dir, 'mcp', 'conf', 'packaged.srg'), '^' + cbsrg, verbose=False)
        
        if os.path.isfile(SRG_CHAIN):
            os.remove(SRG_CHAIN)
        
        with open(SRG_CHAIN, 'wb') as out_file: 
            out_file.write('\n'.join(chained))
            
        self.run_command(RANGEAPPLY)
        
    def cleanup_source(self, cb_srg):
        SRG_MCP = os.path.join(self.fml_dir, 'mcp', 'conf', 'packaged.srg')
        SRC_MCP = os.path.join(self.fml_dir, 'mcp', 'src', 'minecraft_server', 'net', 'minecraft')
        SRC_CB  = os.path.join(self.cb_dir, 'src', 'main', 'java', 'net', 'minecraft')
        
        sys.path.append(os.path.join(self.fml_dir, 'mcp', 'runtime', 'pylibs'))
        from cleanup_src import src_cleanup
        print 'Running MCP src cleanup:'
        src_cleanup(SRC_CB, fix_imports=True, fix_unicode=True, fix_charval=True, fix_pi=True, fix_round=False)
    
        from cleanup_var_names import cleanup_var_names
        print 'Cleaning local variable names:'
        cleanup_var_names(SRG_MCP, cb_srg, SRC_CB)
        
        from whitespaceneutralizer import nutralize_whitespace
        nutralize_whitespace(SRC_CB, SRC_MCP)
        
def main(options, args):
    mapper = Remapper(options)
    mapper.setupfml()
    
    cb_to_vanilla = os.path.join(mapper.data, mapper.version, 'cb_to_vanilla.srg')
    if not os.path.isfile(cb_to_vanilla):
        mapper.generatecbsrg(cb_to_vanilla)
        
    van_range = os.path.join(mapper.data, mapper.version, 'mcp.rangemap')
    if not os.path.isfile(van_range):
        mapper.generatemcprange(van_range)
        
    mapper.setupcb()
    
    cb_range = 'cb.rangemap'
    mapper.generatecbrange(cb_range)
    
    mapper.run_rangeapply(cb_to_vanilla, van_range, cb_range)
    
    mapper.cleanup_source(cb_to_vanilla)
    
if __name__ == '__main__':
    parser = OptionParser()
    parser.add_option('-d', '--data-dir',  action='store', dest='data_dir', help='Data directory, typically a checkout of MinecraftRemaper', default='../../Data')
    parser.add_option('-c', '--cb-dir',    action='store', dest='cb_dir',   help='Path to CraftBukkit clone, none to pull automatically', default=None)
    parser.add_option('-f', '--fml-dir',   action='store', dest='fml_dir',  help='Path to setup FML, none to setup autoamtically', default=None)
    parser.add_option('-v', '--version',   action='store', dest='version',  help='Version to work on, must be a sub folder of --data-dir', default=None)
    parser.add_option('-i', '--idea',      action='store', dest='idea',     help='Instalaton folder of idea', default=None)
    options, args = parser.parse_args()
    
    main(options, args)
    