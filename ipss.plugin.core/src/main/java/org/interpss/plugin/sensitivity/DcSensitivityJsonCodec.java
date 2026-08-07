package org.interpss.plugin.sensitivity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.AnalysisType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.LodfSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MultiOutageLodfSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.PtdfSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.SensitivitySpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.ShiftFactorSpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/** JSON transport codec; callers decide whether and where JSON is persisted. */
public final class DcSensitivityJsonCodec {
	private final Gson gson = new GsonBuilder()
			.registerTypeHierarchyAdapter(SensitivitySpec.class, new SensitivitySpecJsonAdapter())
			.setPrettyPrinting().create();

	public String toJson(DcSensitivityStudyDefinition study) { return gson.toJson(study); }
	public DcSensitivityStudyDefinition fromJson(String json) {
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		migrateLegacyAnalyses(object);
		DcSensitivityStudyDefinition result = gson.fromJson(object, DcSensitivityStudyDefinition.class);
		if (result == null) throw new IllegalArgumentException("Sensitivity study JSON is empty");
		if (result.schemaVersion() > DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION)
			throw new IllegalArgumentException("Unsupported sensitivity schema version " + result.schemaVersion());
		return result;
	}

	private static void migrateLegacyAnalyses(JsonObject object) {
		if (object.has("analyses")) return;
		JsonArray types = object.has("analysisTypes") ? object.getAsJsonArray("analysisTypes") : new JsonArray();
		if (types.isEmpty() && object.has("analysisType")) types.add(object.get("analysisType"));
		if (types.isEmpty()) return;
		JsonArray analyses = new JsonArray();
		for (JsonElement typeElement : types) {
			AnalysisType type = AnalysisType.valueOf(typeElement.getAsString());
			String property = switch (type) {
				case PTDF -> "ptdf";
				case SHIFT_FACTOR -> "shiftFactor";
				case LODF -> "lodf";
				case MULTI_OUTAGE_LODF -> "multiOutageLodf";
			};
			if (!object.has(property) || object.get(property).isJsonNull())
				throw new IllegalArgumentException(type + " specification is required");
			JsonObject spec = object.getAsJsonObject(property).deepCopy();
			spec.addProperty("type", type.name());
			analyses.add(spec);
		}
		object.add("analyses", analyses);
	}

	private static final class SensitivitySpecJsonAdapter
			implements JsonSerializer<SensitivitySpec>, JsonDeserializer<SensitivitySpec> {
		private final Gson delegate = new Gson();

		@Override
		public JsonElement serialize(SensitivitySpec source, java.lang.reflect.Type ignored,
				JsonSerializationContext context) {
			JsonObject object = delegate.toJsonTree(source, source.getClass()).getAsJsonObject();
			object.addProperty("type", source.type().name());
			return object;
		}

		@Override
		public SensitivitySpec deserialize(JsonElement json, java.lang.reflect.Type ignored,
				JsonDeserializationContext context) {
			JsonObject object = json.getAsJsonObject();
			if (!object.has("type")) throw new IllegalArgumentException("Sensitivity specification type is required");
			return switch (AnalysisType.valueOf(object.get("type").getAsString())) {
				case PTDF -> delegate.fromJson(object, PtdfSpec.class);
				case SHIFT_FACTOR -> delegate.fromJson(object, ShiftFactorSpec.class);
				case LODF -> delegate.fromJson(object, LodfSpec.class);
				case MULTI_OUTAGE_LODF -> delegate.fromJson(object, MultiOutageLodfSpec.class);
			};
		}
	}
	public void write(Path path, DcSensitivityStudyDefinition study) throws IOException {
		Files.writeString(path, toJson(study));
	}
	public DcSensitivityStudyDefinition read(Path path) throws IOException { return fromJson(Files.readString(path)); }
}
