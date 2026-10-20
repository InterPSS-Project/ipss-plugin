"""Cold native round-rotor SMIB references, with entered/read-back data retained."""
import csv
import json
import math
import os
from pathlib import Path
import shutil
import struct
import sys

from hplntd_fault_probe import checked, sha

PROFILES = ('flat', 'fault', 'damping', 'saturated-flat', 'saturated-fault',
            'resistive', 'mva50', 'mva200', 'exponential-flat', 'exponential-fault')
BASE_CON = (6., .033, .54, .078, 6.4, 0., .8958, .8645,
            .1189, .1969, .089, .0521, 0., 0.)


def configuration(profile, raw):
    if profile not in PROFILES:
        raise ValueError('Unknown round-rotor profile: ' + profile)
    constants = list(BASE_CON)
    if profile == 'damping':
        constants[5] = 2.5
    if profile.startswith(('saturated-', 'exponential-')):
        constants[12:] = [.1, .4]
    model = 'GENROE' if profile.startswith('exponential-') else 'GENROU'
    mva = {'mva50': 50., 'mva200': 200.}.get(profile, 100.)
    resistance = .01 if profile == 'resistive' else 0.
    lines = raw.splitlines()
    matches = [i for i, line in enumerate(lines) if line.startswith("1,'1',50.001,")]
    assert len(matches) == 1, 'Exact public generator record required'
    fields = lines[matches[0]].split(',')
    fields[8], fields[9] = str(mva), str(resistance)
    lines[matches[0]] = ','.join(fields)
    dynamic = "1 '" + model + "' '1' " + ' '.join(map(str, constants))
    dynamic += " /\n2 'GENCLS' '1' 99999 0 /\n"
    return dict(model=model, constants=constants, mva=mva, resistance=resistance,
                apply_fault=not profile.endswith('flat'), raw='\n'.join(lines) + '\n',
                dynamic=dynamic)


def f32(value):
    return struct.unpack('<f', struct.pack('<f', value))[0]


