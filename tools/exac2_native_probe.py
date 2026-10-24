"""Field-current base discriminator using the supplied case's excitation constants."""
import sys
import expic1_native_probe as excitation


def configuration(mode,grid,repeat):
    settings=excitation.configuration(mode,grid,repeat)
    if mode not in ('flat','step-up','fault'):raise ValueError('Unknown field-current base profile')
    settings.update(model='EXAC2',state_count=5,
        constants=[0,0,0,300,.066667,8,-8,20,105,-85,.3,10,1,.03,1.2,.3,.4,1,4.4,3.3,.01,4.4,.08],
        artifact_root='ipss.test.plugin.core/testData/private/exac2-field-base',
        extra_scripts=['exac2_native_probe.py'])
    return settings


if __name__=='__main__':
    settings=configuration(*sys.argv[1:]);excitation.configuration=lambda *args:settings;excitation.main()
