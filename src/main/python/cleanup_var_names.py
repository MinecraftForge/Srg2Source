#!/usr/bin/python

# Cleanup jad-style variable names with CB/MCP class names

import os, os.path, sys, csv, re, fnmatch, shutil, zipfile, pprint
from optparse import OptionParser
from pprint import pprint

def get_class_map(mcp, cb):
    def get_cl(file, invert=False):
        values = {}
        with open(file, 'r') as fh:
            for line in fh:
                pts = line.rstrip('\r\n').split(' ')
                if pts[0] == 'CL:':
                    values[pts[1].split('/')[-1]] = pts[2].split('/')[-1]
        return values
        
    map_cb = get_cl(cb)
    map_mcp = get_cl(mcp)
    map = {}
    for t,m in map_cb.iteritems():
        if m in map_mcp:
            map[map_mcp[m]] = t
        else:
            print m + " " + t
    for k,v in map.items():
        if k == v:
            map.pop(k)
    return map

def rename_file(file, map={}):
    tmp = file + '.tmp'
    with open(file, 'rb') as in_file:
        print file
        data = rename_class(in_file.read().replace('\r', ''), map)
        with open(tmp, 'wb') as out_file:
            out_file.write(data)
    shutil.move(tmp, file)
    
def rename_class(data, map={}):
    replace = {}
    for k,v in map.items():
        reg  = r' %s(\[*\]*) ((a*)(%s)(\d*))' % (k, v.lower())
        
        for m in re.findall(reg, data):
            replace[m[1]] = m[2] + k.lower() + m[4]
            print '  ' + m[1] + ' -> ' + replace[m[1]]
            
    for k in sorted(replace, key=len, reverse=True): # Through keys sorted by length
        data = re.sub(r'%s([^\d\w])' % k, '%s\\1' % replace[k], data)
        
    return data
    
def cleanup_var_names(srg_mcp, srg_cb, path):
    map = get_class_map(srg_mcp, srg_cb)
    for path, _, filelist in os.walk(path, followlinks=True):
        for cur_file in fnmatch.filter(filelist, '*.java'):
            file = os.path.normpath(os.path.join(path, cur_file))
            rename_file(file, map)
            
def main(options, args):
    for arg in args:
        cleanup_var_names(options.mcp, options.cb, arg)
if __name__ == '__main__':
    parser = OptionParser()
    parser.add_option('-m', '--mcp', action='store', dest='mcp', help='Vanilla to MCP srg file', default='mcp.srg')
    parser.add_option('-c', '--cb',  action='store', dest='cb',  help='CraftBukkit to Vanilla srg file', default='cb.srg')
    options, args = parser.parse_args()

    main(options, args)