def run_case(tag, entered_step, profile, cfg, *, directory_prefix='genrou-native-',
             probe_source=None, state_names=('EP_Q', 'EP_D', 'PSIK_D', 'PSIK_Q', 'SPEED_DEV', 'ANGLE'),
             record_phasors=False, extra_machine_channels=(), support_files=()):
    import psse3607
    import psspy
    import dyntools
    root = Path.cwd()
    step = float(entered_step)
    permitted_steps = (.0005, .00025)
    if cfg.get('pss5c_observation_diagnostic', False):
        assert directory_prefix == 'pss5c-native-', 'Fine steps are an explicit PSS5C diagnostic only'
        permitted_steps += (.00005, .000025)
    assert step in permitted_steps
    end = cfg.get('end_s', .5)
    assert end in (.5, 3.)
    assert tag and all(c.isalnum() or c in '-_' for c in tag)
    assert directory_prefix in ('genrou-native-', 'gensal-native-', 'gensae-field-', 'gensae-recovery-', 'esst1a-washout-', 'esst1a-bypass-', 'ieeest-input-', 'pss5c-native-')
    out = root / 'target' / (directory_prefix + tag)
    out.mkdir(exist_ok=False)
    probe_source = Path(probe_source or __file__).resolve()
    shutil.copy2(probe_source, out / 'probe.py')
    helpers = []
    if probe_source != Path(__file__).resolve():
        shutil.copy2(__file__, out / 'genrou_native_probe.py')
        helpers.append('genrou_native_probe.py')
    for name in support_files:
        assert Path(name).name == name and name.endswith('.py')
        shutil.copy2(probe_source.parent / name, out / name)
        helpers.append(name)
    shutil.copy2(root / 'tools/hplntd_fault_probe.py', out / 'hplntd_fault_probe.py')
    (out / 'network.raw').write_text(cfg['raw'])
    (out / 'dynamic.dyr').write_text(cfg['dynamic'])
    os.chdir(out)
    checked('initialize', psspy.psseinit(50))
    for fn in (psspy.progress_output, psspy.report_output,
               psspy.alert_output, psspy.prompt_output):
        checked('log', fn(2, 'native.log', [0, 0]))
    library = Path(psspy.__file__).resolve().parents[1] / 'PSSBIN/DSMDL.DLL'
    document = library.parent.parent / 'DOCS/ModelLibrary.pdf'
    checked('library', psspy.addmodellibrary(str(library)))
    checked('network', psspy.read(0, 'network.raw'))
    checked('flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0]))
    assert psspy.solved() == 0
    solved = {key: checked(key, psspy.macdat(1, '1', key))
              for key in ('P', 'Q', 'MBASE')}
    checked('convert generators', psspy.cong(0))
    for stage in (1, 2, 3):
        checked('convert loads', psspy.conl(0, 1, stage, [0, 0], [0.] * 4))
    checked('order', psspy.ordr(0))
    checked('factor', psspy.fact())
    checked('network solution', psspy.tysl(0))
    checked('dynamic input', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {}
    for kind in ('CON', 'ICON', 'STATE', 'VAR'):
        response = psspy.mdlind(1, '1', 'GEN', 'N' + kind)
        # This API reports code 6, rather than a successful zero, for an
        # absent standard-model ICON/VAR allocation. Never mask other errors.
        count = 0 if kind in ('ICON', 'VAR') and response[0] == 6 else checked('count ' + kind, response)
        layout['N' + kind] = count
        if count:
            layout[kind] = checked('index ' + kind, psspy.mdlind(1, '1', 'GEN', kind))
    assert tuple(layout['N' + k] for k in ('CON', 'ICON', 'STATE', 'VAR')) == (len(cfg['constants']), 0, len(state_names), 0), layout
    def read_constants():
        return [checked('constant', psspy.dsrval('CON', layout['CON'] + i)) for i in range(len(cfg['constants']))]
    before = read_constants()
    assert before == [f32(v) for v in cfg['constants']], before
    for i, name in enumerate(state_names):
        checked('state channel', psspy.state_channel([-1, layout['STATE'] + i], name))
    components = {}
    for spec in cfg.get('components', ()):
        kind = spec['kind']
        allocation = {}
        for array in ('CON', 'ICON', 'STATE', 'VAR'):
            response = psspy.mdlind(1, '1', kind, 'N' + array)
            count = 0 if array in ('ICON', 'VAR') and response[0] == 6 else checked('count ' + kind + array, response)
            allocation['N' + array] = count
            if count:
                allocation[array] = checked('index ' + kind + array, psspy.mdlind(1, '1', kind, array))
        reserved_icons = spec.get('reserved_icon_count', 0)
        if reserved_icons:
            assert spec['model'] == 'BASOEL2' and reserved_icons == 12 and len(spec['icons']) == 3
        assert tuple(allocation['N' + k] for k in ('CON', 'ICON', 'STATE', 'VAR')) == (
            len(spec['constants']), len(spec['icons']) + reserved_icons, len(spec['states']), spec.get('variable_count', len(spec.get('variables', ())))), allocation
        constants = [checked('constant ' + kind, psspy.dsrval('CON', allocation['CON'] + i)) for i in range(allocation['NCON'])]
        icons = [checked('selector ' + kind, psspy.dsival('ICON', allocation['ICON'] + i)) for i in range(allocation['NICON'])]
        assert constants == [f32(v) for v in spec['constants']] and icons[:len(spec['icons'])] == list(spec['icons'])
        components[kind] = dict(model=spec['model'], layout=allocation, actual_constants=constants, actual_icons=icons[:len(spec['icons'])])
        if reserved_icons:
            components[kind].update(reserved_icon_count=reserved_icons,reserved_icons_initial=icons[len(spec['icons']):])
        for i, name in enumerate(spec['states']):
            checked('state channel ' + kind, psspy.state_channel([-1, allocation['STATE'] + i], name))
        for i, name in enumerate(spec.get('variables', ())):
            checked('variable channel ' + kind, psspy.var_channel([-1, allocation['VAR'] + i], name))
    def validate_components():
        for kind, component in components.items():
            a = component['layout']
            assert component['actual_constants'] == [checked('constant ' + kind, psspy.dsrval('CON', a['CON'] + i)) for i in range(a['NCON'])]
            entered_count = len(component['actual_icons'])
            assert component['actual_icons'] == [checked('selector ' + kind, psspy.dsival('ICON', a['ICON'] + i)) for i in range(entered_count)]
            if component.get('reserved_icon_count', 0):
                component['reserved_icons_final'] = [checked('internal selector ' + kind, psspy.dsival('ICON', a['ICON'] + i))
                                                     for i in range(entered_count, a['NICON'])]
    for bus in (1, 2):
        if record_phasors:
            checked('phasor channels', psspy.voltage_and_angle_channel(
                [-1, -1, -1, bus], ['V_BUS' + str(bus), 'ANGLE_BUS' + str(bus)]))
        else:
            checked('voltage channel', psspy.voltage_channel([-1, -1, -1, bus], 'V_BUS' + str(bus)))
    for bus in cfg.get('frequency_buses', ()):
        checked('frequency channel', psspy.bus_frequency_channel([-1, bus], 'FREQ' + str(bus)))
    for code, name in ((1, 'MACH_ANGLE'), (2, 'P_PU'), (3, 'Q_PU'),
                       (5, 'EFD'), (6, 'PMECH'), (7, 'MACH_SPEED')) + tuple(extra_machine_channels):
        checked('machine channel', psspy.machine_array_channel([-1, code, 1], '1', name))
    for code, name in ((1, 'REF_ANGLE'), (7, 'REF_SPEED')):
        checked('reference channel', psspy.machine_array_channel([-1, code, 2], '1', name))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    if cfg.get('record_rejection_preflight', False):
        assert directory_prefix == 'pss5c-native-'
        preflight=dict(status='native-diagnostic-preflight-not-trajectory-acceptance',profile=profile,
            actual_constants=before,layout=layout,components=components,step_s=step,
            library_sha256=sha(library),source_document_sha256=sha(document),
            python_sha256=sha(sys.executable),wrapper_sha256=sha(psspy.__file__),engine_module=psse3607.__name__,
            trajectory_acceptance=False)
        Path('diagnostic-preflight.json').write_text(json.dumps(preflight,indent=2)+'\n')
    checked('step', psspy.dynamics_solution_param_2([di] * 8, [df, df, step] + [df] * 5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    assert read_constants() == before, 'Initialization changed entered constants'
    validate_components()
    live_states = []
    def record_live_states(stage):
        if not cfg.get('record_live_states', False):
            return
        values = {name: checked('live generator state', psspy.dsrval('STATE', layout['STATE'] + i))
                  for i, name in enumerate(state_names)}
        for spec in cfg.get('components', ()):
            offset = components[spec['kind']]['layout']['STATE']
            values.update({name: checked('live controller state', psspy.dsrval('STATE', offset + i))
                           for i, name in enumerate(spec['states'])})
        entry = dict(stage=stage, time_s=checked('live clock', psspy.dsrval('TIME')),
                     step_s=checked('live step', psspy.dsrval('DELT')), states=values)
        if cfg.get('record_live_variables', False):
            entry['variables'] = {name: checked('live controller variable', psspy.dsrval('VAR', components[spec['kind']]['layout']['VAR'] + i))
                                  for spec in cfg.get('components', ()) for i, name in enumerate(spec.get('variables', ()))}
        live_states.append(entry)
    record_live_states('initialized')
    events = []
    for until in (.05, .1, end):
        checked('interval', psspy.run(0, until, 1000000, 1, 0))
        record_live_states('interval-end')
        if cfg['apply_fault'] and until in (.05, .1):
            actual = checked('event clock', psspy.dsrval('TIME'))
            if until == .05:
                checked('fault', psspy.dist_bus_fault(1, 3, 100, [0, 20]))
            else:
                checked('clear', psspy.dist_clear_fault(1))
            events.append(dict(time_s=until, actual_time_s=actual,
                               action='fault' if until == .05 else 'clear'))
            if cfg.get('record_live_fault_states', False):
                record_live_states('post-fault-event' if until == .05 else 'post-clear-event')
        if until == .05 and cfg.get('vref_increment', 0.):
            increment = cfg['vref_increment']
            assert math.isfinite(increment)
            actual = checked('reference event clock', psspy.dsrval('TIME'))
            checked('voltage reference increment', psspy.increment_vref(1, '1', increment))
            if cfg.get('record_live_event_states', False):
                record_live_states('post-reference-event')
            events.append(dict(time_s=until, actual_time_s=actual,
                               action='voltage-reference-increment', increment=increment))
    _, labels, channels = dyntools.CHNF('native.out').get_data()
    indices = sorted(k for k in labels if isinstance(k, int))
    assert all(math.isfinite(v) for k in ['time'] + indices for v in channels[k])
    with Path('native.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s'] + [labels[k] for k in indices])
        for i, time in enumerate(channels['time']):
            writer.writerow([format(time, '.12g')] + [format(channels[k][i], '.12g') for k in indices])
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    files = ['network.raw', 'dynamic.dyr', 'probe.py', 'hplntd_fault_probe.py',
             'native.log', 'native.out', 'native.csv'] + helpers
    if live_states:
        Path('live-states.json').write_text(json.dumps(live_states, indent=2) + '\n')
        files.append('live-states.json')
    manifest = dict(status='native-converged-profile' if quality else 'rejected-native-quality',
                    model=cfg['model'], profile=profile, declared_constants=cfg['constants'],
                    actual_constants=before, final_constants=read_constants(), layout=layout,
                    solved=solved, resistance_machine_pu=cfg['resistance'], step_s=step, end_s=end,
                    events=events, fault=dict(bus=1, impedance_ohm=[0, 20], impedance_system_pu=[0, .2]),
                    sample_count=len(channels['time']), channel_count=len(indices),
                    library_sha256=sha(library), source_document_sha256=sha(document),
                    python_sha256=sha(sys.executable), wrapper_sha256=sha(psspy.__file__),
                    engine_module=psse3607.__name__, files={name: sha(name) for name in files})
    assert manifest['final_constants'] == before
    validate_components()
    if components:
        manifest['components'] = components
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(status=manifest['status'], profile=profile, samples=manifest['sample_count'])))
    assert quality


def main():
    tag, step, profile = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    run_case(tag, step, profile, configuration(profile, source.read_text()))


if __name__ == '__main__':
    main()
