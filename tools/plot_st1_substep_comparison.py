"""Plot retained ST1 local-integration experiments without resampling observations."""
from pathlib import Path
import argparse
import json
import os
import struct
import sys

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / 'target/plot-dependencies'))
os.environ.setdefault('MPLCONFIGDIR', str(REPO / 'target/matplotlib-config'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import numpy as np


def read_selected(folder, names):
    metadata = json.loads((folder / 'full-network-channels.json').read_text())
    labels = [c['name'] for c in metadata['channels']]
    indices = [labels.index(n.replace('sync_rotor_angle_deg__', 'machine_ANGLE_deg__')) for n in names]
    times, kinds, values = [], [], []
    with (folder / 'full-network.bin').open('rb') as stream:
        assert stream.read(8) == b'IPSSOBS1'
        assert struct.unpack('>i', stream.read(4))[0] == len(labels)
        while header := stream.read(12):
            t, length = struct.unpack('>di', header)
            kind = stream.read(length).decode()
            payload = stream.read(8 * len(labels))
            assert len(payload) == 8 * len(labels)
            if t >= -1e-12:
                times.append(t)
                kinds.append(kind)
                values.append([struct.unpack_from('>d', payload, 8*i)[0] for i in indices])
    result = np.asarray(values)
    assert np.isfinite(result).all()
    return np.asarray(times), kinds, result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-root', type=Path, default=REPO/'target/st1-substep-experiment-20261021')
    parser.add_argument('--output', type=Path, default=REPO/'docs/benchmarks/figures/st1-single-step-2026-10-21')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    summaries = json.loads((args.evidence_root/'all-channel-accuracy-summary.json').read_text())
    plt.rcParams.update({'font.size': 11, 'axes.spines.top': False, 'axes.spines.right': False,
                         'axes.titlesize': 12, 'axes.labelsize': 11, 'savefig.facecolor': 'white'})
    plotted = []
    with PdfPages(args.output/'comparison.pdf') as pdf:
        for case in (1, 2):
            summary = next(s for s in summaries if s['case'] == case)
            families = ('voltage', 'rotor_angle')
            names = [summary['single_minus_ten'][f]['periods']['full']['worst_max_abs_channel'] for f in families]
            runs = {}
            for count in (10, 1):
                folder = args.evidence_root/f'case{case}-substeps{count}'/f'actual/case{case}_bus_fault_01/run-0'
                runs[count] = read_selected(folder, names)
            times, kinds, baseline = runs[10]
            assert np.array_equal(times, runs[1][0]) and kinds == runs[1][1]
            candidate = runs[1][2]
            difference = candidate-baseline
            events = [float(t) for t, k in zip(times, kinds) if k == 'EVENT_POST']
            assert len(events) == 2
            metrics = []
            for j, family in enumerate(families):
                peak = int(np.argmax(np.abs(difference[:,j])))
                maximum = float(abs(difference[peak,j]))
                assert abs(maximum-summary['single_minus_ten'][family]['periods']['full']['worst_max_abs']) < 1e-10
                metrics.append(dict(channel=names[j],rmse=float(np.sqrt(np.mean(difference[:,j]**2))),
                                    max_abs=maximum,peak_time_s=float(times[peak]),samples=len(times)))
            plotted.append(dict(case=case,channels=metrics,events_s=events))
            for zoom in (False, True):
                fig, axes = plt.subplots(2, 2, figsize=(13, 8), sharex=True,
                                         gridspec_kw={'height_ratios':[1.25,1]})
                fig.subplots_adjust(top=.82, bottom=.18, left=.085, right=.97, hspace=.32, wspace=.24)
                period = 'first 1.3 seconds' if zoom else 'full ten-second run'
                fig.suptitle(f'Case {case} | ST1: ten local steps vs one\n{period}', fontsize=18, x=.085, ha='left', y=.98)
                fig.text(.085,.875,'Same network step: 1/240 s  •  Network tolerance: 1e−5 pu  •  No angle offsets or sample reduction',fontsize=10,color='#4b5563')
                for j,(name,unit) in enumerate(zip(names,('pu','degrees'))):
                    bus = name.split('__bus_')[1].split('__')[0]
                    title = f'Bus {bus} voltage' if j == 0 else f'Bus {bus}, unit {name.split("__id_")[1]} rotor angle'
                    axes[0,j].set_title(title,loc='left',fontweight='bold')
                    axes[0,j].plot(times,baseline[:,j],color='#0072B2',lw=1.8,label='Ten local steps')
                    axes[0,j].plot(times,candidate[:,j],color='#D55E00',lw=1.4,ls='--',label='One local step')
                    axes[0,j].set_ylabel(f'{"Voltage" if j==0 else "Rotor angle"} ({unit})')
                    axes[0,j].legend(loc='best',fontsize=9,framealpha=.95)
                    axes[1,j].plot(times,difference[:,j],color='#6A3D9A',lw=1.4)
                    axes[1,j].axhline(0,color='#777777',lw=.8)
                    axes[1,j].set_ylabel(f'One − ten ({unit})')
                    axes[1,j].set_xlabel('Time (s)')
                    m=metrics[j]
                    axes[1,j].set_title(f'Full-run RMSE {m["rmse"]:.6g}; peak |Δ| {m["max_abs"]:.6g} ({unit})',loc='left',fontsize=10)
                    for ax in axes[:,j]:
                        ax.axvspan(events[0],events[1],color='#666666',alpha=.12)
                        for event in events:ax.axvline(event,color='#777777',lw=.8,ls=':')
                        ax.grid(alpha=.2)
                        ax.set_xlim(0,1.3 if zoom else 10)
                        if zoom:
                            visible=times <= 1.3
                            values=np.concatenate((baseline[visible,j],candidate[visible,j])) if ax is axes[0,j] else difference[visible,j]
                            lo,hi=float(values.min()),float(values.max());padding=max((hi-lo)*.08,1e-8)
                            ax.set_ylim(lo-padding,hi+padding)
                fig.text(.085,.045,'Channels selected by largest absolute one-versus-ten difference in each family. Shading: fault applied to cleared.\nBoth event-side samples are preserved. These plots compare implementations; they do not establish reference accuracy.',fontsize=9,color='#4b5563')
                filename=f'case{case}-'+('early-time' if zoom else 'full-run')+'.png'
                fig.savefig(args.output/filename,dpi=150)
                pdf.savefig(fig)
                plt.close(fig)
    (args.output/'plotted-channels.json').write_text(json.dumps(plotted,indent=2))
    print(json.dumps(plotted,indent=2))


if __name__ == '__main__':
    main()
