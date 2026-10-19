"""Documented native plant fallback and retained remote-voltage discriminators."""
import sys
from repca_native_probe import configuration as base_configuration,run

MODES=('missing-edge','missing-circuit','from-zero','to-zero','circuit-zero','all-zero','negative-target')

def configuration(mode,grid,repeat):
    if mode not in MODES:raise ValueError('Unknown plant fallback profile')
    settings=base_configuration('ldc',grid,repeat)
    settings.update(name=mode+'-'+grid,mode=mode)
    if mode=='missing-edge':settings['icons'][1:4]=[1,3,'1']
    if mode=='missing-circuit':settings['icons'][1:4]=[1,2,'Z']
    if mode=='from-zero':settings['icons'][1:4]=[0,2,'1']
    if mode=='to-zero':settings['icons'][1:4]=[1,0,'1']
    if mode=='circuit-zero':settings['icons'][1:4]=[1,2,'0']
    if mode=='all-zero':settings['icons'][1:4]=[0,0,'0']
    if mode=='negative-target':settings['icons'][1:4]=[1,-2,'1']
    return settings

if __name__=='__main__':run(configuration(*sys.argv[1:]),extra_scripts=['repca_fallback_probe.py'],prefix='repca-fallback-matrix-')
