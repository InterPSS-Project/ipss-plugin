"""Small-case discriminators for the supplied case's unusual controller records."""
import json,shutil,sys
from pathlib import Path


def configuration(model,grid,repeat):
    import repca_native_probe as plant
    import wttqa_native_probe as torque
    if model in ('plant-positive-deadband','plant-positive-deadband-fault','plant-signed-deadband-fault'):
        settings=plant.configuration('flat-v' if model=='plant-positive-deadband' else 'v',grid,repeat)
        settings.update(name='texas7k-'+model+'-'+grid,icons=[1,0,0,'0',1,1,0],
            constants=[.066667,18,5,0,.05,0,0,0,.02,.1,-.1,.025,0,.493,-.493,.1,.05,.25,0,0,999,-999,999,-999,.1,20,0],
            source_bus=220216)
        if model=='plant-signed-deadband-fault':settings['constants'][11]=-.025
        return settings
    records={
        'torque-equal':(150492,1,[.4,2,.066667,60,1.12,0,.4,.58,.4,.73,.82,.87,.97,1,0]),
        'torque-decreasing':(150502,1,[.4,2,.066667,60,1.12,0,.504,.58,.4,.74,.76,.869,.905,1,0]),
        'torque-selector2':(190149,2,[.6,3,.066667,30,1.2,.08,.2,.58,.4,.72,.78,.866,.98,1,0])}
    if model not in records:raise ValueError('Unknown case parameter discriminator')
    bus,flag,constants=records[model]
    settings=torque.configuration('flat',grid,repeat)
    settings.update(name='texas7k-'+model+'-'+grid,tflag=flag,constants=constants,source_bus=bus)
    return settings


def main():
    model,grid,repeat=sys.argv[1:];settings=configuration(model,grid,repeat);root=Path.cwd()
    if model.startswith('plant-'):
        import repca_native_probe
        repca_native_probe.run(settings,extra_scripts=('texas7k_parameter_probe.py',),prefix='case-parameter-')
    else:
        import wttqa_native_probe
        out=root/'target'/('wttqa-native-'+settings['name']+'-'+repeat)
        wttqa_native_probe.configuration=lambda *args:settings
        try:wttqa_native_probe.main()
        finally:
            if out.is_dir():
                shutil.copy2(__file__,out/'texas7k_parameter_probe.py')
                # Preserve the discriminator even if native validation rejects it.
                (out/'case-parameter-settings.json').write_text(json.dumps(settings,indent=2)+'\n')


if __name__=='__main__':main()
