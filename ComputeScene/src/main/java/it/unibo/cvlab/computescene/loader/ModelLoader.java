package it.unibo.cvlab.computescene.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unibo.cvlab.computescene.model.Model;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

public class ModelLoader {

    private static Path defaultModelsPath = Paths.get("models").toAbsolutePath();
    private final static Gson gson;

    static {
        GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        gson = builder.create();
    }

    private Path modelsPath;
    private Set<Path> modelPathSet = new HashSet<>();

    public ModelLoader() throws IOException {
        this(defaultModelsPath);
    }

    public ModelLoader(Path modelsPath) throws IOException {
        this.modelsPath = modelsPath;

        try (Stream<Path> paths = Files.walk(modelsPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter((path)->path.getFileName().toString().endsWith(".json"))
                    .forEach((modelPathSet::add));
        }
    }

    public Path getModelsPath() {
        return modelsPath;
    }

    public Set<Path> getModelPathSet() {
        return modelPathSet;
    }

    public Model parseModel(Path modelPath) throws IOException {
        if(modelPathSet.contains(modelPath)){
            BufferedReader reader = Files.newBufferedReader(modelPath);
            Model model = gson.fromJson(reader, Model.class);
            reader.close();

            model.setModelsPath(modelsPath);
            return model;
        }

        throw new IllegalArgumentException("Model path non presente nel set");
    }

}
