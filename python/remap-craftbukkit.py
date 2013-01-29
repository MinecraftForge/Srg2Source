import os, os.path, sys, subprocess, zipfile
import shutil, glob, fnmatch, signal
import csv, re, logging, urllib, stat
from pprint import pprint
from pprint import pformat
from zipfile import ZipFile
from optparse import OptionParser
from ConfigParser import ConfigParser

#if shouldExtract:
    # Extract map of symbol ranges in CB source, required for renaming
    # IDEA must have Srg2source plugin installed, it will detect batchmode and automatically run
#    status = os.system(IDEA+" "+os.path.join(os.getcwd(), CB_ROOT))

#if shouldRename:
    # Change to a new minecraft-server library with MCP names
#    status = os.system("patch -p1 -d "+CB_ROOT+" -R < "+os.path.join(DATA, "pom-slim-minecraft-server.patch"))
#    assert status ==  0

#    status = os.system("patch -p1 -d "+CB_ROOT+" < "+os.path.join(DATA, "pom-minecraft-server-pkgmcp.patch"))
#    assert status == 0

    # Apply the renames
#    status = os.system(PYTHON+" rangeapply.py --srcRoot "+CB_ROOT+" --srcRangeMap "+CB_RANGEMAP+" --lvRangeMap "+MCP_RANGEMAP+" --mcpConfDir "+os.path.join(MCP_ROOT, "conf")+" --srgFiles "+SRG_CB2MCP+" "+SRG_CB2MCP_FIXES)
#    assert status == 0

#if shouldFormat:
    # Reformat source style in NMS (only; not OBC) to more closely resemble MCP
    # This assumes you have astyle installed and ~/.astylerc copied from conf/astyle.cfg
    #ARTISTIC_STYLE_OPTIONS=../mcp726-pkgd/conf/astyle.cfg astyle --suffix=none -R $CB_ROOT/src/main/java/net/minecraft # TODO
    #find $CB_ROOT/src/main/java/net/minecraft -name '*.java' -exec astyle --suffix=none {} \;
#    os.system("find "+os.path.join(CB_ROOT, "src/main/java/net/minecraft")+" -name '*.java' -exec astyle --suffix=none {} \;")

#    os.system(PYTHON+" javadocxfer.py "+CB_ROOT+" "+MCP_ROOT)

