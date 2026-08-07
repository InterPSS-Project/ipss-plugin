package org.interpss.plugin.sensitivity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.dflib.DataFrame;
import org.dflib.parquet.Parquet;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.AnalysisType;
import org.interpss.plugin.sensitivity.SensitivityResult.Block;
import org.interpss.plugin.sensitivity.SensitivityResult.Manifest;
import org.interpss.plugin.sensitivity.SensitivityResult.Row;
import org.interpss.plugin.sensitivity.SensitivityResult.RunSnapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Writes each streamed result block as a Parquet partition plus a JSON manifest. */
public final class ParquetSensitivityResultSink implements SensitivityResultSink {
	private static final String[] COLUMNS = {
			"analysisType", "directionId", "sourceId", "sinkId", "monitorId", "outageId",
			"candidateType", "candidateId", "factor", "transferMw", "incrementalFlowMw"
	};
	private final Path directory;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final List<String> partitions = new ArrayList<>();
	private int partitionIndex;

	public ParquetSensitivityResultSink(Path directory) {
		this.directory = directory.toAbsolutePath().normalize();
	}

	@Override
	public void begin(String resultId, String studyId, List<AnalysisType> types, RunSnapshot snapshot) {
		try {
			Files.createDirectories(directory);
			partitions.clear();
			partitionIndex = 0;
		} catch (IOException ex) {
			throw new IllegalStateException("Cannot create sensitivity result directory " + directory, ex);
		}
	}

	@Override
	public void accept(Block block) {
		if (block.rows().isEmpty()) return;
		Path path = directory.resolve("partition-%06d.parquet".formatted(partitionIndex++));
		List<Object[]> values = block.rows().stream().map(ParquetSensitivityResultSink::values).toList();
		DataFrame frame = DataFrame.byArrayRow(COLUMNS).ofIterable(values);
		Parquet.saver().save(frame, path.toString());
		partitions.add(path.toUri().toString());
	}

	@Override
	public Manifest complete(Manifest manifest) {
		Manifest persisted = withPartitions(manifest);
		writeManifest(persisted);
		return persisted;
	}

	@Override
	public Manifest fail(Manifest manifest, Throwable error) {
		Manifest persisted = withPartitions(manifest);
		writeManifest(persisted);
		return persisted;
	}

	private Manifest withPartitions(Manifest manifest) {
		return new Manifest(manifest.resultId(), manifest.studyId(), manifest.analysisTypes(),
				manifest.candidateCount(), manifest.storedRowCount(), manifest.complete(), manifest.snapshot(),
				partitions, manifest.diagnostics());
	}

	private void writeManifest(Manifest manifest) {
		try {
			Files.writeString(directory.resolve("manifest.json"), gson.toJson(manifest));
		} catch (IOException ex) {
			throw new IllegalStateException("Cannot write sensitivity result manifest", ex);
		}
	}

	private static Object[] values(Row row) {
		return new Object[] { row.analysisType().name(), row.directionId(), row.sourceId(), row.sinkId(),
				row.monitorId(), row.outageId(), row.candidateType(), row.candidateId(), row.factor(),
				row.transferMw(), row.incrementalFlowMw() };
	}
}
