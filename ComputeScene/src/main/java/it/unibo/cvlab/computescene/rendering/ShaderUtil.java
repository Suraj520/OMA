/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.unibo.cvlab.computescene.rendering;

import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shader helper functions. */
public class ShaderUtil {
    private final static Logger Log = Logger.getLogger(ShaderUtil.class.getSimpleName());

    private static Path shadersPath = Paths.get("shaders").toAbsolutePath();

    public static void setShadersPath(Path shadersPath) {
        ShaderUtil.shadersPath = shadersPath;
    }

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param type The type of shader we will be creating.
     * @param filename The filename of the asset file about to be turned into a shader.
     * @return The shader object handler.
     */
    public static int loadGLShader(String tag, int type, String filename)
            throws IOException {
        String code = readShaderFileFromAssets(filename);

        int shader = GL30.glCreateShader(type);
        GL30.glShaderSource(shader, code);
        GL30.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GL30.glGetShaderiv(shader, GL30.GL_COMPILE_STATUS, compileStatus);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.log(Level.SEVERE, tag+") Error compiling shader: " + GL30.glGetShaderInfoLog(shader));
            GL30.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     * @throws RuntimeException If an OpenGL error is detected.
     */
    public static void checkGLError(String tag, String label) {
        int lastError = GL30.GL_NO_ERROR;
        // Drain the queue of all errors.
        int error;
        while ((error = GL30.glGetError()) != GL30.GL_NO_ERROR) {
            Log.log(Level.SEVERE, label + ": glError " + error);
            lastError = error;
        }
        if (lastError != GL30.GL_NO_ERROR) {
            throw new RuntimeException(label + ": glError " + lastError);
        }
    }

    /**
     * Converts a raw shader file into a string.
     *
     * @param filename The filename of the shader file about to be turned into a shader.
     * @return The context of the text file, or null in case of error.
     */
    private static String readShaderFileFromAssets(String filename)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(shadersPath.resolve(filename))) {
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ", -1);
                if (tokens[0].equals("#include")) {
                    String includeFilename = tokens[1];
                    includeFilename = includeFilename.replace("\"", "");
                    if (includeFilename.equals(filename)) {
                        throw new IOException("Do not include the calling file.");
                    }
                    sb.append(readShaderFileFromAssets(includeFilename));
                } else {
                    sb.append(line).append("\n");
                }
            }

            return sb.toString();
        }
    }
}