#    os.system(PYTHON+" whitespaceneutralizer.py "+CB_ROOT+" "+MCP_ROOT)

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

    def runidea(self, project_dir, project_file=None, batchmode=True):
        self.idea = self.options.idea
        
        if self.idea is None:
            self.logger.error('IDEA command not specified, must pass in a commadn to run IntelliJ')
            sys.exit(1)
            
        if project_file is None:
            project_file = project_dir

        # Extract map of symbol ranges in CB source, required for renaming
        # IDEA must have Srg2source plugin installed, it will detect batchmode and automatically run            
        cookie = os.path.join(project_dir, "srg2source-batchmode")
        if os.path.isfile(cookie): 
            os.remove(cookie)
            
        if batchmode:
            fh = open(cookie, 'w')
            fh.close()

        if not self.run_idea_command([self.idea, os.path.abspath(project_file)]):
            self.logger.error('Extraction failed!')
            sys.exit(1)

        if os.path.isfile(cookie):
            os.remove(cookie)
    
    def run_idea_command(self, command, cwd='.'):
        self.logger.info('Running command: ')
        self.logger.info(pformat(command))
            
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, bufsize=1, cwd=cwd)
        while process.poll() is None:
            line = process.stdout.readline()
            if line:
                line = line.rstrip()
                sys.stdout.write('.')
                #print line
                if 'Srg2source batch mode finished' in line: #Dirty dirty hax put for some reason it hangs
                    print 'Killing process y u hang?'
                    if self.osname == 'win':
                        subprocess.call(['taskkill', '/F', '/T', '/PID', str(process.pid)])
                    else:
                        os.kill(process.pid, signal.SIGUSR1)
                    return True
        if process.returncode:
            self.logger.error("failed: %d", process.returncode)
            return False
        return True

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
            if not self.run_command([sys.executable, 'install.py', '--no-client', '--server'], self.fml_dir):
                self.logger.error('Could not setup FML')
                sys.exit(1)
            
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
        OUT_JAR  = os.path.join('out.jar')
        OUT_SRG  = os.path.join('out.jar.srg')
        CB_JAR   = os.path.abspath('cb_minecraft_server.jar')
        VA_JAR   = os.path.abspath(os.path.join(self.fml_dir, 'mcp', 'jars', 'minecraft_server.jar'))
        SS = ['java', '-jar', os.path.abspath(os.path.join('tools', 'SpecialSource.jar')), CB_JAR, VA_JAR, CB_JAR]
        
        if not os.path.isfile(CB_JAR):
            if not self.download_file(self.dev_url, CB_JAR):
                sys.exit(1)
        
        self.logger.info('Generating SpecialSource srg file')
        if not self.run_command(SS):
            sys.exit(1)
        
        if os.path.isfile(OUT_JAR):
            os.remove(OUT_JAR)
        if os.path.isfile(cb_to_vanilla):
            os.remove(cb_to_vanilla)
        
        shutil.move(OUT_SRG, cb_to_vanilla)

    def generatemcprange(self, rangefile):
        self.logger.info('Generating MCP rangemap')
        fh = open('MCP/MCP.iml.template', 'r')
        data = ''.join(fh.readlines())
        fh.close()
        
        mcp_dir = os.path.abspath(os.path.join(self.fml_dir, 'mcp')).replace('\\', '/')
        data = data.replace('MCP_LOC', mcp_dir)
        
        fh = open('MCP/MCP.iml', 'w')
        fh.write(data)
        fh.close()
        
        self.runidea('MCP', 'MCP/MCP.ipr')
        if os.path.isfile(rangefile):
            os.remove(rangefile)
            
        shutil.copy('MCP/MCP.rangemap', rangefile)

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
                
    def setupslimjar(self):
        CB_JAR = 'cb_minecraft_server.jar'
        if not os.path.isfile(CB_JAR):
            if not self.download_file(self.dev_url, CB_JAR):
                sys.exit(1)
            
        def grab_files(path, main_path=None):
            ret = []
            if main_path is None:
                main_path = os.path.abspath(path) + os.sep
            
            for dirname, subs, files in os.walk(path):
                if '.git' in subs:
                    subs.remove('.git')
                    
                for sub in subs:
                    ret += grab_files(os.path.join(dirname, sub), main_path)
                    
                for file in files:
                    ret.append(os.path.abspath(os.path.join(dirname, file)).replace(main_path, '').replace(os.sep, '/'))
                    
            return ret
            
        repo_files = grab_files(os.path.join(self.cb_dir, 'src', 'main', 'java'))
        
        SLIM_JAR = 'slim_minecraft_server_%s.jar' % self.version
        if os.path.isfile(SLIM_JAR):
            os.remove(SLIM_JAR)
            
        self.logger.info('Stripping repo files from minecraft_server.jar')
    
        zip_in = ZipFile(CB_JAR, mode='a')
        zip_out = ZipFile(SLIM_JAR, 'w', zipfile.ZIP_DEFLATED)
        for i in zip_in.filelist:
            name = i.filename.replace(os.sep, '/')
            if name.endswith('.class') and name.replace('.class', '.java') in repo_files:
                self.logger.info('Skipping: %s' % i.filename)
            else:
                c = zip_in.read(i.filename)
                zip_out.writestr(i.filename, c)
                
        zip_out.close()
        zip_in.close()
        
        MVN_INSTALL = ['mvn', 'install:install-file', 
            '-Dfile=%s' % os.path.abspath(SLIM_JAR), '-DgroupId=org.bukkit', '-DartifactId=slim-minecraft-server',
            '-Dversion=%s' % self.version, '-Dpackaging=jar']
           
        if self.osname == 'win':
            MVN_INSTALL = ['cmd', '/C'] + MVN_INSTALL #DIRT HAX windows doesnt seem to see mvn...
            
        if not self.run_command(MVN_INSTALL, cwd=self.cb_dir):
            self.logger.error('Could not install slim jar')
            sys.exit(1)
            
        os.remove(SLIM_JAR)
        os.remove(CB_JAR)

    def apply_patch(self, target_dir, patch):
        PATCH = ['patch', '-p2', '-i', os.path.abspath(patch)]
        
        if self.osname == 'win':
            PATCH[0] = os.path.abspath(os.path.join(self.fml_dir, 'mcp', 'runtime', 'bin', 'applydiff.exe'))
            
        return self.run_command(PATCH, cwd=target_dir)
      
    def generatecbrange(self, rangefile):
        POM_FILE = os.path.join(self.cb_dir, 'pom.xml')
        BAK_FILE = os.path.join(self.cb_dir, 'pom.bak')
        
        if os.path.isfile(BAK_FILE):
            os.remove(BAK_FILE)
            
        shutil.move(POM_FILE, BAK_FILE)
        
        with open(BAK_FILE, 'rb') as in_file:
            with open(POM_FILE, 'wb') as out_file: 
                buf = in_file.read()
                buf = buf.replace('<artifactId>minecraft-server</artifactId>', '<artifactId>slim-minecraft-server</artifactId>')
                out_file.write(buf)
        
        PRE_PATCH = os.path.join(self.data, self.version, 'prerenamefixes.patch')
        if os.path.isfile(PRE_PATCH):
            self.logger.info('Applying Pre-Rename fixes patch')
            if not self.apply_patch(self.cb_dir, PRE_PATCH):
                self.logger.error('Could not apply pre-patch')
                sys.exit(1)
        
        if os.path.isdir(os.path.join(self.cb_dir, '.idea')):
            shutil.rmtree(os.path.join(self.cb_dir, '.idea'), onerror=self.remove_readonly)
        
        IML_FILE = os.path.join(self.cb_dir, 'craftbukkit.iml') #Copy over templates
        IPR_FILE = os.path.join(self.cb_dir, 'craftbukkit.ipr')
        IWS_FILE = os.path.join(self.cb_dir, 'craftbukkit.iws')
        
        if not os.path.isfile(IML_FILE):
            shutil.copy('craftbukkit.iml', IML_FILE)
            
        if not os.path.isfile(IPR_FILE):
            shutil.copy('craftbukkit.ipr', IPR_FILE)
        
        if os.path.isfile(IWS_FILE):
            os.remove(IWS_FILE)
        shutil.copy('craftbukkit.iws', IWS_FILE)
        
        #self.runidea(self.cb_dir, IPR_FILE, batchmode=False) # Preflight, to refresh from pom
        self.runidea(self.cb_dir, IPR_FILE)
        
        os.remove(POM_FILE)
        shutil.move(BAK_FILE, POM_FILE)
        
        shutil.move(os.path.join(self.cb_dir, os.path.basename(self.cb_dir) + '.rangemap'), rangefile)

    def run_rangeapply(self, cbsrg, mcprange, cbrange):
        RANGEAPPLY = [sys.executable, 'rangeapply.py',
            '--srcRoot', self.cb_dir,
            '--srcRangeMap', cbrange,
            '--lvRangeMap', mcprange,
            '--mcpConfDir', os.path.join(self.fml_dir, 'mcp', 'conf'),
            '--srgFiles',  ''] #"+SRG_CB2MCP+" "+SRG_CB2MCP_FIXES)
            
        from chain import chain
        chained = chain(os.path.join(self.fml_dir, 'mcp', 'conf', 'packaged.srg'), '^' + cbsrg, verbose=False)
        
        SRG_CHAIN = 'chained.srg'
        
        if os.path.isfile(SRG_CHAIN):
            os.remove(SRG_CHAIN)
        
        with open(SRG_CHAIN, 'wb') as out_file: 
            out_file.write('\n'.join(chained))
        
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
    
    mapper.setupslimjar()
    
    cb_range = 'cb.rangemap'
    #mapper.generatecbrange(cb_range)
    
    mapper.run_rangeapply(cb_to_vanilla, van_range, cb_range)
    
if __name__ == '__main__':
    parser = OptionParser()
    parser.add_option('-d', '--data-dir',  action='store', dest='data_dir', help='Data directory, typically a checkout of MinecraftRemaper', default='../../Data')
    parser.add_option('-c', '--cb-dir',    action='store', dest='cb_dir',   help='Path to CraftBukkit clone, none to pull automatically', default=None)
    parser.add_option('-f', '--fml-dir',   action='store', dest='fml_dir',  help='Path to setup FML, none to setup autoamtically', default=None)
    parser.add_option('-v', '--version',   action='store', dest='version',  help='Version to work on, must be a sub folder of --data-dir', default=None)
    parser.add_option('-i', '--idea',      action='store', dest='idea',     help='Instalaton folder of idea', default=None)
    options, args = parser.parse_args()
    
    main(options, args)
    