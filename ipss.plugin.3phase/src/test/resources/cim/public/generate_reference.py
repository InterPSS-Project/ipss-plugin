"""Run independent CIMHub exports in DSS-Python 0.15.7 / DSS C-API 0.14.5.
Usage: python generate_reference.py /path/to/exported/dss /path/to/cimhub-1.1.0.jar
All snapshot adjustments are recorded in per-case settings files.
"""
import csv
import gzip
import hashlib
import json
import math
import sys
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from dss import DSS

HERE = Path(__file__).resolve().parent
C = '{http://iec.ch/TC57/CIM100#}'
R = '{http://www.w3.org/1999/02/22-rdf-syntax-ns#}'

def text(e, key):
    return e.find(C + key).text

def run(directory, jar):
    inputs = json.loads((HERE / 'manifest.json').read_text())
    manifest = {'engine': DSS.Version, 'cimhub_commit': inputs['commit'],
                'converter_jar': jar.name, 'converter_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'cases': []}
    archive_files = []
    hashes = {}
    for case in inputs['cases']:
        name = case['name']
        xml = gzip.decompress((HERE / (name + '.xml.gz')).read_bytes())
        assert hashlib.sha256(xml).hexdigest() == case['sha256_xml'], name
        root = ET.fromstring(xml)
        engine = DSS.NewContext()
        engine.Text.Command = f'compile "{directory / (name + "_base.dss")}"'
        circuit = engine.ActiveCircuit
        settings = ['set controlmode=off loadmult=1 tolerance=1e-10 maxiterations=200',
                    'batchedit load..* vminpu=0 vmaxpu=1000000',
                    'batchedit transformer..* ppm_antifloat=0']
        # Only the isolated delta secondary needs an artificial ground reference.
        # Its line-to-line voltages, not the arbitrary common mode, are compared.
        transformers = circuit.Transformers
        for transformer in transformers.AllNames:
            transformers.Name = transformer
            transformers.Wdg = transformers.NumWindings
            if transformers.IsDelta:
                settings += [f'edit transformer.{transformer} ppm_antifloat=1']
        # Reapply the converter's explicit CIM tap settings after compile/calcv.
        settings += [s for s in (directory / (name + '_base.dss')).read_text().splitlines()
                     if s.lower().startswith('edit transformer.')]
        uuid_map = {}
        for row in (directory / (name + '_uuid.dss')).read_text().splitlines():
            element, uuid = row.split()
            uuid_map[uuid.strip('{}').lower()] = element
        for e in root.findall(C + 'PowerElectronicsConnection'):
            mrid = text(e, 'IdentifiedObject.mRID')
            unit = e.find(C + 'PowerElectronicsConnection.PowerElectronicsUnit').get(R + 'resource')
            unit_element = next(u for u in root if u.get(R + 'about', '#' + u.get(R + 'ID', '')) == unit)
            element = uuid_map[text(unit_element, 'IdentifiedObject.mRID').lower()]
            assert element.lower().startswith(('pvsystem.', 'storage.', 'generator.')), element
            circuit.SetActiveElement(element)
            active = circuit.ActiveCktElement
            bus, phases = active.BusNames[0], active.NumPhases
            kv = active.Properties('kv').Val
            conn = active.Properties('conn').Val
            # Aggregation is valid only for balanced per-phase setpoints.
            connection_uri = e.get(R + 'about', '#' + e.get(R + 'ID', ''))
            children = [p for p in root.findall(C + 'PowerElectronicsConnectionPhase')
                        if p.find(C + 'PowerElectronicsConnectionPhase.PowerElectronicsConnection').get(R + 'resource') == connection_uri]
            if children:
                assert len(children) == phases, (name, mrid, 'DER phase count')
                for key in ('p', 'q'):
                    expected = float(text(e, 'PowerElectronicsConnection.' + key)) / phases
                    assert all(math.isclose(float(text(p, 'PowerElectronicsConnectionPhase.' + key)),
                                            expected, abs_tol=1e-6) for p in children), (name, mrid, key)
            settings += [f'edit {element} enabled=no',
                         f'new load.snapshot_{mrid} bus1={bus} phases={phases} conn={conn} kv={kv} '
                         f'kw={-float(text(e,"PowerElectronicsConnection.p"))/1000:.15g} '
                         f'kvar={-float(text(e,"PowerElectronicsConnection.q"))/1000:.15g} '
                         'model=1 vminpu=0 vmaxpu=1000000']
        settings += ['solve']
        (directory / (name + '_snapshot.dss')).write_text('\n'.join(settings) + '\n')
        for command in settings: engine.Text.Command = command
        assert circuit.Solution.Converged, name
        with (HERE / (name + '-voltage.csv')).open('w', newline='') as f:
            writer = csv.writer(f); writer.writerow(['bus','phase','real_volts','imag_volts'])
            for bus in circuit.AllBusNames:
                circuit.SetActiveBus(bus)
                v = circuit.ActiveBus.Voltages
                assert all(math.isfinite(value) for value in v), (name, bus)
                for i, node in enumerate(circuit.ActiveBus.Nodes):
                    writer.writerow([bus,node,format(v[2*i],'.16g'),format(v[2*i+1],'.16g')])
        circuit.SetActiveElement('vsource.source')
        power = circuit.ActiveCktElement.Powers
        source = [-sum(power[:6:2]), -sum(power[1:6:2])]
        assert all(math.isfinite(value) for value in source), name
        with (HERE / (name + '-power.csv')).open('w', newline='') as f:
            writer=csv.writer(f); writer.writerow(['kw','kvar']); writer.writerow(source)
        manifest['cases'].append({'name':name,'xml_sha256':case['sha256_xml'],
            'bus_count':len(circuit.AllBusNames),'source_kw_kvar':source,
            'files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in directory.glob(name + '_*.dss')}})
        archive_files += list(directory.glob(name + '_*.dss'))
        for suffix in ('-voltage.csv', '-power.csv'):
            file = HERE / (name + suffix)
            hashes[file.name] = hashlib.sha256(file.read_bytes()).hexdigest()
        print(name, len(circuit.AllBusNames), source)
    with ZipFile(HERE / 'native-circuits.zip', 'w', compression=ZIP_DEFLATED) as archive:
        for file in sorted(archive_files):
            info = ZipInfo(file.name, (2026, 10, 16, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, file.read_bytes())
    manifest['native_archive_sha256'] = hashlib.sha256((HERE / 'native-circuits.zip').read_bytes()).hexdigest()
    manifest['reference_sha256'] = hashes
    (HERE / 'reference-manifest.json').write_text(json.dumps(manifest,indent=2) + '\n')
    with (HERE / 'reference-sha256.csv').open('w', newline='') as f:
        writer = csv.writer(f); writer.writerow(['file', 'sha256']); writer.writerows(sorted(hashes.items()))

if __name__ == '__main__':
    run(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve())
