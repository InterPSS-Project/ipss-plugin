"""Native curve-order and selector discriminators; source values remain unsorted."""
import sys
import wttqa_native_probe as torque

RECORDS={
    'equal':(1,[.4,2,.066667,60,1.12,0,.4,.58,.4,.73,.82,.87,.97,1,0]),
    'decreasing-first':(1,[.4,2,.066667,60,1.12,0,.504,.58,.4,.74,.76,.869,.905,1,0]),
    'decreasing-last':(1,[.6,3,.066667,30,1.1,.04,.15,.58,.4,.66,.95,.861,.86,1,0]),
    'selector0':(0,[.6,3,.066667,30,1.2,.08,.2,.58,.4,.72,.78,.866,.98,1,0]),
    'selector1':(1,[.6,3,.066667,30,1.2,.08,.2,.58,.4,.72,.78,.866,.98,1,0]),
    'selector2':(2,[.6,3,.066667,30,1.2,.08,.2,.58,.4,.72,.78,.866,.98,1,0])}


def configuration(mode,grid,repeat,power):
    if mode not in RECORDS:raise ValueError('Unknown torque compatibility profile')
    flag,constants=RECORDS[mode]
    settings=torque.configuration('speed' if mode.startswith('selector') else 'flat',grid,repeat)
    if not 0<power<1.1:raise ValueError('Power must remain in the reviewed fixture envelope')
    settings.update(name=mode+'-'+format(power,'.6g').replace('.','p')+'-'+grid,
        constants=list(constants),tflag=flag,initial_power_pu=power,
        artifact_root='ipss.test.plugin.core/testData/private/wttqa-compatibility',
        extra_scripts=['wttqa_compatibility_probe.py'])
    if not mode.startswith('selector'):settings.update(apply_s=.001,clear_s=.002,end_s=.003)
    return settings


if __name__=='__main__':
    settings=configuration(*sys.argv[1:4],float(sys.argv[4]));torque.configuration=lambda *args:settings;torque.main()
